package com.mercato.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Commerce.*;
import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Repository.*;
import com.mercato.Jobs.*;
import com.mercato.jobs.PostgresTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real PostgreSQL replaces mocked rollback and concurrency simulations. */
@SpringBootTest(classes=CashfreeFinalizeConcurrencyTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false","spring.flyway.enabled=false","mercato.jobs.enabled=false","cashfree.secret.key=test-secret"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class CashfreeFinalizeConcurrencyTest {
 private static final String database=PostgresTestSupport.database();
 @Configuration @EnableAutoConfiguration @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Commerce","com.mercato.Jobs"})
 @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Commerce"})
 @Import({JobStore.class,DurableJobs.class,PaymentInbox.class,PaymentReconciler.class,RefundObligations.class,PaidOrderAllocation.class,AllocationHandler.class,OrderReservationServiceImpl.class,CashfreeServiceImpl.class,OrderServiceImpl.class})
 static class Config {
  @Bean CartService carts(){return org.mockito.Mockito.mock(CartService.class);}
  @Bean com.mercato.Utils.AuthUtil auth(){return org.mockito.Mockito.mock(com.mercato.Utils.AuthUtil.class);}
  @Bean CashfreeGateway gateway(){return org.mockito.Mockito.mock(CashfreeGateway.class);}
  @Bean CartReservationService cartReservations(){return org.mockito.Mockito.mock(CartReservationService.class);}
  @Bean OrderLineUpdateService updates(){return org.mockito.Mockito.mock(OrderLineUpdateService.class);}
  @Bean OrderRetryHoldService retryHolds(){return org.mockito.Mockito.mock(OrderRetryHoldService.class);}
  @Bean CommissionService commissions(){return org.mockito.Mockito.mock(CommissionService.class);}
 }
 @DynamicPropertySource static void postgres(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->database);r.add("spring.datasource.username",PostgresTestSupport::username);r.add("spring.datasource.password",PostgresTestSupport::password);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");}
 @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
 @Autowired OrderRepository orders;@Autowired ProductRepository products;@Autowired CategoryRepository categories;
 @Autowired PaymentInbox inbox;@Autowired PaymentReconciler reconciler;@Autowired PaidOrderAllocation allocator;@Autowired AllocationHandler handler;
 @Autowired PlatformTransactionManager transactions;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 @Autowired OrderServiceImpl orderService;@Autowired com.mercato.Utils.AuthUtil auth;@Autowired CashfreeGateway gateway;
 private TransactionTemplate tx;private String orderId;
 @BeforeEach void setup(){tx=new TransactionTemplate(transactions);orderId="ORD-"+UUID.randomUUID().toString().substring(0,12);tx.executeWithoutResult(t->{
  var category=new Category();category.setCategoryName("Test category");categories.save(category);
  var o=new Order();o.setOrderId(orderId);o.setCustomerName("Buyer");o.setCustomerEmail("buyer@mercato.invalid");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.INITIATED);o.setCurrency("INR");o.setSubtotal(new BigDecimal("40.00"));o.setCharges(new BigDecimal("12.00"));o.setTotalAmount(new BigDecimal("52.00"));o.setRecipientName("Buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Test address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");
  for(int i=0;i<2;i++){var p=Product.builder().productId(orderId+"-"+i).productName("Test product").description("Test product description").primaryImageUrl("/images/test.jpg").category(category).retailPrice(new BigDecimal("10.00")).physicalQty(5).build();products.save(p);var l=new OrderLine();l.setFulfillmentId(orderId+"-F"+i);l.setOrderLineNumber(i+1);l.setProductId(p.getProductId());l.setProductName(p.getProductName());l.setUnitPrice(p.getRetailPrice());l.setOrderedQty(2);l.setLineTotal(new BigDecimal("20.00"));l.setRevenue(BigDecimal.ZERO);l.setSellerEmail("seller"+i+"@mercato.invalid");l.setSellerName("Seller");l.setCommissionRatePercent(new BigDecimal("0.5"));l.setCommissionCapPerItem(new BigDecimal("500"));l.setOrderLineStatus(OrderLineStatus.CREATED);o.addOrderLine(l);}
  var p=new Payment();p.setAmount(o.getTotalAmount());p.setCurrency("INR");p.setPaymentMethod(PaymentMethod.UNKNOWN);p.setStatus(PaymentStatus.INITIATED);o.attachPayment(p);orders.save(o);
 });}
 private String event(String status,String attempt,String amount,String currency){var p=json.createObjectNode();p.put("cf_payment_id",attempt);p.put("payment_status",status);p.put("payment_amount",amount);p.put("payment_currency",currency);inbox.polled(orderId,p);return jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=? AND event_json LIKE ? ORDER BY received_at DESC LIMIT 1",String.class,orderId,"%"+attempt+"%");}
 private Order snapshot(){return tx.execute(t->{var o=orders.findByOrderId(orderId).orElseThrow();o.getPayment().getRefunds().size();return o;});}
 @Test void captureSurvivesAllocationRollbackAndRefundIncludesAllCharges(){
  tx.executeWithoutResult(t->products.findByProductIdForUpdate(orderId+"-1").orElseThrow().setPhysicalQty(0));reconciler.process(event("SUCCESS","shortage-"+orderId,"52.00","INR"));
  assertThrows(com.mercato.ExceptionHandler.InsufficientInventoryException.class,()->allocator.allocate(orderId));assertEquals(new BigDecimal("52.00"),snapshot().getPayment().getCapturedAmount());assertEquals(0,jdbc.queryForObject("SELECT sum(reserved_qty) FROM products WHERE product_id LIKE ?",Integer.class,orderId+"%"));
  handler.handle(new JobRecord(UUID.randomUUID(),"ALLOCATE_ORDER","test",Map.of("orderId",orderId),1,JobStatus.READY,1,Instant.now(),null,null,null,null,0,null,Instant.now(),Instant.now()));var o=snapshot();assertTrue(o.isAutomaticFullRefund());assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"refund:"+o.getPayment().getRefund().getRefundId()));
 }
 @Test void failedAttemptCannotDowngradeCapture(){reconciler.process(event("SUCCESS","paid-"+orderId,"52.00","INR"));reconciler.process(event("FAILED","failed-"+orderId,"52.00","INR"));assertEquals(PaymentStatus.SUCCESS,snapshot().getPaymentStatus());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM gateway_payment_attempt WHERE order_id=?",Integer.class,orderId));}
 @Test void concurrentAllocationIsExactlyOnce()throws Exception {reconciler.process(event("SUCCESS","parallel-"+orderId,"52.00","INR"));var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->allocator.allocate(orderId));var b=pool.submit(()->allocator.allocate(orderId));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertNotNull(snapshot().getAllocationCompletedAt());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON o.id=r.order_fk WHERE o.order_id=?",Integer.class,orderId));assertEquals(4,jdbc.queryForObject("SELECT sum(reserved_qty) FROM products WHERE product_id LIKE ?",Integer.class,orderId+"%"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"order-confirmed:"+orderId));}
 @Test void lateCaptureKeepsCancellationAndRefundsActualCapture(){tx.executeWithoutResult(t->orders.lockByOrderId(orderId).orElseThrow().setOrderStatus(OrderStatus.CANCELLED));reconciler.process(event("SUCCESS","late-"+orderId,"52.00","INR"));var o=snapshot();assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertTrue(o.isAutomaticFullRefund());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());}
 @Test void wrongCurrencyCannotAllocateOrMarkPaid(){String id=event("SUCCESS","invalid-"+orderId,"52.00","USD");assertThrows(PoisonJobException.class,()->reconciler.process(id));assertNull(snapshot().getPayment().getCapturedAt());assertNull(snapshot().getAllocationCompletedAt());}
 @Test void verifiedCollectionsIncludeChargesOnceAndExcludeUnpaidOrders(){BigDecimal before=orders.findTotalRevenue();reconciler.process(event("SUCCESS","collections-"+orderId,"52.00","INR"));assertEquals(new BigDecimal("52.00"),orders.findTotalRevenue().subtract(before));}
 @Test void retryPaymentResolvesItsSnapshotBeforeTheGatewayCallWithOsivDisabled(){var buyer=new EcommUser();buyer.setEmail("buyer@mercato.invalid");org.mockito.Mockito.when(auth.getLoggedInUser()).thenReturn(buyer);org.mockito.Mockito.when(gateway.get("/orders/"+orderId)).thenAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return json.createObjectNode().put("order_status","ACTIVE").put("payment_session_id","retry-session");});assertEquals("retry-session",orderService.retryPayment(orderId));assertEquals("retry-session",snapshot().getPayment().getPaymentSessionId());}
 @Test void retryCanRecoverAnOrderWhoseInitialGatewayCreationNeverArrived(){var buyer=new EcommUser();buyer.setUserId("buyer");buyer.setUsername("Buyer");buyer.setEmail("buyer@mercato.invalid");org.mockito.Mockito.when(auth.getLoggedInUser()).thenReturn(buyer);org.mockito.Mockito.when(gateway.get("/orders/"+orderId)).thenThrow(new CashfreeGateway.GatewayFailure(404,"order_not_found"));org.mockito.Mockito.when(gateway.post(org.mockito.ArgumentMatchers.eq("/orders"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("create-order:"+orderId))).thenReturn(json.createObjectNode().put("cf_order_id","cf-created").put("payment_session_id","recovered-session"));assertEquals("recovered-session",orderService.retryPayment(orderId));assertEquals("cf-created",snapshot().getPayment().getCfOrderId());}
 @Test void signedRawBytesAreDurablyRetainedAlongsideTheRedactedWorkPayload()throws Exception{
  byte[] raw=("{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\""+orderId+"\"},\"customer_details\":{\"customer_email\":\"synthetic-private@mercato.invalid\"},\"payment\":{\"cf_payment_id\":\"raw-proof\",\"payment_amount\":52.00,\"payment_currency\":\"INR\"}}}\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  String timestamp="1720000000123";var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));mac.update(timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));String signature=Base64.getEncoder().encodeToString(mac.doFinal(raw));
  inbox.receive(raw,signature,timestamp);inbox.receive(raw,signature,timestamp);
  assertArrayEquals(raw,jdbc.queryForObject("SELECT verified_raw_body FROM gateway_event_inbox WHERE order_id=?",byte[].class,orderId));
  assertEquals(signature,jdbc.queryForObject("SELECT verified_signature FROM gateway_event_inbox WHERE order_id=?",String.class,orderId));assertEquals(timestamp,jdbc.queryForObject("SELECT verified_timestamp FROM gateway_event_inbox WHERE order_id=?",String.class,orderId));assertFalse(jdbc.queryForObject("SELECT event_json FROM gateway_event_inbox WHERE order_id=?",String.class,orderId).contains("synthetic-private"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs j JOIN gateway_event_inbox e ON j.business_key='gateway-event:'||e.id WHERE e.order_id=?",Integer.class,orderId));
 }
 @Test void partiallyCancelledCheckoutCannotResurrectCancelledItems(){tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();var line=o.getOrderLines().get(0);line.cancel(1);line.setOrderLineStatus(line.deriveStatus());});reconciler.process(event("SUCCESS","partial-cancel-"+orderId,"52.00","INR"));allocator.allocate(orderId);var o=snapshot();assertTrue(o.isAutomaticFullRefund());assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON o.id=r.order_fk WHERE o.order_id=?",Integer.class,orderId));}
 @Test void inboxAndJobRollBackTogether(){long before=jdbc.queryForObject("SELECT count(*) FROM durable_jobs",Long.class);tx.executeWithoutResult(t->{event("SUCCESS","rollback-"+orderId,"52.00","INR");t.setRollbackOnly();});assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM durable_jobs",Long.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM gateway_event_inbox WHERE order_id=?",Integer.class,orderId));}
}
