package com.mercato.order;

import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Jobs.*;
import com.mercato.Payloads.Request.*;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import com.mercato.jobs.PostgresTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=PostgresQuantityWorkflowTest.Config.class,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false","mercato.jobs.enabled=false"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class PostgresQuantityWorkflowTest {
    static final String DATABASE=PostgresTestSupport.database();
    static final Instant CONFIRMED=Instant.parse("2026-10-26T00:00:00Z");
    static final ThreadLocal<String> IDENTITY=ThreadLocal.withInitial(()->"seller@test.invalid");
    static final ThreadLocal<String> CUSTOMER_ID=new ThreadLocal<>();
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(CONFIRMED.plusSeconds(7200));
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value.get();}
    }
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Commerce","com.mercato.Jobs"})
    @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Commerce"})
    @Import({ShipmentService.class,SellerOrderVisibility.class,FulfillmentServiceImpl.class,com.mercato.Auth.AuthEmailJobHandler.class,com.mercato.Mail.AuthenticationMailGuard.class,com.mercato.Mail.MailActivity.class,com.mercato.Mail.TrackedMailSender.class,com.mercato.Mail.BrandedMailSender.class,com.mercato.Controller.FulfillmentWorkflowController.class,WorkflowAdoption.class,OrderServiceImpl.class,OrderLineUpdateServiceImpl.class,FulfillmentWorkflow.class,FulfillmentFunding.class,WorkflowCommands.class,FulfillmentCancellation.class,FulfillmentDeadlines.class,FulfillmentSlaMetrics.class,FulfillmentNoticeGuard.class,OrderReservationServiceImpl.class,DurableJobs.class,JobStore.class})
    static class Config {
        @Bean DelhiveryClient carrier(){return mock(DelhiveryClient.class);}
        @Bean CartService carts(){return mock(CartService.class);}
        @Bean CashfreeService gateway(){return mock(CashfreeService.class);}
        @Bean CartReservationService cartHolds(){return mock(CartReservationService.class);}
        @Bean OrderRetryHoldService retry(){return mock(OrderRetryHoldService.class);}
        @Bean CommissionService commissions(){return mock(CommissionService.class);}
        @Bean com.mercato.Commerce.RefundObligations refundObligations(){return mock(com.mercato.Commerce.RefundObligations.class);}
        @Bean AuditEvents auditEvents(){return new AuditEvents();}
        @Bean(name={"clock","durableJobClock"}) MutableClock clock(){return new MutableClock();}
        @Bean AuthUtil auth(){var a=mock(AuthUtil.class);when(a.getLoggedInUser()).thenAnswer(c->{var user=new EcommUser();user.setEmail(IDENTITY.get());user.setUserId(CUSTOMER_ID.get());return user;});return a;}
        @Bean RefundService refunds(){return mock(RefundService.class);}
        @Bean ShipmentNotificationService mail(){return mock(ShipmentNotificationService.class);}
        @Bean TestMailbox smtpMailbox(){return new TestMailbox();}
        @Bean @SuppressWarnings("unchecked") org.springframework.session.FindByIndexNameSessionRepository<org.springframework.session.Session> sessionRepository(){return mock(org.springframework.session.FindByIndexNameSessionRepository.class);}
    }
    static class TestMailbox extends org.springframework.mail.javamail.JavaMailSenderImpl {
        final List<jakarta.mail.internet.MimeMessage> sent=new CopyOnWriteArrayList<>();
        @Override public void send(jakarta.mail.internet.MimeMessage message){
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"SMTP must execute after the eligibility read transaction ends");sent.add(message);
        }
    }
    static class AuditEvents {
        final List<FulfillmentCancellationRecorded> events=new CopyOnWriteArrayList<>();
        boolean reject;
        @org.springframework.context.event.EventListener public void cancellation(FulfillmentCancellationRecorded event){if(reject)throw new IllegalStateException("Synthetic refund hook rejection");events.add(event);}
    }
    @Autowired AuditEvents auditEvents;@Autowired WorkflowAdoption adoption;@Autowired OrderService orderService;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired com.mercato.Controller.FulfillmentWorkflowController workflowController;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->DATABASE);p.add("spring.datasource.username",PostgresTestSupport::username);p.add("spring.datasource.password",PostgresTestSupport::password);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");}
    @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
    @Autowired ShipmentService shipmentService;@Autowired ShipmentRepository parcels;@Autowired DelhiveryClient carrier;
    @Autowired FulfillmentService fulfillments;
    @Autowired OrderLineRepository lines;@Autowired OrderRepository orders;@Autowired ProductRepository products;@Autowired CategoryRepository categories;
    @Autowired OrderReservationRepository reservations;@Autowired OrderLineUpdateService updates;
    @Autowired FulfillmentDeadlines deadlines;@Autowired FulfillmentCancellation cancellation;@Autowired JobStore jobs;@Autowired DurableJobs queue;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired MutableClock clock;
    @Autowired RefundService refundService;
    @Autowired TestMailbox smtpMailbox;@Autowired com.mercato.Auth.AuthEmailJobHandler authEmailHandler;
    String orderId,fulfillment;TransactionTemplate tx;
    @BeforeEach void fixture(TestInfo test){
        jdbc.update("UPDATE order_snapshot SET workflow_review_required=true");
        jdbc.update("DELETE FROM durable_job_retry_audit");jdbc.update("DELETE FROM durable_jobs");
        reset(refundService,carrier);CUSTOMER_ID.remove();
        smtpMailbox.sent.clear();
        auditEvents.events.clear();auditEvents.reject=false;clock.value.set(CONFIRMED.plusSeconds(7200));IDENTITY.set("seller@test.invalid");tx=new TransactionTemplate(transactions);
        orderId="QTY-"+UUID.randomUUID().toString().substring(0,12);fulfillment=orderId+"-F";
        tx.executeWithoutResult(t->{
            var c=new Category();c.setCategoryName("Quantity regression");categories.save(c);
            var p=Product.builder().productId(orderId+"-P").productName("Quantity product").description("Synthetic quantity regression").primaryImageUrl("/test.jpg").category(c).retailPrice(BigDecimal.TEN).physicalQty(20).reservedQty(10).build();products.save(p);
            var o=new Order();o.setOrderId(orderId);o.setCustomerName("Buyer");o.setCustomerEmail("buyer@test.invalid");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.SUCCESS);o.setCurrency("INR");o.setSubtotal(new BigDecimal("100"));o.setCharges(BigDecimal.ZERO);o.setTotalAmount(new BigDecimal("100"));o.setRecipientName("Buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Synthetic address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");
            var l=new OrderLine();l.setFulfillmentId(fulfillment);l.setOrderLineNumber(1);l.setProductId(p.getProductId());l.setProductName(p.getProductName());l.setUnitPrice(BigDecimal.TEN);l.setOrderedQty(10);l.setLineTotal(new BigDecimal("100"));l.setOrderLineStatus(OrderLineStatus.CREATED);l.setSellerEmail("seller@test.invalid");l.setSellerName("Seller");o.addOrderLine(l);
            var payment=new Payment();payment.setAmount(o.getTotalAmount());payment.setCurrency("INR");payment.setPaymentMethod(PaymentMethod.UNKNOWN);payment.setStatus(PaymentStatus.SUCCESS);payment.setCapturedAt(CONFIRMED);payment.setCapturedAmount(o.getTotalAmount());o.attachPayment(payment);o.confirmOrder();o.setAllocationCompletedAt(CONFIRMED);if(!test.getTestMethod().orElseThrow().getName().startsWith("unknown"))o.establishConfirmation(CONFIRMED);
            if(test.getTestMethod().orElseThrow().getName().startsWith("stableCustomer"))o.setCustomerUserId("a-different-customer-id");
            orders.saveAndFlush(o);
            reservations.save(OrderReservation.builder().order(o).orderLine(l).product(p).reservedQty(10).build());
        });
    }
    OrderLineUpdateRequestDTO request(OrderLineAction action,int qty,long version,String operation){var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(fulfillment);r.setOrderLineNumber(1);r.setAction(action);r.setQty(qty);r.setExpectedVersion(version);r.setOperationId(operation);r.setReason("Synthetic test reason");return r;}
    OrderLine snapshot(){return tx.execute(t->orders.findByOrderId(orderId).orElseThrow().getOrderLines().get(0));}
    void packAll(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,10,0,"accept"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.START_PREPARING,10,1,"prepare"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.MARK_PACKED,10,2,"pack"),TransitionTrigger.SELLER);
    }
    void currentCaptureProof(){tx.executeWithoutResult(t->{var p=orders.lockByOrderId(orderId).orElseThrow().getPayment();p.setCaptureVerifiedAt(CONFIRMED);p.setCaptureEvidenceId("synthetic-reader-proof");});}
    @Test void parcelSchemaIsAdditiveAndDoesNotInventLegacyShipments() {
        assertEquals(1,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name='shipments'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from shipments where order_fk=(select id from order_snapshot where order_id=?)",Integer.class,orderId));
    }
    ShipmentService.Handoff handoff(String operation,long version,String awb,int qty){return new ShipmentService.Handoff(operation,version,awb,true,List.of(new ShipmentService.Item(1,qty)));}
    @Test void parcelsAreAtomicScopedIdempotentAndDeliveredWithoutAnotherStockDeduction() {
        packAll();currentCaptureProof();
        var request=handoff("parcel-one",3,"001"+orderId,4);
        var first=shipmentService.create(fulfillment,TransitionTrigger.SELLER,request);
        assertEquals(4,snapshot().getShippedQty());assertEquals(1,first.shipments().size());
        assertEquals(first,shipmentService.create(fulfillment,TransitionTrigger.SELLER,request));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("parcel-one",3,request.waybill(),3)));
        var second=shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("parcel-two",4,"002"+orderId,6));
        assertEquals(2,second.shipments().size());assertEquals(10,snapshot().getShippedQty());
        int physical=products.findByProductId(orderId+"-P").orElseThrow().getPhysicalQty();assertEquals(10,physical);
        IDENTITY.set("buyer@test.invalid");assertEquals(2,shipmentService.read(orderId,TransitionTrigger.CUSTOMER,false).shipments().size());
        IDENTITY.set("foreign@test.invalid");assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->shipmentService.read(orderId,TransitionTrigger.CUSTOMER,false));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->shipmentService.read(fulfillment,TransitionTrigger.SELLER,true));
        IDENTITY.set("seller@test.invalid");
        var delivery=new ShipmentService.Change("delivered-one",5L,"Received by customer",null);
        var delivered=shipmentService.change(first.shipments().get(0).id(),delivery,true);
        assertEquals(4,snapshot().getDeliveredQty());assertEquals(delivered,shipmentService.change(first.shipments().get(0).id(),delivery,true));
        var other=second.shipments().stream().filter(s->!s.id().equals(first.shipments().get(0).id())).findFirst().orElseThrow();
        shipmentService.change(other.id(),new ShipmentService.Change("delivered-two",6L,"Received by customer",null),true);
        assertEquals(10,snapshot().getDeliveredQty());assertEquals(physical,products.findByProductId(orderId+"-P").orElseThrow().getPhysicalQty());
        assertEquals(4,jdbc.queryForObject("select count(*) from durable_jobs where business_key like 'parcel-mail:%' and cast(payload as jsonb)->>'to'='buyer@test.invalid'",Integer.class));
    }
    @Test void heldAndUnfundedParcelHandoffsCannotChangeInventory() {
        packAll();clock.value.set(CONFIRMED.plusSeconds(3599));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->shipmentService.read(fulfillment,TransitionTrigger.SELLER,true));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("held",3,"H"+orderId,1)));
        clock.value.set(CONFIRMED.plusSeconds(7200));
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("unfunded",3,"U"+orderId,1)));
        assertEquals(0,snapshot().getShippedQty());assertTrue(shipmentService.read(fulfillment,TransitionTrigger.SELLER,true).shipments().isEmpty());
    }
    @Test void providerDeliveryIsForwardOnlyIdempotentAndOutsideTransaction() {
        packAll();currentCaptureProof();var result=shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("tracked",3,"AWB"+orderId,10));
        var parcel=result.shipments().get(0);clock.value.set(clock.instant().plusSeconds(60));when(carrier.enabled()).thenReturn(true);
        var reverse=new DelhiveryClient.Update(parcel.waybill(),"Delivered","DL",clock.instant(),null,List.of(),true);
        when(carrier.fetch(anyCollection())).thenAnswer(c->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return new DelhiveryClient.Result(Map.of(parcel.waybill(),reverse),false);});
        shipmentService.process(List.of(parcel.id()));assertEquals(0,snapshot().getDeliveredQty());
        var forward=new DelhiveryClient.Update(parcel.waybill(),"Delivered","DL",clock.instant(),null,List.of(),false);
        when(carrier.fetch(anyCollection())).thenReturn(new DelhiveryClient.Result(Map.of(parcel.waybill(),forward),false));
        shipmentService.process(List.of(parcel.id()));shipmentService.process(List.of(parcel.id()));
        assertEquals(10,snapshot().getDeliveredQty());assertEquals(10,products.findByProductId(orderId+"-P").orElseThrow().getPhysicalQty());
        assertEquals("DELIVERED",shipmentService.read(fulfillment,TransitionTrigger.SELLER,true).shipments().get(0).status());
    }
    @Test void correctedWaybillFencesAnAlreadyInFlightDeliveryResponse() {
        packAll();currentCaptureProof();var result=shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("corrected",3,"OLD"+orderId,10));
        var parcel=result.shipments().get(0);when(carrier.enabled()).thenReturn(true);clock.value.set(clock.instant().plusSeconds(60));
        when(carrier.fetch(anyCollection())).thenAnswer(c->{
            shipmentService.change(parcel.id(),new ShipmentService.Change("fix-awb",4L,"Correct carrier reference","NEW"+orderId),false);
            return new DelhiveryClient.Result(Map.of(parcel.waybill(),new DelhiveryClient.Update(parcel.waybill(),"Delivered","DL",clock.instant(),null,List.of(),false)),false);
        });
        shipmentService.process(List.of(parcel.id()));assertEquals(0,snapshot().getDeliveredQty());
        assertEquals("NEW"+orderId,shipmentService.read(fulfillment,TransitionTrigger.SELLER,true).shipments().get(0).waybill());
    }
    @Test void parcelHandoffAndSellerCancellationSerializeOnTheOrderLock() throws Exception {
        packAll();currentCaptureProof();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var shipping=pool.submit(()->{IDENTITY.set("seller@test.invalid");return tx.execute(t->{orders.lockByOrderId(orderId).orElseThrow();entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("raced-shipment",3,"RACE"+orderId,10));});});
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var cancelling=pool.submit(()->{IDENTITY.set("seller@test.invalid");return updates.updateOrderLine(request(OrderLineAction.CANCEL,10,3,"raced-cancellation"),TransitionTrigger.SELLER);});
            release.countDown();shipping.get(10,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->cancelling.get(10,TimeUnit.SECONDS));
            assertEquals(10,snapshot().getShippedQty());assertEquals(0,snapshot().getCancelledQty());assertEquals(10,products.findByProductId(orderId+"-P").orElseThrow().getPhysicalQty());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void trackingRefreshCooldownAndInvalidDatesRetainTheLastKnownState() {
        packAll();currentCaptureProof();var result=shipmentService.create(fulfillment,TransitionTrigger.SELLER,handoff("cooldown",3,"COOL"+orderId,10));var parcel=result.shipments().get(0);when(carrier.enabled()).thenReturn(true);
        shipmentService.refresh(fulfillment,TransitionTrigger.SELLER,true);shipmentService.refresh(fulfillment,TransitionTrigger.SELLER,true);
        assertEquals(1,jdbc.queryForObject("select count(*) from durable_jobs where job_type='DELHIVERY_TRACK'",Integer.class));
        when(carrier.fetch(anyCollection())).thenReturn(new DelhiveryClient.Result(Map.of(parcel.waybill(),new DelhiveryClient.Update(parcel.waybill(),"Delivered","DL",clock.instant().plusSeconds(3600),null,List.of(),false)),false));
        shipmentService.process(List.of(parcel.id()));assertEquals(0,snapshot().getDeliveredQty());assertEquals("SHIPPED",shipmentService.read(fulfillment,TransitionTrigger.SELLER,true).shipments().get(0).status());
    }
    @Test void stableCustomerIdentityPreventsRecycledEmailShipmentAccess() {
        IDENTITY.set("buyer@test.invalid");
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->shipmentService.read(orderId,TransitionTrigger.CUSTOMER,false));
        CUSTOMER_ID.set("a-different-customer-id");IDENTITY.set("changed-email@test.invalid");
        assertNotNull(shipmentService.read(orderId,TransitionTrigger.CUSTOMER,false));
        CUSTOMER_ID.remove();
    }
    @Test void heldSellerReadsAndCommandsAreUnavailableUntilExactRelease() {
        clock.value.set(CONFIRMED.plusSeconds(3600).minusNanos(1000));
        assertTrue(fulfillments.getOpenFulfillmentOrders().stream().noneMatch(f->fulfillment.equals(f.getFulfillmentId())));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->fulfillments.getFulfillmentOrder(fulfillment));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"held"),TransitionTrigger.SELLER));
        IDENTITY.set("foreign@test.invalid");
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->fulfillments.getFulfillmentOrder(fulfillment));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"foreign"),TransitionTrigger.SELLER));
        IDENTITY.set("seller@test.invalid");clock.value.set(CONFIRMED.plusSeconds(3600));
        assertEquals(fulfillment,fulfillments.getFulfillmentOrder(fulfillment).getFulfillmentId());
        assertEquals(1,updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"exact-release"),TransitionTrigger.SELLER).getAcceptedQty());
        clock.value.set(CONFIRMED.plusSeconds(3600).plusNanos(1000));
        assertEquals(fulfillment,fulfillments.getFulfillmentOrder(fulfillment).getFulfillmentId());
    }
    @Test void explicitSellerRoutesIgnoreAdminPrivilegeAndEnforceHold() throws Exception {
        var auth=new AuthUtil(mock(com.mercato.Security.services.UserDetailsServiceImpl.class)){@Override public EcommUser getLoggedInUser(){var u=new EcommUser();u.setEmail(IDENTITY.get());return u;}};
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("dual","unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SELLER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertEquals(TransitionTrigger.ADMIN,auth.resolveTransitionTrigger());
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(workflowController).setControllerAdvice(new com.mercato.ExceptionHandler.GlobalExceptionHandler()).build();
            clock.value.set(CONFIRMED.plusSeconds(3600));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/seller/fulfillment/commands").contentType("application/json").content(json.writeValueAsBytes(request(OrderLineAction.ACCEPT,1,0,"seller-route")))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            assertEquals("SELLER",jdbc.queryForObject("select actor_type from fulfillment_commands where order_id=? and operation_id='seller-route'",String.class,orderId));
            var cancel=scope();cancel.setExpectedVersion(1L);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/seller/fulfillment/cancellations").contentType("application/json").content(json.writeValueAsBytes(cancel))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            assertEquals("SELLER",jdbc.queryForObject("select actor_type from fulfillment_commands where order_id=? and operation_id=?",String.class,orderId,cancel.getOperationId()));
            clock.value.set(CONFIRMED.plusSeconds(3599));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/seller/fulfillment/commands").contentType("application/json").content(json.writeValueAsBytes(request(OrderLineAction.ACCEPT,1,1,"hidden-http"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            IDENTITY.set("foreign@test.invalid");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/seller/fulfillment/cancellations").contentType("application/json").content(json.writeValueAsBytes(scope())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            IDENTITY.set("seller@test.invalid");
            assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->workflowController.sellerCommand(request(OrderLineAction.ACCEPT,1,1,"hidden-dual")));
            assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->workflowController.sellerCancel(scope()));
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void preReleaseFullCancellationCreatesNoIncomingWorkAndPartialSurvivorReleases() {
        clock.value.set(CONFIRMED.plusSeconds(3599));IDENTITY.set("buyer@test.invalid");
        updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"buyer-full"),TransitionTrigger.CUSTOMER);
        IDENTITY.set("seller@test.invalid");assertTrue(fulfillments.getClosedFulfillmentOrders().stream().noneMatch(f->fulfillment.equals(f.getFulfillmentId())));
        clock.value.set(CONFIRMED.plusSeconds(3600));deadlines.handle(deadline("RELEASE"));
        assertEquals(0,jdbc.queryForObject("select count(*) from durable_jobs where business_key like ?",Integer.class,"fulfillment-mail:"+orderId+":RELEASE:%"));
        assertEquals(0,jdbc.queryForObject("select count(*) from fulfillment_work_items where order_id=? and kind='RELEASE'",Integer.class,orderId));
        assertEquals(fulfillment,fulfillments.getClosedFulfillmentOrders().stream().filter(f->fulfillment.equals(f.getFulfillmentId())).findFirst().orElseThrow().getFulfillmentId());
    }
    @Test void preReleasePartialCancellationSurvivorBecomesActionableAtRelease() {
        clock.value.set(CONFIRMED.plusSeconds(3600).minusNanos(1000));IDENTITY.set("buyer@test.invalid");
        updates.updateOrderLine(request(OrderLineAction.CANCEL,3,0,"buyer-part"),TransitionTrigger.CUSTOMER);
        IDENTITY.set("seller@test.invalid");clock.value.set(CONFIRMED.plusSeconds(3600));deadlines.handle(deadline("RELEASE"));
        assertEquals(1,jdbc.queryForObject("select count(*) from durable_jobs where business_key like ?",Integer.class,"fulfillment-mail:"+orderId+":RELEASE:%"));
        assertEquals(7,updates.updateOrderLine(request(OrderLineAction.ACCEPT,7,1,"survivor"),TransitionTrigger.SELLER).getAcceptedQty());
    }
    @Test void unknownLegacyConfirmationRemainsUnavailable() {
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->fulfillments.getFulfillmentOrder(fulfillment));
        assertTrue(fulfillments.getOpenFulfillmentOrders().stream().noneMatch(f->fulfillment.equals(f.getFulfillmentId())));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->workflowController.sellerCommand(request(OrderLineAction.ACCEPT,1,0,"legacy-hidden")));
        assertNotNull(orders.findByOrderId(orderId).orElseThrow());
        var metrics=(FulfillmentSlaMetrics.Report)workflowController.sellerMetrics(CONFIRMED.minusSeconds(1),CONFIRMED.plusSeconds(200000));
        assertEquals(0,metrics.acceptance().cohortOrders());
    }
    @Test void prospectiveAdoptionDelaysVisibilityAndRefreshDoesNotRestartGrace() {
        Instant adopted=CONFIRMED.plusSeconds(10800);
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();o.setWorkflowPolicyAdoptedAt(adopted);o.establishConfirmation(adopted);});
        clock.value.set(adopted.minusNanos(1000));
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->fulfillments.getFulfillmentOrder(fulfillment));
        clock.value.set(adopted);assertEquals(fulfillment,fulfillments.getFulfillmentOrder(fulfillment).getFulfillmentId());
        tx.executeWithoutResult(t->{var o=orders.findByOrderId(orderId).orElseThrow();assertEquals(CONFIRMED,o.getConfirmedAt());assertEquals(CONFIRMED.plusSeconds(3600),o.getSellerActionsAvailableAt());});
    }
    @Test void historicalCaptureCannotAuthorizePhysicalHandoff(){
        packAll();
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(OrderLineAction.SHIP,1,3,"legacy-ship"),TransitionTrigger.ADMIN));
        assertEquals(0,snapshot().getShippedQty());
    }
    @Test void adminDeliveryRecordsRealProgressAndReplaysWithoutInventoryOrMoneyChanges(){
        packAll();currentCaptureProof();
        updates.updateOrderLine(request(OrderLineAction.SHIP,10,3,"ship"),TransitionTrigger.ADMIN);
        var deliver=OrderLineAction.valueOf("DELIVER");
        var r=request(deliver,4,4,"deliver-part");var first=updates.updateOrderLine(r,TransitionTrigger.ADMIN);
        assertEquals(4,first.getWorkflow().deliveredQty());assertEquals(6,first.getWorkflow().actionMaxima().get("DELIVER"));
        assertEquals(4,updates.updateOrderLine(r,TransitionTrigger.ADMIN).getWorkflow().deliveredQty());
        r.setQty(5);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->updates.updateOrderLine(r,TransitionTrigger.ADMIN));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->updates.updateOrderLine(request(deliver,1,4,"stale-deliver"),TransitionTrigger.ADMIN));
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(deliver,7,5,"too-many"),TransitionTrigger.ADMIN));
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(deliver,1,5,"seller-deliver"),TransitionTrigger.SELLER));
        IDENTITY.set("buyer@test.invalid");assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(deliver,1,5,"buyer-deliver"),TransitionTrigger.CUSTOMER));
        clock.value.set(CONFIRMED.plusSeconds(9000));var full=updates.updateOrderLine(request(deliver,6,5,"deliver-rest"),TransitionTrigger.ADMIN);
        assertEquals("DELIVERED",full.getWorkflow().state());assertEquals(10,snapshot().getShippedQty());
        assertEquals(10,jdbc.queryForObject("SELECT physical_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(java.sql.Timestamp.from(clock.instant()),jdbc.queryForObject("SELECT max(occurred_at) FROM order_line_state_transitions WHERE action='DELIVER' AND order_line_fk=?",java.sql.Timestamp.class,snapshot().getId()));
        verifyNoInteractions(refundService);
    }
    @Test void deliveryRejectsUnshippedAndLostCurrentProof(){
        packAll();currentCaptureProof();var deliver=OrderLineAction.valueOf("DELIVER");
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(deliver,1,3,"unshipped"),TransitionTrigger.ADMIN));
        updates.updateOrderLine(request(OrderLineAction.SHIP,2,3,"ship"),TransitionTrigger.ADMIN);
        tx.executeWithoutResult(t->orders.lockByOrderId(orderId).orElseThrow().getPayment().setCaptureEvidenceId(null));
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(deliver,1,4,"unproven"),TransitionTrigger.ADMIN));
    }
    void addSecondLine(boolean prepared,String seller){tx.executeWithoutResult(t->{
        var o=orders.lockByOrderId(orderId).orElseThrow();var first=o.getOrderLines().get(0);
        var p=products.findByProductIdForUpdate(first.getProductId()).orElseThrow();p.adjustReservedQty(2);
        var l=new OrderLine();l.setFulfillmentId(fulfillment);l.setOrderLineNumber(2);l.setProductId(p.getProductId());l.setProductName(p.getProductName());l.setUnitPrice(BigDecimal.TEN);l.setOrderedQty(2);l.setLineTotal(new BigDecimal("20"));l.setOrderLineStatus(OrderLineStatus.CONFIRMED);l.setSellerEmail(seller);l.setSellerName("Second seller");
        if(prepared){l.accept(2);l.startPreparing(1);}o.addOrderLine(l);lines.saveAndFlush(l);
        reservations.save(OrderReservation.builder().order(o).orderLine(l).product(p).reservedQty(2).build());
    });}
    FulfillmentCancelRequest scope(){var r=new FulfillmentCancelRequest();r.setOrderId(orderId);r.setFulfillmentId(fulfillment);r.setExpectedVersion(0L);r.setOperationId("cancel-scope");r.setReason("Synthetic scoped cancellation");return r;}
    @Test void idempotencyPersistsOutcomeAndRejectsChangedBodyOrStaleVersion(){
        var r=request(OrderLineAction.ACCEPT,4,0,"accept");var first=updates.updateOrderLine(r,TransitionTrigger.SELLER);
        assertEquals(1,first.getWorkflow().version());assertEquals(4,updates.updateOrderLine(r,TransitionTrigger.SELLER).getAcceptedQty());
        r.setQty(5);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->updates.updateOrderLine(r,TransitionTrigger.SELLER));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"stale"),TransitionTrigger.SELLER));
        assertEquals(4,snapshot().getAcceptedQty());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM fulfillment_commands WHERE order_id=?",Integer.class,orderId));
    }
    @Test void partialCancelUsesRemainingUnitsAndInventoryAndAuditAgree(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,8,0,"accept"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.START_PREPARING,6,1,"prepare"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.MARK_PACKED,4,2,"pack"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.CANCEL,5,3,"cancel"),TransitionTrigger.SELLER);
        var l=snapshot();assertEquals(5,l.getAcceptedQty());assertEquals(5,l.getPreparingQty());assertEquals(4,l.getPackedQty());assertTrue(l.validQuantities());
        assertEquals(5,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        var row=jdbc.queryForMap("SELECT actor_type,unaccepted_qty,accepted_only_qty,preparing_only_qty,packed_only_qty FROM fulfillment_commands WHERE order_id=? AND operation_id='cancel'",orderId);
        assertEquals("SELLER",row.get("actor_type"));assertEquals(2,row.get("unaccepted_qty"));assertEquals(2,row.get("accepted_only_qty"));assertEquals(1,row.get("preparing_only_qty"));assertEquals(0,row.get("packed_only_qty"));
    }
    @Test void ownershipAndLegacyRoutesCannotBypassHoldOrRequiredTokens(){
        IDENTITY.set("other@test.invalid");assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"other"),TransitionTrigger.SELLER));IDENTITY.set("seller@test.invalid");
        clock.value.set(CONFIRMED.plusSeconds(3599));assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"early"),TransitionTrigger.SELLER));
        clock.value.set(CONFIRMED.plusSeconds(3600));var r=request(OrderLineAction.ACCEPT,1,0,null);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->updates.updateOrderLine(r,TransitionTrigger.SELLER));
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,0,"boundary"),TransitionTrigger.SELLER);assertEquals(1,snapshot().getAcceptedQty());
    }
    @Test void cancelAndPreparingRaceSerializeOnAggregateWithoutViolatingBuckets()throws Exception{
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,10,0,"accept"),TransitionTrigger.SELLER);
        var owner=new java.util.concurrent.atomic.AtomicInteger();var entered=new CountDownLatch(1);var run=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->tx.execute(t->{orders.lockByOrderId(orderId).orElseThrow();owner.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));entered.countDown();try{assertTrue(run.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return updates.updateOrderLine(request(OrderLineAction.START_PREPARING,10,1,"prepare"),TransitionTrigger.SELLER);}));
            assertTrue(entered.await(10,TimeUnit.SECONDS));var b=pool.submit(()->{IDENTITY.set("buyer@test.invalid");return updates.updateOrderLine(request(OrderLineAction.CANCEL,10,1,"cancel"),TransitionTrigger.CUSTOMER);});
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean blocked=false;
            while(System.nanoTime()<until){if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))",Integer.class,owner.get())>0){blocked=true;break;}}
            assertTrue(blocked,"Customer cancellation must actually wait for the aggregate lock");
            run.countDown();a.get(10,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->b.get(10,TimeUnit.SECONDS));
            var l=snapshot();assertEquals(10,l.getPreparingQty());assertEquals(0,l.getCancelledQty());assertTrue(l.validQuantities());
        }finally{run.countDown();pool.shutdownNow();}
    }
    JobRecord deadline(String kind){return new JobRecord(UUID.randomUUID(),"FULFILLMENT_DEADLINE","test",Map.of("orderId",orderId,"kind",kind),1,JobStatus.READY,1,clock.instant(),null,null,null,null,0,null,clock.instant(),clock.instant());}
    @Test void lateTimeoutCancelsOnlyUnacceptedUnitsAndPreparationReviewIsIndependent(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,4,0,"accept"),TransitionTrigger.SELLER);
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("ACCEPT_TIMEOUT"));deadlines.handle(deadline("ACCEPT_TIMEOUT"));
        assertEquals(6,snapshot().getCancelledQty());assertEquals(4,snapshot().getAcceptedQty());
        deadlines.handle(deadline("PREPARATION_REVIEW"));assertEquals(4,jdbc.queryForObject("SELECT affected_qty FROM fulfillment_work_items WHERE order_id=? AND kind='PREPARATION_REVIEW'",Integer.class,orderId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM fulfillment_commands WHERE order_id=? AND actor_type='SYSTEM'",Integer.class,orderId));
    }
    @Test void futureJobCannotBeClaimedEarlyAndCancelledOrdersSkipReleaseMail(){
        tx.executeWithoutResult(t->deadlines.schedule(orders.findByOrderId(orderId).orElseThrow()));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key LIKE ?",Integer.class,"fulfillment:"+orderId+":%"));
        var future=queue.enqueueAt("FUTURE","future:"+orderId,Map.of(),clock.instant().plusSeconds(3600));
        assertTrue(jobs.claim("quantity-test",10,Duration.ofSeconds(30)).stream().noneMatch(j->j.id().equals(future.id())));
        clock.value.set(clock.instant().plusSeconds(3600));
        assertTrue(jobs.claim("quantity-test-due",10,Duration.ofSeconds(30)).stream().anyMatch(j->j.id().equals(future.id())));
        updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"cancel"),TransitionTrigger.SELLER);deadlines.handle(deadline("RELEASE"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key LIKE ?",Integer.class,"fulfillment-mail:"+orderId+":%"));
    }
    @Test void fullFulfillmentCancellationIsAtomicAndReplaysItsPersistedOutcome(){
        addSecondLine(false,"seller@test.invalid");var r=scope();var first=cancellation.cancel(r,TransitionTrigger.SELLER);
        assertEquals(2,first.size());assertEquals(2,first.get(0).getWorkflow().version());
        clock.value.set(CONFIRMED.plusSeconds(10000));var replay=cancellation.cancel(r,TransitionTrigger.SELLER);
        assertEquals(first.get(0).getWorkflow().serverNow(),replay.get(0).getWorkflow().serverNow());
        assertEquals(0,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON r.order_fk=o.id WHERE o.order_id=?",Integer.class,orderId));
        r.setReason("Changed body");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->cancellation.cancel(r,TransitionTrigger.SELLER));
    }
    @Test void customerWholeScopeDoesNotPartiallyCancelWhenLaterLineIsPreparing(){
        addSecondLine(true,"seller@test.invalid");IDENTITY.set("buyer@test.invalid");
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->cancellation.cancel(scope(),TransitionTrigger.CUSTOMER));
        assertEquals(0,snapshot().getCancelledQty());assertEquals(12,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fulfillment_commands WHERE order_id=?",Integer.class,orderId));
    }
    @Test void sellerCannotCancelOtherSellerUnitsInsideAFulfillmentScope(){
        addSecondLine(false,"another-seller@test.invalid");
        assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->cancellation.cancel(scope(),TransitionTrigger.SELLER));
        assertEquals(0,snapshot().getCancelledQty());
    }
    @Test void inconsistentReservationCannotReleaseInventoryOrCommitCancellation(){
        jdbc.update("UPDATE order_reservations SET reserved_qty=1 WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",orderId);
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(OrderLineAction.CANCEL,5,0,"bad-inventory"),TransitionTrigger.SELLER));
        assertEquals(0,snapshot().getCancelledQty());assertEquals(10,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
    }
    @Test void exactDeadlineGuardsCommandsEvenBeforeLateJobsRun(){
        clock.value.set(CONFIRMED.plusSeconds(176399));updates.updateOrderLine(request(OrderLineAction.ACCEPT,4,0,"last-second"),TransitionTrigger.SELLER);
        assertThrows(WaitingJobException.class,()->deadlines.handle(deadline("ACCEPT_TIMEOUT")));
        clock.value.set(CONFIRMED.plusSeconds(176400));assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,1,"too-late"),TransitionTrigger.SELLER));
        deadlines.handle(deadline("ACCEPT_TIMEOUT"));assertEquals(6,snapshot().getCancelledQty());assertEquals(4,snapshot().getAcceptedQty());
    }
    @Test void synchronousCancellationHookIsAtomicAndDoesNotRepeatOnReplay(){
        var r=request(OrderLineAction.CANCEL,3,0,"refund-hook");auditEvents.reject=true;
        assertThrows(IllegalStateException.class,()->updates.updateOrderLine(r,TransitionTrigger.SELLER));
        assertEquals(0,snapshot().getCancelledQty());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fulfillment_commands WHERE order_id=?",Integer.class,orderId));
        auditEvents.reject=false;updates.updateOrderLine(r,TransitionTrigger.SELLER);updates.updateOrderLine(r,TransitionTrigger.SELLER);
        assertEquals(1,auditEvents.events.size());var event=auditEvents.events.get(0);
        assertEquals("SELLER_CANCELLATION",event.reasonCode());assertEquals(1,event.cancellationOrdinalFrom());assertEquals(3,event.cancellationOrdinalTo());assertEquals(3,event.unacceptedQty());assertEquals("seller@test.invalid",event.actorIdentity());
    }
    @Test void oldWholeOrderRouteRequiresTokensUsesQuantityPolicyAndReplaysAfterClosure(){
        IDENTITY.set("buyer@test.invalid");var r=new OrderCancelRequestDTO();r.setOrderId(orderId);r.setReason("Customer whole-order request");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->orderService.cancelOrder(r,TransitionTrigger.CUSTOMER));
        r.setOperationId("whole-order");r.setExpectedVersion(0L);var first=orderService.cancelOrder(r,TransitionTrigger.CUSTOMER);
        clock.value.set(CONFIRMED.plusSeconds(15000));var repeated=orderService.cancelOrder(r,TransitionTrigger.CUSTOMER);
        assertEquals("CANCELLED",repeated.getWorkflow().state());assertEquals(first.getWorkflow().serverNow(),repeated.getWorkflow().serverNow());assertEquals(10,repeated.getOrderLines().get(0).getCancelledQty());
    }
    @Test void legacyAdoptionStartsProspectiveDeadlinesWithoutChangingHistoricalConfirmation(){
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();o.setWorkflowVersion(1);o.setWorkflowReviewRequired(true);});
        clock.value.set(CONFIRMED.plusSeconds(200000));IDENTITY.set("admin@test.invalid");
        var r=new WorkflowAdoption.Request(orderId,"adopt",0L,"Verified captured funds and allocation");
        var first=adoption.adopt(r,TransitionTrigger.ADMIN);assertFalse(first.reviewRequired());assertEquals(CONFIRMED,first.confirmedAt());assertEquals(clock.instant().plusSeconds(172800),first.acceptDeadline());
        assertEquals(first,adoption.adopt(r,TransitionTrigger.ADMIN));assertThrows(WaitingJobException.class,()->deadlines.handle(deadline("ACCEPT_TIMEOUT")));
        IDENTITY.set("seller@test.invalid");updates.updateOrderLine(request(OrderLineAction.ACCEPT,2,1,"after-adoption"),TransitionTrigger.SELLER);assertEquals(2,snapshot().getAcceptedQty());
    }
    @Test void adoptionDoesNotInventMissingFundingOrConfirmationEvidence(){
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();o.setWorkflowVersion(1);o.setWorkflowReviewRequired(true);o.getPayment().setCapturedAt(null);o.getPayment().setCapturedAmount(null);});
        IDENTITY.set("admin@test.invalid");assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->adoption.adopt(new WorkflowAdoption.Request(orderId,"adopt",0L,"Needs reconciliation"),TransitionTrigger.ADMIN));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key LIKE ?",Integer.class,"fulfillment:"+orderId+":%"));
    }
    @Test void packedUnitsRequireLogisticsAndSellerCancellationNeverChangesShippedHistory(){
        currentCaptureProof();
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,10,0,"accept"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.START_PREPARING,10,1,"prepare"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.MARK_PACKED,10,2,"pack"),TransitionTrigger.SELLER);
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(OrderLineAction.SHIP,3,3,"seller-ship"),TransitionTrigger.SELLER));
        IDENTITY.set("admin@test.invalid");updates.updateOrderLine(request(OrderLineAction.SHIP,3,3,"admin-ship"),TransitionTrigger.ADMIN);
        IDENTITY.set("seller@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,7,4,"cancel-unshipped"),TransitionTrigger.SELLER);
        var l=snapshot();assertEquals(3,l.getShippedQty());assertEquals(3,l.getPackedQty());assertEquals(7,l.getCancelledQty());assertTrue(l.validQuantities());
        assertEquals(17,jdbc.queryForObject("SELECT physical_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
    }
    @Test void canonicalProjectionPublishesAllowedActionsFromCurrentEligibility(){
        var response=updates.updateOrderLine(request(OrderLineAction.ACCEPT,8,0,"accept"),TransitionTrigger.SELLER);
        var projection=json.valueToTree(response).get("workflow");assertTrue(projection.has("allowedActions"),"Canonical response must publish allowedActions");
        assertEquals(List.of("ACCEPT","START_PREPARING","CANCEL"),json.convertValue(projection.get("allowedActions"),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){}));
    }
    @Test void acceptanceMetricsExcludeOnlyCustomerCancellationsBeforeDeadline(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,6,0,"accept"),TransitionTrigger.SELLER);
        IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,3,1,"early-customer"),TransitionTrigger.CUSTOMER);
        clock.value.set(CONFIRMED.plusSeconds(176400));updates.updateOrderLine(request(OrderLineAction.CANCEL,1,2,"boundary-customer"),TransitionTrigger.CUSTOMER);
        var report=json.valueToTree(deadlines.metrics());assertTrue(report.has("acceptance"),"Metrics must publish unit acceptance rates");var a=report.get("acceptance");
        assertEquals(10,a.get("orderedQty").asInt());assertEquals(3,a.get("customerCancelledBeforeDeadlineQty").asInt());assertEquals(7,a.get("acceptanceDenominatorQty").asInt());assertEquals(6,a.get("onTimeAcceptedQty").asInt());assertEquals(6.0/7,a.get("onTimeAcceptanceRate").asDouble(),0.0000001);
    }
    @Test void sellerDelayStaysInAcceptanceDenominator(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,4,0,"accept"),TransitionTrigger.SELLER);
        IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,2,1,"customer"),TransitionTrigger.CUSTOMER);
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("ACCEPT_TIMEOUT"));
        var a=json.valueToTree(deadlines.metrics()).get("acceptance");assertNotNull(a);assertEquals(8,a.get("acceptanceDenominatorQty").asInt());assertEquals(4,a.get("onTimeAcceptedQty").asInt());assertEquals(4,a.get("timeoutCancelledQty").asInt());assertEquals(0.5,a.get("onTimeAcceptanceRate").asDouble());
    }
    @Test void metricsHaveNoMatureDenominatorBeforeDeadline(){
        var a=json.valueToTree(deadlines.metrics()).get("acceptance");assertNotNull(a);assertEquals(0,a.get("acceptanceDenominatorQty").asInt());assertTrue(a.get("onTimeAcceptanceRate").isNull());
    }
    @Test void adoptionMetricsUseProspectiveActiveAndAcceptedBaselines(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,6,0,"old-accept"),TransitionTrigger.SELLER);
        updates.updateOrderLine(request(OrderLineAction.CANCEL,2,1,"old-cancel"),TransitionTrigger.SELLER);
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();o.setWorkflowVersion(1);o.setWorkflowReviewRequired(true);});
        Instant adopted=CONFIRMED.plusSeconds(200000);clock.value.set(adopted);IDENTITY.set("admin@test.invalid");adoption.adopt(new WorkflowAdoption.Request(orderId,"adopt",2L,"Prospective SLA"),TransitionTrigger.ADMIN);
        clock.value.set(adopted.plusSeconds(10));IDENTITY.set("seller@test.invalid");updates.updateOrderLine(request(OrderLineAction.ACCEPT,1,3,"new-accept"),TransitionTrigger.SELLER);
        clock.value.set(adopted.plusSeconds(20));IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,1,4,"new-cancel"),TransitionTrigger.CUSTOMER);
        clock.value.set(adopted.plusSeconds(172800));var a=json.valueToTree(deadlines.metrics()).get("acceptance");assertNotNull(a);assertEquals(8,a.get("orderedQty").asInt());assertEquals(7,a.get("acceptanceDenominatorQty").asInt());assertEquals(7,a.get("onTimeAcceptedQty").asInt());assertEquals(1.0,a.get("onTimeAcceptanceRate").asDouble());
    }
    @Test void retrySchedulingUsesTheInjectedDurableJobClock(){
        var job=queue.enqueue("CLOCK_WAIT","clock-retry:"+orderId,Map.of());
        var handler=new JobHandler(){public String type(){return "CLOCK_WAIT";}public void handle(JobRecord record){throw new WaitingJobException("Synthetic waiting state",Duration.ofSeconds(30));}};
        new JobProcessor(jobs,List.of(handler)).process(jobs.claim("retry-clock",1,Duration.ofSeconds(60)).get(0));
        assertEquals(clock.instant().plusSeconds(30),jobs.get(job.id()).dueAt());
    }
    @Test void sellerMetricsAreDerivedFromAuthenticatedOwnershipAndPublishCoverage(){
        addSecondLine(false,"another-seller@test.invalid");updates.updateOrderLine(request(OrderLineAction.ACCEPT,6,0,"accept"),TransitionTrigger.SELLER);
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("ACCEPT_TIMEOUT"));
        var own=json.valueToTree(workflowController.sellerMetrics(CONFIRMED,CONFIRMED.plusSeconds(1)));
        assertTrue(own.get("coverage").get("sellerScoped").asBoolean());assertEquals(CONFIRMED.toString(),own.get("coverage").get("fromInclusive").asText());
        assertEquals(10,own.get("acceptance").get("orderedQty").asInt());assertEquals(6,own.get("acceptance").get("onTimeAcceptedQty").asInt());assertEquals(4,own.get("acceptance").get("timeoutCancelledQty").asInt());
        IDENTITY.set("another-seller@test.invalid");var other=json.valueToTree(workflowController.sellerMetrics(CONFIRMED,CONFIRMED.plusSeconds(1)));
        assertEquals(2,other.get("acceptance").get("orderedQty").asInt());assertEquals(0,other.get("acceptance").get("onTimeAcceptedQty").asInt());assertEquals(2,other.get("acceptance").get("timeoutCancelledQty").asInt());
        var excluded=json.valueToTree(workflowController.sellerMetrics(CONFIRMED.minusSeconds(1),CONFIRMED));assertEquals(0,excluded.get("acceptance").get("cohortLines").asInt());
    }
    @Test void acceptedCustomerCancellationsBeforeDeadlineReduceBothNumeratorAndDenominator(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,10,0,"accept"),TransitionTrigger.SELLER);IDENTITY.set("buyer@test.invalid");
        updates.updateOrderLine(request(OrderLineAction.CANCEL,3,1,"customer"),TransitionTrigger.CUSTOMER);clock.value.set(CONFIRMED.plusSeconds(200000));
        var a=json.valueToTree(deadlines.metrics()).get("acceptance");assertEquals(7,a.get("onTimeAcceptedQty").asInt());assertEquals(7,a.get("acceptanceDenominatorQty").asInt());assertEquals(1.0,a.get("onTimeAcceptanceRate").asDouble());
    }
    void assertCancellationReconciliationRollback(int reservationQty,int requestedQty){
        if(reservationQty<0)jdbc.update("DELETE FROM order_reservations WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",orderId);
        else jdbc.update("UPDATE order_reservations SET reserved_qty=? WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",reservationQty,orderId);
        int productBalance=reservationQty<0?10:reservationQty;
        jdbc.update("UPDATE products SET reserved_qty=? WHERE product_id=?",productBalance,orderId+"-P");
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->updates.updateOrderLine(request(OrderLineAction.CANCEL,requestedQty,0,"reconcile-first"),TransitionTrigger.SELLER));
        assertEquals(0,snapshot().getCancelledQty());assertEquals(0L,tx.execute(t->orders.findByOrderId(orderId).orElseThrow().getWorkflowRevision()).longValue());
        assertEquals(productBalance,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fulfillment_commands WHERE order_id=?",Integer.class,orderId));
        assertTrue(auditEvents.events.isEmpty());verifyNoInteractions(refundService);
        if(reservationQty>=0)assertEquals(reservationQty,jdbc.queryForObject("SELECT reserved_qty FROM order_reservations WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,orderId));
    }
    @Test void allocatedCancellationRejectsMissingReservation(){assertCancellationReconciliationRollback(-1,5);}
    @Test void terminalCancellationRejectsOversizedReservationInsteadOfOrphaningStock(){assertCancellationReconciliationRollback(12,10);}
    @Test void cancellationRejectsUndersizedReservationEvenWhenItCoversRequestedUnits(){assertCancellationReconciliationRollback(5,2);}
    @Test void genuinelyUnallocatedUnpaidCancellationDoesNotConsumeAnotherAllocation(){
        String unpaidId="UNPAID-"+UUID.randomUUID().toString().substring(0,8);String unpaidFulfillment=unpaidId+"-F";
        tx.executeWithoutResult(t->{
            var o=new Order();o.setOrderId(unpaidId);o.setCustomerName("Buyer");o.setCustomerEmail("buyer@test.invalid");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.INITIATED);o.setCurrency("INR");o.setSubtotal(new BigDecimal("100"));o.setCharges(BigDecimal.ZERO);o.setTotalAmount(new BigDecimal("100"));o.setRecipientName("Buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Synthetic unpaid address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");
            o.addOrderLine(OrderLine.builder().fulfillmentId(unpaidFulfillment).orderLineNumber(1).productId(orderId+"-P").productName("Unpaid product").unitPrice(BigDecimal.TEN).orderedQty(10).lineTotal(new BigDecimal("100")).sellerEmail("seller@test.invalid").sellerName("Seller").orderLineStatus(OrderLineStatus.CREATED).build());orders.saveAndFlush(o);
        });
        var r=request(OrderLineAction.CANCEL,5,0,"unpaid-cancel");r.setFulfillmentId(unpaidFulfillment);IDENTITY.set("buyer@test.invalid");
        assertEquals(5,updates.updateOrderLine(r,TransitionTrigger.CUSTOMER).getCancelledQty());
        assertEquals(10,jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,orderId+"-P"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON r.order_fk=o.id WHERE o.order_id=?",Integer.class,unpaidId));
    }
    JobRecord fulfillmentMail(String seller,String kind){
        return jobs.get(jdbc.queryForObject("SELECT id FROM durable_jobs WHERE business_key LIKE ? AND email_recipient=?",UUID.class,"fulfillment-mail:"+orderId+":"+kind+":%",seller));
    }
    List<JobRecord> claimFulfillmentMail(String owner,int limit){
        var claimed=jobs.claim(owner,limit,Duration.ofMinutes(1));
        // Business eligibility uses a fixed Clock; SMTP tracking deliberately fences with DB time.
        // Keep test-owned leases valid on both clocks without depending on today's calendar date.
        Instant databaseNow=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",(r,n)->r.getTimestamp(1).toInstant());
        Instant leaseUntil=(databaseNow.isAfter(clock.instant())?databaseNow:clock.instant()).plusSeconds(60);
        for(var job:claimed)assertEquals(1,jdbc.update("UPDATE durable_jobs SET lease_expires_at=? WHERE id=? AND lease_token=? AND lease_owner=? AND status='RUNNING'",java.sql.Timestamp.from(leaseUntil),job.id(),job.leaseToken(),job.leaseOwner()));
        return claimed.stream().map(job->jobs.get(job.id())).toList();
    }
    void deliverQueuedFulfillmentMail(){
        for(var job:claimFulfillmentMail("fulfillment-smtp",10))new JobProcessor(jobs,List.of(authEmailHandler)).process(job);
    }
    void assertSkippedMail(String seller,String kind){
        var job=fulfillmentMail(seller,kind);assertEquals(JobStatus.SUCCEEDED,job.status());
        var row=jdbc.queryForMap("SELECT email_state,email_code,email_accepted_at FROM durable_jobs WHERE id=?",job.id());
        assertEquals("SKIPPED",row.get("email_state"));assertEquals("FULFILLMENT_NOTICE_INELIGIBLE",row.get("email_code"));assertNull(row.get("email_accepted_at"));
    }
    @Test void lateReminderDoesNotAdvertiseAcceptanceAfterItsDeadline(){
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("REMINDER"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key LIKE ?",Integer.class,"fulfillment-mail:"+orderId+":REMINDER:%"));assertEquals(0,snapshot().getCancelledQty());
    }
    @Test void queuedReleaseAndReminderAreSkippedAfterWholeCustomerCancellation(){
        clock.value.set(CONFIRMED.plusSeconds(172800));deadlines.handle(deadline("RELEASE"));deadlines.handle(deadline("REMINDER"));
        IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"customer-closed"),TransitionTrigger.CUSTOMER);deliverQueuedFulfillmentMail();
        assertTrue(smtpMailbox.sent.isEmpty());assertSkippedMail("seller@test.invalid","RELEASE");assertSkippedMail("seller@test.invalid","REMINDER");
    }
    @Test void queuedNoticesSkipTimedOutSellerButReleaseAcceptedSurvivors()throws Exception{
        addSecondLine(true,"survivor@test.invalid");clock.value.set(CONFIRMED.plusSeconds(172800));deadlines.handle(deadline("RELEASE"));deadlines.handle(deadline("REMINDER"));
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("ACCEPT_TIMEOUT"));deliverQueuedFulfillmentMail();
        assertEquals(1,smtpMailbox.sent.size());assertEquals("survivor@test.invalid",com.mercato.mail.MimeContent.recipient(smtpMailbox.sent.get(0)));
        assertSkippedMail("seller@test.invalid","RELEASE");assertSkippedMail("seller@test.invalid","REMINDER");
        assertEquals("ACCEPTED",jdbc.queryForObject("SELECT email_state FROM durable_jobs WHERE id=?",String.class,fulfillmentMail("survivor@test.invalid","RELEASE").id()));
    }
    @Test void queuedReminderExpiresEvenBeforeTheTimeoutWorkerRuns(){
        clock.value.set(CONFIRMED.plusSeconds(172800));deadlines.handle(deadline("REMINDER"));clock.value.set(CONFIRMED.plusSeconds(176400));deliverQueuedFulfillmentMail();
        assertTrue(smtpMailbox.sent.isEmpty());assertEquals(0,snapshot().getCancelledQty());assertSkippedMail("seller@test.invalid","REMINDER");
    }
    @Test void queuedReleaseAndReminderOnlySkipSellerWhoseOwnUnitsWereCancelled()throws Exception{
        addSecondLine(false,"survivor@test.invalid");clock.value.set(CONFIRMED.plusSeconds(172800));deadlines.handle(deadline("RELEASE"));deadlines.handle(deadline("REMINDER"));
        updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"seller-closed"),TransitionTrigger.SELLER);deliverQueuedFulfillmentMail();
        assertEquals(2,smtpMailbox.sent.size());for(var message:smtpMailbox.sent)assertEquals("survivor@test.invalid",com.mercato.mail.MimeContent.recipient(message));
        assertSkippedMail("seller@test.invalid","RELEASE");assertSkippedMail("seller@test.invalid","REMINDER");
    }
    @Test void lateReleaseStillNotifiesAcceptedSurvivorsAfterTimeout(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,4,0,"accepted-survivors"),TransitionTrigger.SELLER);clock.value.set(CONFIRMED.plusSeconds(200000));
        deadlines.handle(deadline("ACCEPT_TIMEOUT"));deadlines.handle(deadline("RELEASE"));deliverQueuedFulfillmentMail();
        assertEquals(1,smtpMailbox.sent.size());assertEquals(4,snapshot().getAcceptedQty());assertEquals(6,snapshot().getCancelledQty());
        assertEquals("ACCEPTED",jdbc.queryForObject("SELECT email_state FROM durable_jobs WHERE id=?",String.class,fulfillmentMail("seller@test.invalid","RELEASE").id()));
    }
    @Test void acceptedNoticeIsNotRelabelledSkippedWhenCancellationPrecedesCompletionRetry(){
        deadlines.handle(deadline("RELEASE"));var claimed=claimFulfillmentMail("smtp-uncertain-completion",1).get(0);authEmailHandler.handle(claimed);
        IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"customer-closed"),TransitionTrigger.CUSTOMER);authEmailHandler.handle(claimed);
        assertEquals(1,smtpMailbox.sent.size());assertEquals("ACCEPTED",jdbc.queryForObject("SELECT email_state FROM durable_jobs WHERE id=?",String.class,claimed.id()));
    }
    @Test void releaseNotificationCarriesStructuredSellerOrderKindAndDeadline(){
        deadlines.handle(deadline("RELEASE"));var payload=fulfillmentMail("seller@test.invalid","RELEASE").payload();
        assertEquals(orderId,payload.get("orderId"));assertEquals("seller@test.invalid",payload.get("sellerEmail"));assertEquals("RELEASE",payload.get("fulfillmentNotificationKind"));assertEquals(CONFIRMED.plusSeconds(176400).toString(),payload.get("acceptDeadline"));
    }
    @Test void legacyQueuedFulfillmentMailRechecksCancellationBeforeDelivery(){
        queue.enqueue("AUTH_EMAIL","fulfillment-mail:"+orderId+":RELEASE:seller@test.invalid",Map.of("to","seller@test.invalid","subject","Old fulfillment notice","text","Review the order"));
        IDENTITY.set("buyer@test.invalid");updates.updateOrderLine(request(OrderLineAction.CANCEL,10,0,"customer-closed"),TransitionTrigger.CUSTOMER);deliverQueuedFulfillmentMail();
        assertTrue(smtpMailbox.sent.isEmpty());assertSkippedMail("seller@test.invalid","RELEASE");
    }
    @Test void legacyQueuedReleasePreservesLegitimateAcceptedSurvivorsAfterTimeout(){
        updates.updateOrderLine(request(OrderLineAction.ACCEPT,4,0,"accepted-survivors"),TransitionTrigger.SELLER);
        queue.enqueue("AUTH_EMAIL","fulfillment-mail:"+orderId+":RELEASE:seller@test.invalid",Map.of("to","seller@test.invalid","subject","Old fulfillment notice","text","Review the order"));
        clock.value.set(CONFIRMED.plusSeconds(200000));deadlines.handle(deadline("ACCEPT_TIMEOUT"));deliverQueuedFulfillmentMail();
        assertEquals(1,smtpMailbox.sent.size());assertEquals("ACCEPTED",jdbc.queryForObject("SELECT email_state FROM durable_jobs WHERE id=?",String.class,fulfillmentMail("seller@test.invalid","RELEASE").id()));
    }
    @Test void validLongSellerEmailKeepsFulfillmentNotificationKeyBounded()throws Exception{
        String email="a".repeat(64)+"@"+"b".repeat(63)+"."+"c".repeat(63)+"."+"d".repeat(56)+".test";
        new jakarta.mail.internet.InternetAddress(email,true).validate();assertEquals(254,email.length());
        // Exercise the enqueue boundary independently of the unrelated legacy snapshot email column width.
        var detached=tx.execute(t->{var o=orders.findByOrderId(orderId).orElseThrow();o.getPayment().getCapturedAt();o.getPayment().getRefunds().size();o.getOrderLines().forEach(l->l.getMoneyUnits().size());return o;});detached.getOrderLines().get(0).setSellerEmail(email);
        var repository=mock(OrderRepository.class);when(repository.lockByOrderId(orderId)).thenReturn(Optional.of(detached));
        var policy=new FulfillmentWorkflow(clock,new FulfillmentFunding());
        var handler=new FulfillmentDeadlines(queue,repository,policy,updates,jdbc,mock(FulfillmentSlaMetrics.class),new FulfillmentNoticeGuard(repository,policy,new SellerOrderVisibility(policy,clock)));
        assertDoesNotThrow(()->tx.executeWithoutResult(t->handler.handle(deadline("RELEASE"))));
        var id=jdbc.queryForObject("SELECT id FROM durable_jobs WHERE email_recipient=?",UUID.class,email);var job=jobs.get(id);
        assertTrue(job.businessKey().length()<=255);assertEquals(email,job.payload().get("sellerEmail"));
        tx.executeWithoutResult(t->handler.handle(deadline("RELEASE")));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE email_recipient=?",Integer.class,email));
    }
}
