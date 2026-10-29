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
 @Import({SellerOrderVisibility.class,com.mercato.Config.WorkflowClockConfig.class,FulfillmentFunding.class,FulfillmentWorkflow.class,FulfillmentDeadlines.class,FulfillmentSlaMetrics.class,FulfillmentNoticeGuard.class,JobStore.class,DurableJobs.class,PaymentInbox.class,PaymentReconciler.class,RefundObligations.class,PaidOrderAllocation.class,AllocationHandler.class,OrderReservationServiceImpl.class,CashfreeServiceImpl.class,OrderServiceImpl.class})
 static class Config {
  @Bean WorkflowCommands workflowCommands(){return org.mockito.Mockito.mock(WorkflowCommands.class);}
  @Bean FulfillmentCancellation cancellation(){return org.mockito.Mockito.mock(FulfillmentCancellation.class);}
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
 @Autowired GatewayAttemptRepository attempts;
 @Autowired OrderServiceImpl orderService;@Autowired com.mercato.Utils.AuthUtil auth;@Autowired CashfreeGateway gateway;
 private TransactionTemplate tx;private String orderId;
 @BeforeEach void setup(){tx=new TransactionTemplate(transactions);orderId="ORD-"+UUID.randomUUID().toString().substring(0,12);tx.executeWithoutResult(t->{
  var category=new Category();category.setCategoryName("Test category");categories.save(category);
  var o=new Order();o.setOrderId(orderId);o.setCustomerName("Buyer");o.setCustomerEmail("buyer@mercato.invalid");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.INITIATED);o.setCurrency("INR");o.setSubtotal(new BigDecimal("40.00"));o.setCharges(new BigDecimal("12.00"));o.setTotalAmount(new BigDecimal("52.00"));o.setRecipientName("Buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Test address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");
  for(int i=0;i<2;i++){var p=Product.builder().productId(orderId+"-"+i).productName("Test product").description("Test product description").primaryImageUrl("/images/test.jpg").category(category).retailPrice(new BigDecimal("10.00")).physicalQty(5).build();products.save(p);var l=new OrderLine();l.setFulfillmentId(orderId+"-F"+i);l.setOrderLineNumber(i+1);l.setProductId(p.getProductId());l.setProductName(p.getProductName());l.setUnitPrice(p.getRetailPrice());l.setOrderedQty(2);l.setLineTotal(new BigDecimal("20.00"));l.setRevenue(BigDecimal.ZERO);l.setSellerEmail("seller"+i+"@mercato.invalid");l.setSellerName("Seller");l.setCommissionRatePercent(new BigDecimal("0.5"));l.setCommissionCapPerItem(new BigDecimal("500"));l.setOrderLineStatus(OrderLineStatus.CREATED);o.addOrderLine(l);}
  var p=new Payment();p.setAmount(o.getTotalAmount());p.setCurrency("INR");p.setPaymentMethod(PaymentMethod.UNKNOWN);p.setStatus(PaymentStatus.INITIATED);o.attachPayment(p);orders.save(o);
 });}
 private String event(String status,String attempt,String amount,String currency){var p=json.createObjectNode();p.put("cf_payment_id",attempt);p.put("payment_status",status);p.put("payment_amount",amount);p.put("payment_currency",currency);if("SUCCESS".equals(status))p.put("is_captured",true);inbox.polled(orderId,p);return jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=? AND event_json LIKE ? ORDER BY received_at DESC LIMIT 1",String.class,orderId,"%"+attempt+"%");}
 private Order snapshot(){return tx.execute(t->{var o=orders.findByOrderId(orderId).orElseThrow();o.getPayment().getRefunds().size();o.getOrderLines().forEach(l->l.getMoneyUnits().size());return o;});}
 @Test void captureSurvivesAllocationRollbackAndRefundIncludesAllCharges(){
  tx.executeWithoutResult(t->products.findByProductIdForUpdate(orderId+"-1").orElseThrow().setPhysicalQty(0));reconciler.process(event("SUCCESS","shortage-"+orderId,"52.00","INR"));
  assertThrows(com.mercato.ExceptionHandler.InsufficientInventoryException.class,()->allocator.allocate(orderId));assertEquals(new BigDecimal("52.00"),snapshot().getPayment().getCapturedAmount());assertEquals(0,jdbc.queryForObject("SELECT sum(reserved_qty) FROM products WHERE product_id LIKE ?",Integer.class,orderId+"%"));
  handler.handle(new JobRecord(UUID.randomUUID(),"ALLOCATE_ORDER","test",Map.of("orderId",orderId),1,JobStatus.READY,1,Instant.now(),null,null,null,null,0,null,Instant.now(),Instant.now()));var o=snapshot();assertTrue(o.isAutomaticFullRefund());assertEquals(1,o.getWorkflowRevision());assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"refund:"+o.getPayment().getRefund().getRefundId()));
 }
 @Test void failedAttemptCannotDowngradeCapture(){reconciler.process(event("SUCCESS","paid-"+orderId,"52.00","INR"));reconciler.process(event("FAILED","failed-"+orderId,"52.00","INR"));assertEquals(PaymentStatus.SUCCESS,snapshot().getPaymentStatus());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM gateway_payment_attempt WHERE order_id=?",Integer.class,orderId));}
 @Test void concurrentAllocationIsExactlyOnce()throws Exception {reconciler.process(event("SUCCESS","parallel-"+orderId,"52.00","INR"));var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->allocator.allocate(orderId));var b=pool.submit(()->allocator.allocate(orderId));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertNotNull(snapshot().getAllocationCompletedAt());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON o.id=r.order_fk WHERE o.order_id=?",Integer.class,orderId));assertEquals(4,jdbc.queryForObject("SELECT sum(reserved_qty) FROM products WHERE product_id LIKE ?",Integer.class,orderId+"%"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"order-confirmed:"+orderId));}
 @Test void lateCaptureKeepsCancellationAndRefundsActualCapture(){tx.executeWithoutResult(t->orders.lockByOrderId(orderId).orElseThrow().setOrderStatus(OrderStatus.CANCELLED));reconciler.process(event("SUCCESS","late-"+orderId,"52.00","INR"));var o=snapshot();assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertTrue(o.isAutomaticFullRefund());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());}
 @Test void wrongCurrencyCannotAllocateOrMarkPaid(){String id=event("SUCCESS","invalid-"+orderId,"52.00","USD");reconciler.process(id);assertTrue(snapshot().getPayment().isFinancialHalt());assertNull(snapshot().getPayment().getCapturedAt());assertNull(snapshot().getAllocationCompletedAt());}
 @Test void verifiedCollectionsIncludeChargesOnceAndExcludeUnpaidOrders(){BigDecimal before=orders.findTotalRevenue();reconciler.process(event("SUCCESS","collections-"+orderId,"52.00","INR"));assertEquals(new BigDecimal("52.00"),orders.findTotalRevenue().subtract(before));}
 @Test void retryPaymentResolvesItsSnapshotBeforeTheGatewayCallWithOsivDisabled(){var buyer=new EcommUser();buyer.setEmail("buyer@mercato.invalid");org.mockito.Mockito.when(auth.getLoggedInUser()).thenReturn(buyer);org.mockito.Mockito.when(gateway.get("/orders/"+orderId)).thenAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return json.createObjectNode().put("order_status","ACTIVE").put("payment_session_id","retry-session");});assertEquals("retry-session",orderService.retryPayment(orderId));assertEquals("retry-session",snapshot().getPayment().getPaymentSessionId());}
 @Test void retryCanRecoverAnOrderWhoseInitialGatewayCreationNeverArrived(){var buyer=new EcommUser();buyer.setUserId("buyer");buyer.setUsername("Buyer");buyer.setEmail("buyer@mercato.invalid");org.mockito.Mockito.when(auth.getLoggedInUser()).thenReturn(buyer);org.mockito.Mockito.when(gateway.get("/orders/"+orderId)).thenThrow(new CashfreeGateway.GatewayFailure(404,"order_not_found"));org.mockito.Mockito.when(gateway.post(org.mockito.ArgumentMatchers.eq("/orders"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("create-order:"+orderId))).thenReturn(json.createObjectNode().put("cf_order_id","cf-created").put("payment_session_id","recovered-session"));assertEquals("recovered-session",orderService.retryPayment(orderId));assertEquals("cf-created",snapshot().getPayment().getCfOrderId());}
 @Test void signedRawBytesAreDurablyRetainedAlongsideTheRedactedWorkPayload()throws Exception{
  byte[] raw=("{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\""+orderId+"\"},\"customer_details\":{\"customer_email\":\"synthetic-private@mercato.invalid\"},\"payment\":{\"cf_payment_id\":\"raw-proof\",\"payment_amount\":52.00,\"payment_currency\":\"INR\"}}}\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  String timestamp="1720000000123";var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));mac.update(timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));String signature=Base64.getEncoder().encodeToString(mac.doFinal(raw));
  inbox.receive(raw,signature,timestamp);inbox.receive(raw,signature,timestamp);
  assertArrayEquals(raw,jdbc.queryForObject("SELECT verified_raw_body FROM gateway_event_inbox WHERE order_id=?",byte[].class,orderId));
  assertEquals(signature,jdbc.queryForObject("SELECT verified_signature FROM gateway_event_inbox WHERE order_id=?",String.class,orderId));assertEquals(timestamp,jdbc.queryForObject("SELECT verified_timestamp FROM gateway_event_inbox WHERE order_id=?",String.class,orderId));assertFalse(jdbc.queryForObject("SELECT event_json FROM gateway_event_inbox WHERE order_id=?",String.class,orderId).contains("synthetic-private"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs j JOIN gateway_event_inbox e ON j.business_key='gateway-event:'||e.id WHERE e.order_id=?",Integer.class,orderId));
  String id=jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=?",String.class,orderId);reconciler.process(id);
  assertNull(snapshot().getPayment().getCapturedAt());assertNotNull(jdbc.queryForObject("SELECT processed_at FROM gateway_event_inbox WHERE id=?",java.sql.Timestamp.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='ALLOCATE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
  reconciler.process(event("SUCCESS","raw-proof","52.00","INR"));assertEquals(new BigDecimal("52.00"),snapshot().getPayment().getCapturedAmount());assertFalse(snapshot().getPayment().isFinancialHalt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='ALLOCATE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
 }
 @Test void partiallyCancelledCheckoutCannotResurrectCancelledItems(){tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();var line=o.getOrderLines().get(0);line.cancel(1);line.setOrderLineStatus(line.deriveStatus());});reconciler.process(event("SUCCESS","partial-cancel-"+orderId,"52.00","INR"));allocator.allocate(orderId);var o=snapshot();assertTrue(o.isAutomaticFullRefund());assertEquals(OrderStatus.CANCELLED,o.getOrderStatus());assertEquals(new BigDecimal("52.00"),o.getPayment().getRefund().getAmount());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM order_reservations r JOIN order_snapshot o ON o.id=r.order_fk WHERE o.order_id=?",Integer.class,orderId));}
 @Test void successWithoutCaptureEvidenceSchedulesAuthenticatedReconciliation() {
  var p=json.createObjectNode();p.put("cf_payment_id","ordinary-auth-id");p.put("payment_status","SUCCESS");p.put("payment_amount","52.00");p.put("payment_currency","INR");p.put("auth_id","ordinary-bank-reference");
  inbox.polled(orderId,p);
  String id=jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=?",String.class,orderId);
  reconciler.process(id);
  assertNull(snapshot().getPayment().getCapturedAt(),"SUCCESS and auth_id do not prove captured funds");
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='ALLOCATE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
 }
 @Test void sameAttemptCanEvolveFromVerifiedHoldToPartialCapture() {
  var p=json.createObjectNode();p.put("cf_payment_id","evolving");p.put("auth_id","hold-identity");p.put("payment_status","SUCCESS");p.put("payment_amount","52.00");p.put("payment_currency","INR");p.put("is_captured",false);p.put("payment_time","2026-10-20T10:00:00Z");
  var a=p.putObject("authorisation");a.put("start_time","2026-10-20T10:00:00Z");a.put("end_time","2026-10-27T10:00:00Z");
  inbox.polled(orderId,p);
  String hold=jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=?",String.class,orderId);
  reconciler.process(hold);assertNull(snapshot().getPayment().getCapturedAt());
  p.put("is_captured",true);a.put("action","CAPTURE");a.put("status","SUCCESS");a.put("captured_amount","32.00");a.put("action_time","2026-10-21T10:00:00Z");
  inbox.polled(orderId,p);
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM gateway_event_inbox WHERE order_id=?",Integer.class,orderId));
  String capture=jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=? AND id<>?",String.class,orderId,hold);
  reconciler.process(capture);
  assertEquals(new BigDecimal("32.00"),snapshot().getPayment().getCapturedAmount());
  var response=json.valueToTree(com.mercato.Mapper.PaymentMapper.toDTO(snapshot().getPayment()));
  assertTrue(response.hasNonNull("providerCapturedAt"));
  assertEquals(Instant.parse("2026-10-21T10:00:00Z"),Instant.parse(response.path("providerCapturedAt").asText()));
 }
 private String observation(com.fasterxml.jackson.databind.JsonNode p) {
  inbox.polled(orderId,p);
  return jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=? AND processed_at IS NULL ORDER BY received_at DESC LIMIT 1",String.class,orderId);
 }
 private com.fasterxml.jackson.databind.node.ObjectNode hold(String id) {
  var p=json.createObjectNode().put("cf_payment_id",id).put("auth_id","auth-"+id).put("payment_status","SUCCESS").put("payment_amount","52.00").put("payment_currency","INR").put("is_captured",false);
  p.putObject("authorization").put("start_time","2026-10-20T10:00:00Z").put("end_time","2026-10-27T10:00:00Z");return p;
 }
 private com.fasterxml.jackson.databind.node.ObjectNode completedCapture(String id) {
  var p=hold(id).put("is_captured",true);
  ((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).put("action","CAPTURE").put("status","SUCCESS").put("captured_amount","52.00").put("action_time","2026-10-21T10:00:00Z");return p;
 }
 private void assertPrimaryEvidenceUnchanged(Payment before,Payment after) {
  assertEquals(before.getGatewayReference(),after.getGatewayReference());assertEquals(before.getCaptureEvidenceId(),after.getCaptureEvidenceId());
  assertEquals(before.getCapturedAmount(),after.getCapturedAmount());assertEquals(before.getCapturedAt(),after.getCapturedAt());assertEquals(before.getCaptureVerifiedAt(),after.getCaptureVerifiedAt());assertEquals(before.getProviderCapturedAt(),after.getProviderCapturedAt());
  assertEquals(before.getAuthorizationId(),after.getAuthorizationId());assertEquals(before.getAuthorizedAmount(),after.getAuthorizedAmount());assertEquals(before.getAuthorizedAt(),after.getAuthorizedAt());assertEquals(before.getAuthorizationExpiresAt(),after.getAuthorizationExpiresAt());
 }
 @Test void omittedAuthorizationIdentityCannotInvalidateTheSameAttemptsProvenCapture() {
  var p=completedCapture("late-omission");String firstId=observation(p);reconciler.process(firstId);var original=snapshot().getPayment();
  p.remove("auth_id");String omittedId=observation(p);reconciler.process(omittedId);
  var current=snapshot().getPayment();assertFalse(current.isFinancialHalt());assertPrimaryEvidenceUnchanged(original,current);assertTrue(new FulfillmentFunding().verified(snapshot()));
  var attempt=attempts.findById("cashfree:late-omission").orElseThrow();assertEquals("auth-late-omission",attempt.getAuthorizationId());assertEquals(firstId,attempt.getCaptureEvidenceId());assertEquals(Instant.parse("2026-10-20T10:00:00Z"),attempt.getAuthorizedAt());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"conflicting-evidence:"+omittedId));
  ((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).remove("start_time");((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).remove("end_time");
  reconciler.process(observation(p));assertPrimaryEvidenceUnchanged(original,snapshot().getPayment());assertFalse(snapshot().getPayment().isFinancialHalt());
 }
 @Test void anotherAttemptsHoldCannotBecomeTheCapturedPrimaryPaymentsAuthorization() {
  reconciler.process(event("SUCCESS","ordinary-primary","52.00","INR"));var original=snapshot().getPayment();
  String holdId=observation(hold("other-hold"));reconciler.process(holdId);
  var current=snapshot().getPayment();assertPrimaryEvidenceUnchanged(original,current);assertFalse(current.isFinancialHalt());
  var attempt=attempts.findById("cashfree:other-hold").orElseThrow();assertEquals("auth-other-hold",attempt.getAuthorizationId());assertEquals("AUTHORIZED",attempt.getEvidenceState());assertNull(attempt.getCapturedAt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"authorization-review:"+holdId));
  reconciler.process(observation(completedCapture("ordinary-primary")));assertFalse(snapshot().getPayment().isFinancialHalt());assertEquals("auth-ordinary-primary",snapshot().getPayment().getAuthorizationId());assertEquals(original.getCaptureEvidenceId(),snapshot().getPayment().getCaptureEvidenceId());
 }
 @Test void extraCaptureKeepsItsOwnProofBeforeAttentionWithoutChangingPrimaryAuthorization() {
  reconciler.process(observation(completedCapture("authorized-primary")));var original=snapshot().getPayment();
  String extraId=observation(completedCapture("extra-capture"));reconciler.process(extraId);
  var current=snapshot().getPayment();assertPrimaryEvidenceUnchanged(original,current);assertTrue(current.isFinancialHalt());
  var attempt=attempts.findById("cashfree:extra-capture").orElseThrow();assertEquals("CAPTURED",attempt.getEvidenceState());assertEquals(new BigDecimal("52.00"),attempt.getCapturedAmount());assertEquals(extraId,attempt.getCaptureEvidenceId());assertNotNull(attempt.getCaptureVerifiedAt());assertNotNull(attempt.getCapturedAt());assertEquals(Instant.parse("2026-10-21T10:00:00Z"),attempt.getProviderCapturedAt());assertEquals("PROVIDER_ACTION",attempt.getProviderCaptureTimeSource());assertEquals("auth-extra-capture",attempt.getAuthorizationId());assertEquals(Instant.parse("2026-10-20T10:00:00Z"),attempt.getAuthorizedAt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"conflicting-evidence:"+extraId));assertNotNull(jdbc.queryForObject("SELECT processed_at FROM gateway_event_inbox WHERE id=?",java.sql.Timestamp.class,extraId));
 }
 @Test void pendingHoldOnAnotherAttemptCannotBeGuessedAsAnOrdinaryCapturesAuthorization() {
  reconciler.process(observation(hold("pending-owner")));var original=snapshot().getPayment();
  String captureId=event("SUCCESS","ordinary-with-other-hold","52.00","INR");reconciler.process(captureId);
  var current=snapshot().getPayment();assertPrimaryEvidenceUnchanged(original,current);assertEquals("AUTHORIZATION_ATTEMPT_OWNERSHIP_REQUIRES_REVIEW",current.getFinancialHaltReason());assertTrue(current.isFinancialHalt());
  var attempt=attempts.findById("cashfree:ordinary-with-other-hold").orElseThrow();assertEquals("CAPTURED",attempt.getEvidenceState());assertEquals(new BigDecimal("52.00"),attempt.getCapturedAmount());assertEquals(captureId,attempt.getCaptureEvidenceId());assertNotNull(attempt.getCaptureVerifiedAt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"authorization-ownership:"+captureId));
 }
 @Test void matchingAuthorizationIdentityAloneCannotClaimAnotherPendingAttemptsOwnership() {
  reconciler.process(observation(hold("verified-owner")));var original=snapshot().getPayment();
  var incoming=completedCapture("new-owner-claim").put("auth_id","auth-verified-owner");String captureId=observation(incoming);reconciler.process(captureId);
  assertPrimaryEvidenceUnchanged(original,snapshot().getPayment());assertEquals("AUTHORIZATION_ATTEMPT_OWNERSHIP_REQUIRES_REVIEW",snapshot().getPayment().getFinancialHaltReason());
  var attempt=attempts.findById("cashfree:new-owner-claim").orElseThrow();assertEquals("CAPTURED",attempt.getEvidenceState());assertEquals(captureId,attempt.getCaptureEvidenceId());assertEquals("auth-verified-owner",attempt.getAuthorizationId());
 }
 @Test void captureWithoutAuthorizationIdentityNeverPopulatesABlankVerifiedIdentity() {
  var p=completedCapture("unknown-authorization");p.remove("auth_id");reconciler.process(observation(p));
  assertNull(snapshot().getPayment().getAuthorizationId());assertNull(snapshot().getPayment().getAuthorizedAt());
  var attempt=attempts.findById("cashfree:unknown-authorization").orElseThrow();assertNull(attempt.getAuthorizationId());assertNull(attempt.getAuthorizedAt());assertEquals("CAPTURED",attempt.getEvidenceState());assertFalse(snapshot().getPayment().isFinancialHalt());
 }
 @Test void authorizationIsPersistedButCannotActivateAllocationOrDispatch() {
  reconciler.process(observation(hold("uncollected")));
  var p=snapshot().getPayment();assertEquals(PaymentStatus.AUTHORIZED,p.getStatus());
  assertEquals(Instant.parse("2026-10-20T10:00:00Z"),p.getAuthorizedAt());assertEquals(new BigDecimal("52.00"),p.getAuthorizedAmount());
  assertNull(p.getCapturedAt());assertFalse(new FulfillmentFunding().verified(snapshot()));
  assertThrows(PoisonJobException.class,()->allocator.allocate(orderId));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='ALLOCATE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
 }
 @Test void partialCaptureRecordsActualCollectionWithoutAllocatingTheOriginalOrder() {
  var p=hold("partial");p.put("is_captured",true);((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).put("action","CAPTURE").put("status","SUCCESS").put("captured_amount","32.00");
  reconciler.process(observation(p));
  assertEquals(new BigDecimal("32.00"),snapshot().getPayment().getCapturedAmount());assertTrue(snapshot().getPayment().isFinancialHalt());
  assertFalse(new FulfillmentFunding().verified(snapshot()));assertThrows(PoisonJobException.class,()->allocator.allocate(orderId));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='ALLOCATE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
 }
 @Test void olderHoldAndFailureDoNotDowngradeCapturedOrRefundedFacts() {
  reconciler.process(event("SUCCESS","preserved","52.00","INR"));
  tx.executeWithoutResult(t->{var o=orders.lockByOrderId(orderId).orElseThrow();o.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);o.getPayment().setStatus(PaymentStatus.PARTIALLY_REFUNDED);});
  reconciler.process(observation(hold("preserved")));
  reconciler.process(event("FAILED","preserved","52.00","INR"));
  assertEquals(new BigDecimal("52.00"),snapshot().getPayment().getCapturedAmount());assertEquals(PaymentStatus.PARTIALLY_REFUNDED,snapshot().getPayment().getStatus());assertFalse(snapshot().getPayment().isFinancialHalt());
 }
 @Test void explicitContradictionBlocksDispatchAndKeepsTheOriginalCaptureEvidence() {
  reconciler.process(event("SUCCESS","contradiction","52.00","INR"));String original=snapshot().getPayment().getCaptureEvidenceId();
  var p=hold("contradiction");((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).put("action","CAPTURE").put("status","SUCCESS").put("captured_amount","52.00");
  reconciler.process(observation(p));
  assertTrue(snapshot().getPayment().isFinancialHalt());assertFalse(new FulfillmentFunding().verified(snapshot()));assertEquals(original,snapshot().getPayment().getCaptureEvidenceId());assertEquals(new BigDecimal("52.00"),snapshot().getPayment().getCapturedAmount());
 }
 @Test void conflictingAuthorizationAliasValuesRemainDurableAndCannotFund() {
  var p=hold("aliases");p.putObject("authorisation").put("action","VOID").put("status","SUCCESS");String id=observation(p);reconciler.process(id);
  assertNotNull(jdbc.queryForObject("SELECT processed_at FROM gateway_event_inbox WHERE id=?",java.sql.Timestamp.class,id));assertTrue(snapshot().getPayment().isFinancialHalt());assertNull(snapshot().getPayment().getCapturedAt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"conflicting-evidence:"+id));
 }
 @Test void unknownFinancialStatusRemainsReviewableAndHaltsUncertainFunding() {
  String id=event("UNSUPPORTED_PROVIDER_STATE","unknown","52.00","INR");assertDoesNotThrow(()->reconciler.process(id));
  assertTrue(snapshot().getPayment().isFinancialHalt());assertNull(snapshot().getPayment().getCapturedAt());
 }
 @Test void changedAuthorizationClockCannotExtendAnExistingHold() {
  var p=hold("immutable-clock");reconciler.process(observation(p));
  ((com.fasterxml.jackson.databind.node.ObjectNode)p.path("authorization")).put("start_time","2026-10-21T10:00:00Z").put("end_time","2026-10-28T10:00:00Z");
  reconciler.process(observation(p));assertTrue(snapshot().getPayment().isFinancialHalt());assertEquals("CONFLICTING_PROVIDER_EVIDENCE",snapshot().getPayment().getFinancialHaltReason());
  assertEquals(Instant.parse("2026-10-20T10:00:00Z"),snapshot().getPayment().getAuthorizedAt());
 }
 @Test void captureWithoutProviderTimeRecordsVerificationWithoutInventingProviderDate() {
  reconciler.process(event("SUCCESS","untimed","52.00","INR"));
  var value=json.valueToTree(com.mercato.Mapper.PaymentMapper.toDTO(snapshot().getPayment()));
  assertTrue(value.path("providerCapturedAt").isNull());
  assertTrue(value.hasNonNull("captureVerifiedAt"));
  assertEquals("OBSERVED_VERIFICATION",value.path("capturedAtProvenance").asText());
  assertFalse(snapshot().getPayment().isFinancialHalt());
 }
 @Test void recheckingLegacyCaptureWithMissingAmountPreservesTimestampAndUsesVerifiedAmount() {
  var legacyTime=Instant.parse("2026-09-20T10:00:00Z");
  tx.executeWithoutResult(t->{var p=orders.lockByOrderId(orderId).orElseThrow().getPayment();p.setCapturedAt(legacyTime);p.setCapturedAmount(null);p.setGatewayReference("legacy-check");p.setFinancialHalt(true);p.setFinancialHaltReason("LEGACY_CAPTURE_REQUIRES_RECONCILIATION");});
  String id=event("SUCCESS","legacy-check","52.00","INR");assertDoesNotThrow(()->reconciler.process(id));
  var p=snapshot().getPayment();assertEquals(legacyTime,p.getCapturedAt());assertEquals(new BigDecimal("52.00"),p.getCapturedAmount());
  assertNotNull(p.getCaptureEvidenceId());assertFalse(p.isFinancialHalt());
 }
 @Test void changedAuthorizationIdentityOnCaptureCannotEvadeValidationWithMissingDates() {
  reconciler.process(observation(hold("identity-change")));
  var p=hold("identity-change").put("is_captured",true).put("auth_id","different-authorization");p.remove("authorization");
  reconciler.process(observation(p));
  assertTrue(snapshot().getPayment().isFinancialHalt());assertNull(snapshot().getPayment().getCapturedAt());
  assertEquals("CONFLICTING_PROVIDER_EVIDENCE",snapshot().getPayment().getFinancialHaltReason());
 }
 @Test void signedWebhookCannotClaimToBeAServerFetchedPoll() throws Exception {
  byte[] raw=("{\"type\":\"PAYMENT_POLL\",\"data\":{\"order\":{\"order_id\":\""+orderId+"\"},\"payment\":{\"cf_payment_id\":\"spoof-source\",\"payment_status\":\"SUCCESS\",\"payment_amount\":52,\"payment_currency\":\"INR\",\"is_captured\":true}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  String timestamp="1720000000123";var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));mac.update(timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  inbox.receive(raw,Base64.getEncoder().encodeToString(mac.doFinal(raw)),timestamp);
  String id=jdbc.queryForObject("SELECT id FROM gateway_event_inbox WHERE order_id=?",String.class,orderId);reconciler.process(id);
  assertNull(snapshot().getPayment().getCapturedAt());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILE_ORDER' AND payload LIKE ?",Integer.class,"%"+orderId+"%"));
 }
 @Test void inboxAndJobRollBackTogether(){long before=jdbc.queryForObject("SELECT count(*) FROM durable_jobs",Long.class);tx.executeWithoutResult(t->{event("SUCCESS","rollback-"+orderId,"52.00","INR");t.setRollbackOnly();});assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM durable_jobs",Long.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM gateway_event_inbox WHERE order_id=?",Integer.class,orderId));}
}
