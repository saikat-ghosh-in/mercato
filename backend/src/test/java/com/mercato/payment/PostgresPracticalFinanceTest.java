package com.mercato.payment;

import com.mercato.Commerce.*;
import com.mercato.Entity.*;
import com.mercato.Entity.Role;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=PostgresPracticalFinanceTest.Config.class,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false","mercato.jobs.enabled=false"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class PostgresPracticalFinanceTest {
    static final String DATABASE=PostgresTestSupport.database();static final Instant NOW=Instant.parse("2026-10-27T06:00:00Z");
    static final ThreadLocal<EcommUser> ACTOR=new ThreadLocal<>();
    @Configuration @EnableAutoConfiguration @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Commerce","com.mercato.Jobs"})
    @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Commerce"})
    @Import({OrderLineUpdateServiceImpl.class,FulfillmentWorkflow.class,FulfillmentFunding.class,WorkflowCommands.class,OrderReservationServiceImpl.class,RefundServiceImpl.class,RefundObligations.class,FinancialUnitHooks.class,MarketplaceFinanceService.class,PaymentSessionRecovery.class,RefundExecution.class,DurableJobs.class,JobStore.class})
    static class Config {
        @Bean(name={"clock","durableJobClock"}) Clock clock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean AuthUtil auth(){var auth=mock(AuthUtil.class);when(auth.getLoggedInUser()).thenAnswer(c->ACTOR.get());return auth;}
        @Bean CashfreeGateway gateway(){return mock(CashfreeGateway.class);}
        @Bean ShipmentNotificationService mail(){return mock(ShipmentNotificationService.class);}
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->DATABASE);p.add("spring.datasource.username",PostgresTestSupport::username);p.add("spring.datasource.password",PostgresTestSupport::password);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");}
    @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
    @Autowired WorkflowCommands commands;@Autowired OrderReservationService inventory;@Autowired RefundExecution refundExecution;@Autowired CashfreeGateway gateway;@Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired OrderRepository orders;@Autowired ProductRepository products;@Autowired CategoryRepository categories;@Autowired OrderReservationRepository reservations;@Autowired UserRepository users;@Autowired RoleRepository roles;
    @Autowired OrderLineUpdateService updates;@Autowired FinancialUnitHooks hooks;@Autowired MarketplaceFinanceService finance;@Autowired PaymentSessionRecovery recovery;@Autowired PlatformTransactionManager transactions;@Autowired JdbcTemplate jdbc;
    TransactionTemplate tx;String id,fulfillment;EcommUser seller,buyer,admin;
    EcommUser actor(String email,String userId,AppRole role){var u=new EcommUser();u.setEmail(email);u.setUserId(userId);u.setRoles(new HashSet<>(Set.of(new Role(role))));return u;}
    @BeforeEach void fixture(){
        reset(gateway);tx=new TransactionTemplate(transactions);id="B2-"+UUID.randomUUID().toString().substring(0,12);fulfillment=id+"-F";
        admin=actor("admin@test.invalid","ADMIN",AppRole.ROLE_ADMIN);buyer=actor("buyer@test.invalid",id+"BUYER",AppRole.ROLE_USER);
        seller=tx.execute(t->{var role=roles.findByRoleName(AppRole.ROLE_SELLER).orElseGet(()->roles.save(new Role(AppRole.ROLE_SELLER)));var u=EcommUser.builder().username(id).email(id+"@test.invalid").password("test-hash").sellerStatus(SellerStatus.APPROVED).emailVerified(true).roles(new HashSet<>(Set.of(role))).build();return users.saveAndFlush(u);});
        ACTOR.set(seller);
        tx.executeWithoutResult(t->{
            var c=new Category();c.setCategoryName(id);categories.save(c);var p=Product.builder().productId(id+"P").productName("Financial unit").description("Synthetic attribution regression").primaryImageUrl("/test.jpg").category(c).retailPrice(new BigDecimal("100")).physicalQty(10).reservedQty(3).build();products.save(p);
            var o=new Order();o.setOrderId(id);o.setCustomerUserId(buyer.getUserId());o.setCustomerEmail(buyer.getEmail());o.setCustomerName("Buyer");o.setCurrency("INR");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.SUCCESS);o.setSubtotal(new BigDecimal("300"));o.setCharges(new BigDecimal("63"));o.setTotalAmount(new BigDecimal("363"));o.setPlatformFee(new BigDecimal("27"));o.setHandlingCharge(new BigDecimal("3"));o.setShippingCharge(new BigDecimal("33"));o.setFinancialPolicyVersion(1);o.setRecipientName("Buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Synthetic address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");
            o.setCheckoutOperationId("intent");o.setCheckoutRequestBody("[\"saved-address\",\"363\",\"STANDARD\",7]");
            var l=new OrderLine();l.setFulfillmentId(fulfillment);l.setOrderLineNumber(1);l.setProductId(p.getProductId());l.setProductName(p.getProductName());l.setUnitPrice(new BigDecimal("100"));l.setOrderedQty(3);l.setLineTotal(new BigDecimal("300"));l.setOrderLineStatus(OrderLineStatus.CONFIRMED);l.setSellerEmail(seller.getEmail());l.setSellerName("Seller");l.setCommissionRatePercent(new BigDecimal("0.5"));l.setCommissionCapPerItem(new BigDecimal("500"));l.setSellerDeliveryCharge(new BigDecimal("15"));o.addOrderLine(l);
            for(int n=1;n<=3;n++){var u=new OrderMoneyUnit();u.setUnitId(id+"U"+n);u.setOrderLine(l);u.setUnitNumber(n);u.setFinancialPolicyVersion(1);u.setNetMerchandisePaise(10000);u.setPriceProvenance("UNKNOWN");u.setBuyerNormalDeliveryPaise(1000);u.setBuyerExpeditedDeliveryPaise(100);u.setPlatformPaise(900);u.setHandlingPaise(100);u.setSellerDeliveryPaise(500);u.setCommissionRatePercent(new BigDecimal("0.5"));u.setCommissionCapPaise(50000L);u.setCommissionPaise(50L);u.setCommissionPolicy("PER_UNIT_CAPPED");u.setState(OrderUnitState.initial(u));l.getMoneyUnits().add(u);}
            var payment=new Payment();payment.setAmount(o.getTotalAmount());payment.setCurrency("INR");payment.setPaymentMethod(PaymentMethod.UNKNOWN);payment.setStatus(PaymentStatus.SUCCESS);payment.setGatewayReference(id+"PAY");payment.setCaptureEvidenceId("verified-test");payment.setCaptureVerifiedAt(NOW);payment.setCapturedAt(NOW);payment.setCapturedAmount(o.getTotalAmount());o.attachPayment(payment);o.confirmOrder();o.setAllocationCompletedAt(NOW.minusSeconds(7200));o.establishConfirmation(NOW.minusSeconds(7200));orders.saveAndFlush(o);reservations.save(OrderReservation.builder().order(o).orderLine(l).product(p).reservedQty(3).build());
        });
    }
    long version(){return jdbc.queryForObject("select workflow_revision from order_snapshot where order_id=?",Long.class,id);}
    OrderLineUpdateRequestDTO request(OrderLineAction action,int qty,String operation){var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(fulfillment);r.setOrderLineNumber(1);r.setAction(action);r.setQty(qty);r.setOperationId(operation);r.setExpectedVersion(version());r.setReason("Synthetic financial test");return r;}
    @Test void pendingCustomerRefundPreservesFundedSurvivorsAndReceivedReturnClosesResidualFees(){
        ACTOR.set(buyer);var cancel=request(OrderLineAction.CANCEL,1,"customer-cancel");updates.updateOrderLine(cancel,TransitionTrigger.CUSTOMER);updates.updateOrderLine(cancel,TransitionTrigger.CUSTOMER);
        assertEquals(new BigDecimal("111.00"),jdbc.queryForObject("select sum(r.amount) from refunds r join payments p on r.payment_fk=p.id join order_snapshot o on o.payment_fk=p.id where o.order_id=?",BigDecimal.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from refund_unit_allocations a join order_money_units u on a.unit_id=u.unit_id where u.unit_id like ?",Integer.class,id+"%"));
        ACTOR.set(seller);updates.updateOrderLine(request(OrderLineAction.ACCEPT,2,"accept"),TransitionTrigger.SELLER);updates.updateOrderLine(request(OrderLineAction.START_PREPARING,2,"prepare"),TransitionTrigger.SELLER);updates.updateOrderLine(request(OrderLineAction.MARK_PACKED,2,"pack"),TransitionTrigger.SELLER);
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(id).orElseThrow();assertEquals(2,hooks.requireShippable(o,List.of(id+"U2",id+"U3")).size());assertThrows(RuntimeException.class,()->hooks.requireShippable(o,List.of(id+"U1")));});
        ACTOR.set(admin);updates.updateOrderLine(request(OrderLineAction.SHIP,2,"ship"),TransitionTrigger.ADMIN);
        assertEquals(new BigDecimal("189.00"),finance.netEarnings(seller,false,Instant.EPOCH,NOW.plusSeconds(1)));assertEquals(new BigDecimal("189.00"),finance.availableBudget(seller));
        long v=version();tx.executeWithoutResult(t->hooks.approveReceivedUnits(id,List.of(id+"U2",id+"U3"),"Task5-validated-receipt","return",v));tx.executeWithoutResult(t->hooks.approveReceivedUnits(id,List.of(id+"U3",id+"U2"),"Task5-validated-receipt","return",v));
        assertEquals(new BigDecimal("363.00"),jdbc.queryForObject("select sum(r.amount) from refunds r join payments p on r.payment_fk=p.id join order_snapshot o on o.payment_fk=p.id where o.order_id=?",BigDecimal.class,id));
        assertEquals(2,jdbc.queryForObject("select shipped_qty from order_lines_snapshot where fulfillment_id=?",Integer.class,fulfillment));assertEquals(8,jdbc.queryForObject("select physical_qty from products where product_id=?",Integer.class,id+"P"));
        assertEquals(new BigDecimal("-10.00"),finance.netEarnings(seller,false,Instant.EPOCH,NOW.plusSeconds(1)));assertEquals(new BigDecimal("0.00"),finance.availableBudget(seller));
        var monthly=finance.monthlyReport(seller.getUserId(),YearMonth.of(2026,10),ZoneOffset.UTC);assertEquals(new BigDecimal("-10.00"),((Map<?,?>)monthly.get("totals")).get("knownNetEarnings"));assertEquals(new BigDecimal("1.00"),((Map<?,?>)monthly.get("totals")).get("commissionReversals"));
        ACTOR.set(buyer);assertTrue(hooks.units(id).stream().allMatch(u->u.refundState().equals("PENDING")));ACTOR.set(actor("other@test.invalid","OTHER",AppRole.ROLE_USER));assertThrows(RuntimeException.class,()->hooks.units(id));
    }
    @Test void parcelHookAdvancesExactlyChosenPackedIdsAndLeavesOneCommandToCaller(){
        ACTOR.set(seller);updates.updateOrderLine(request(OrderLineAction.ACCEPT,3,"accept"),TransitionTrigger.SELLER);updates.updateOrderLine(request(OrderLineAction.START_PREPARING,3,"prepare"),TransitionTrigger.SELLER);updates.updateOrderLine(request(OrderLineAction.MARK_PACKED,3,"pack"),TransitionTrigger.SELLER);ACTOR.set(admin);
        long before=version();tx.executeWithoutResult(t->{var o=orders.lockByOrderId(id).orElseThrow();String body=commands.encode(List.of("PARCEL",id+"U3",before));assertNull(commands.stored(o,"parcel",before,body));var selected=hooks.recordShippedUnits(o,List.of(id+"U3"),"parcel");assertEquals(id+"U3",selected.get(0).getUnitId());assertEquals(before,o.getWorkflowRevision());var l=o.getOrderLines().get(0);l.ship(1);inventory.settleQty(l,1,OrderLineAction.SHIP);o.workflowChanged();commands.recordAggregate(o,"parcel",body,TransitionTrigger.ADMIN,admin.getEmail(),"Synthetic shipment","SHIP",1,"{}");});
        var states=hooks.units(id);assertEquals("PACKED",states.stream().filter(u->u.unitId().equals(id+"U1")).findFirst().orElseThrow().stage());assertEquals("SHIPPED",states.stream().filter(u->u.unitId().equals(id+"U3")).findFirst().orElseThrow().stage());assertEquals(before+1,version());assertEquals(1,jdbc.queryForObject("select count(*) from fulfillment_commands where order_id=? and operation_id='parcel'",Integer.class,id));
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(id).orElseThrow();assertThrows(RuntimeException.class,()->hooks.requireShippable(o,List.of(id+"U3")));});
    }
    @Test void simultaneousSameCancellationCannotDuplicateRefundOrInventoryRelease()throws Exception{
        var r=request(OrderLineAction.CANCEL,1,"concurrent");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{var a=pool.submit(()->{ACTOR.set(buyer);start.await();return updates.updateOrderLine(r,TransitionTrigger.CUSTOMER);});var b=pool.submit(()->{ACTOR.set(buyer);start.await();return updates.updateOrderLine(r,TransitionTrigger.CUSTOMER);});start.countDown();a.get(20,java.util.concurrent.TimeUnit.SECONDS);b.get(20,java.util.concurrent.TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("select count(*) from refunds r join payments p on r.payment_fk=p.id join order_snapshot o on o.payment_fk=p.id where o.order_id=?",Integer.class,id));assertEquals(2,jdbc.queryForObject("select reserved_qty from products where product_id=?",Integer.class,id+"P"));assertEquals(1,version());
    }
    JobRecord job(String refund){return new JobRecord(UUID.randomUUID(),"REFUND_ORDER","test",Map.of("refundId",refund),1,JobStatus.READY,1,NOW,null,null,null,null,0,null,NOW,NOW);}
    String refundId(){return jdbc.queryForObject("select r.refund_id from refunds r join payments p on r.payment_fk=p.id join order_snapshot o on o.payment_fk=p.id where o.order_id=?",String.class,id);}
    @Test void refundTransportReconcilesStableIdentityOutsideTransactionsAndSurvivorsStayActionable()throws Exception{
        ACTOR.set(buyer);updates.updateOrderLine(request(OrderLineAction.CANCEL,1,"customer"),TransitionTrigger.CUSTOMER);String refund=refundId();
        when(gateway.get(anyString())).thenAnswer(c->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());throw new CashfreeGateway.GatewayFailure(404,"not_found");});
        when(gateway.post(anyString(),any(),anyString())).thenAnswer(c->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return json.readTree("[{\"refund_id\":\""+refund+"\",\"cf_payment_id\":\""+id+"PAY\",\"refund_amount\":111,\"refund_currency\":\"INR\",\"refund_status\":\"SUCCESS\",\"cf_refund_id\":\"provider-refund\"}]");});
        refundExecution.handle(job(refund));verify(gateway).post(eq("/orders/"+id+"/refunds"),any(),eq("refund:"+refund));clearInvocations(gateway);refundExecution.handle(job(refund));verifyNoInteractions(gateway);
        assertEquals("SUCCESS",jdbc.queryForObject("select status from refunds where refund_id=?",String.class,refund));ACTOR.set(seller);assertDoesNotThrow(()->updates.updateOrderLine(request(OrderLineAction.ACCEPT,2,"survivors"),TransitionTrigger.SELLER));
    }
    @Test void insufficientFundsAndWrongProviderAttemptNeverBecomeSuccess(){
        ACTOR.set(buyer);updates.updateOrderLine(request(OrderLineAction.CANCEL,1,"customer"),TransitionTrigger.CUSTOMER);String refund=refundId();
        when(gateway.get(anyString())).thenThrow(new CashfreeGateway.GatewayFailure(400,"insufficient_balance"));assertThrows(WaitingJobException.class,()->refundExecution.handle(job(refund)));assertEquals("ONHOLD",jdbc.queryForObject("select status from refunds where refund_id=?",String.class,refund));
        doAnswer(c->json.readTree("{\"refund_id\":\""+refund+"\",\"cf_payment_id\":\"another-attempt\",\"refund_amount\":111,\"refund_currency\":\"INR\",\"refund_status\":\"SUCCESS\"}")).when(gateway).get(anyString());assertThrows(ManualInterventionJobException.class,()->refundExecution.handle(job(refund)));assertEquals("ONHOLD",jdbc.queryForObject("select status from refunds where refund_id=?",String.class,refund));
    }
    @Test void recoveryIsBuyerIdOwnedAndReturnsOriginalRequestWithoutGatewayWork(){
        ACTOR.set(buyer);var result=(Map<?,?>)recovery.recoverIntent("intent");var request=(OrderCaptureRequestDTO)result.get("request");assertEquals("saved-address",request.getAddressId());assertEquals(7L,request.getExpectedCartVersion());assertEquals("intent",request.getOperationId());assertEquals(id,result.get("orderId"));assertEquals("ORDER_UPDATED",result.get("state"));assertNull(result.get("paymentSessionId"));
        ACTOR.set(actor(buyer.getEmail(),"OTHER-ID",AppRole.ROLE_USER));assertThrows(com.mercato.ExceptionHandler.ResourceNotFoundException.class,()->recovery.recoverIntent("intent"));
    }
    @Test void staleAndWrongOwnerReturnCommandsCannotRefundUnshippedUnits(){
        ACTOR.set(seller);assertThrows(RuntimeException.class,()->tx.executeWithoutResult(t->hooks.approveReceivedUnits(id,List.of(id+"U1"),"receipt","return",version())));
        ACTOR.set(admin);assertThrows(RuntimeException.class,()->tx.executeWithoutResult(t->hooks.approveReceivedUnits(id,List.of(id+"U1"),"receipt","return",version())));assertEquals(0,jdbc.queryForObject("select count(*) from refunds r join payments p on r.payment_fk=p.id join order_snapshot o on o.payment_fk=p.id where o.order_id=?",Integer.class,id));
    }
}
