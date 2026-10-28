package com.mercato.inventory;

import com.mercato.Entity.*;
import com.mercato.Entity.cart.*;
import com.mercato.ExceptionHandler.InsufficientInventoryException;
import com.mercato.Payloads.Request.OrderCaptureRequestDTO;
import com.mercato.Payloads.Response.CashfreeOrderResponse;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transactions exercise expired-hold checkout and the cleanup/renewal lock race. */
@SpringBootTest(classes=PostgresCheckoutReservationTest.Config.class,properties={
    "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false","spring.flyway.enabled=true",
    "mercato.jobs.enabled=false"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class PostgresCheckoutReservationTest {
    private static final String DATABASE=PostgresTestSupport.database();
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Commerce","com.mercato.Jobs"})
    @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Commerce"})
    @Import({OrderServiceImpl.class,CartServiceImpl.class,CartReservationServiceImpl.class,CartPricingServiceImpl.class,PackageDeliveryPricing.class,
        OrderRetryHoldService.class,OrderReservationServiceImpl.class,com.mercato.Commerce.PaidOrderAllocation.class,
        WorkflowCommands.class,FulfillmentCancellation.class,OrderLineUpdateServiceImpl.class,
        com.mercato.Commerce.PaymentSessionRecovery.class,com.mercato.Jobs.DurableJobs.class,com.mercato.Jobs.JobStore.class,com.mercato.Controller.OrderController.class})
    static class Config {
        @Bean FulfillmentWorkflow workflow(){return new FulfillmentWorkflow(java.time.Clock.systemUTC(),new FulfillmentFunding());}
        @Bean AuthUtil auth(){return mock(AuthUtil.class);}
        @Bean com.mercato.Commerce.CashfreeGateway gateway(){return mock(com.mercato.Commerce.CashfreeGateway.class);}
        @Bean com.mercato.Commerce.PaymentInbox inbox(){return mock(com.mercato.Commerce.PaymentInbox.class);}
        @Bean CashfreeService cashfree(com.mercato.Commerce.CashfreeGateway gateway,com.mercato.Commerce.PaymentInbox inbox,OrderRepository orders,AuthUtil auth,com.mercato.Jobs.DurableJobs jobs,PlatformTransactionManager transactions){return spy(new CashfreeServiceImpl(gateway,inbox,orders,auth,jobs,transactions,"http://localhost/return","http://localhost/notify"));}
        @Bean CommissionService commissions(){return mock(CommissionService.class);}
        @Bean RefundService refundService(){return mock(RefundService.class);}
        @Bean FulfillmentService fulfillments(){return mock(FulfillmentService.class);}
        @Bean ShipmentNotificationService notifications(){return mock(ShipmentNotificationService.class);}
        @Bean java.time.Clock clock(){return java.time.Clock.systemUTC();}
        @Bean FulfillmentFunding funding(){return new FulfillmentFunding();}
        @Bean FulfillmentDeadlines deadlines(){return mock(FulfillmentDeadlines.class);}
        @Bean com.mercato.Commerce.RefundObligations refunds(){return mock(com.mercato.Commerce.RefundObligations.class);}
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->DATABASE);p.add("spring.datasource.username",PostgresTestSupport::username);
        p.add("spring.datasource.password",PostgresTestSupport::password);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
    @Autowired UserRepository users; @Autowired CategoryRepository categories; @Autowired ProductRepository products;
    @Autowired CartRepository carts; @Autowired AddressRepository addresses; @Autowired OrderRepository orders;
    @Autowired CartReservationRepository holds; @Autowired CartReservationService reservations;
    @Autowired CartPricingService pricing; @Autowired OrderService orderService; @Autowired AuthUtil auth;
    @Autowired CartService cartService;
    @Autowired CashfreeService cashfree; @Autowired CommissionService commissions;
    @Autowired com.mercato.Commerce.PaidOrderAllocation allocation;
    @Autowired com.mercato.Commerce.PaymentSessionRecovery recovery;
    @Autowired com.mercato.Commerce.CashfreeGateway gateway;
    @Autowired OrderLineUpdateService updates;
    @Autowired com.mercato.Controller.OrderController controller;
    @Autowired PlatformTransactionManager transactions; @Autowired JdbcTemplate jdbc;
    static final java.util.concurrent.atomic.AtomicLong PHONES=new java.util.concurrent.atomic.AtomicLong(9990000000L);
    TransactionTemplate tx; EcommUser buyer; String first,second,addressId; Long cartId; BigDecimal total;

    @BeforeEach void fixture(){
        tx=new TransactionTemplate(transactions);reset(auth,cashfree,commissions,gateway);
        String key=UUID.randomUUID().toString().substring(0,8);first="HOLD-"+key+"-A";second="HOLD-"+key+"-B";
        tx.executeWithoutResult(status->{
            buyer=users.saveAndFlush(EcommUser.builder().username("hold"+key).email("hold"+key+"@mercato.invalid")
                .password("synthetic-not-a-login").phoneNumber(String.valueOf(PHONES.incrementAndGet())).enabled(true).emailVerified(true).build());
            var category=new Category();category.setCategoryName("Hold test "+key);categories.save(category);
            var cart=new Cart();cart.setUser(buyer);carts.saveAndFlush(cart);cartId=cart.getId();
            for(int i=0;i<2;i++){
                var product=products.saveAndFlush(Product.builder().productId(i==0?first:second).productName("Hold test product "+i)
                    .description("Synthetic inventory regression product").primaryImageUrl("/images/test.jpg").category(category)
                    .seller(buyer).retailPrice(new BigDecimal("100.00")).physicalQty(i==0?12:40).build());
                cart.addProduct(product,i==0?1:4);
            }
            carts.saveAndFlush(cart);cart.getCartItems().forEach(reservations::reserve);pricing.applyCharges(cart);total=cart.getTotal();
            var address=new Address();address.setUser(buyer);address.setRecipientName("Test buyer");address.setRecipientPhone("9999999999");
            address.setAddressLine1("Synthetic address");address.setCity("Test city");address.setState("Test state");address.setPincode("123456");address.setCountry("IN");
            addressId=addresses.saveAndFlush(address).getAddressId();
        });
        when(auth.getLoggedInUser()).thenReturn(buyer);when(commissions.currentRate()).thenReturn(new BigDecimal("0.50"));
        doAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new CashfreeOrderResponse("test-cf-id","test-payment-session");
        }).when(cashfree).createOrder(any(),eq(buyer));
        when(gateway.get(anyString())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("order_status","ACTIVE").put("payment_session_id","test-payment-session"));
        doAnswer(call->{com.mercato.Entity.fulfillment.Order order=call.getArgument(0);order.getPayment().setPaymentSessionId("test-payment-session");return null;})
            .when(cashfree).initiatePayment(any(),any(),any());
    }
    void expire(){jdbc.update("UPDATE cart_reservations SET expires_at=? WHERE cart_item_fk IN (SELECT cart_item_id FROM cart_items WHERE cart_fk=?)",java.sql.Timestamp.from(Instant.now().minusSeconds(60)),cartId);}
    OrderCaptureRequestDTO request(){var request=new OrderCaptureRequestDTO();request.setAddressId(addressId);request.setExpectedTotal(total);request.setOperationId(UUID.randomUUID().toString());request.setExpectedCartVersion(0L);return request;}
    int reserved(String id){return jdbc.queryForObject("SELECT reserved_qty FROM products WHERE product_id=?",Integer.class,id);}
    int holdCount(){return jdbc.queryForObject("SELECT count(*) FROM cart_reservations r JOIN cart_items i ON i.cart_item_id=r.cart_item_fk WHERE i.cart_fk=?",Integer.class,cartId);}

    @Test void checkoutReacquiresCleanedUpHoldsWithoutChangingTheBag(){
        expire();reservations.releaseExpired();assertEquals(0,holdCount());assertEquals(0,reserved(first));
        var response=assertDoesNotThrow(()->orderService.placeOrder(request()));assertEquals("test-payment-session",response.getPaymentSessionId());
        assertEquals(2,holdCount());assertEquals(1,reserved(first));assertEquals(4,reserved(second));
        assertEquals(5,jdbc.queryForObject("SELECT sum(quantity) FROM cart_items WHERE cart_fk=?",Integer.class,cartId));
    }
    @Test void checkoutRenewsExpiredHoldsWithoutCountingStockTwice(){
        expire();assertDoesNotThrow(()->orderService.placeOrder(request()));assertEquals(2,holdCount());assertEquals(1,reserved(first));assertEquals(4,reserved(second));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM cart_reservations r JOIN cart_items i ON i.cart_item_id=r.cart_item_fk WHERE i.cart_fk=? AND r.expires_at>CURRENT_TIMESTAMP",Integer.class,cartId));
    }
    @Test void laterItemShortageRollsBackAllReacquisitionsBeforeCreatingAnOrder(){
        expire();reservations.releaseExpired();jdbc.update("UPDATE products SET physical_qty=3 WHERE product_id=?",second);long before=orders.count();
        var failure=assertThrows(InsufficientInventoryException.class,()->orderService.placeOrder(request()));
        assertEquals(second,failure.getProductId());assertEquals(3,failure.getAvailableQty());assertEquals(before,orders.count());
        assertEquals(0,holdCount());assertEquals(0,reserved(first));assertEquals(0,reserved(second));verifyNoInteractions(cashfree);
    }
    @Test void uncertainCheckoutRetryKeepsTheOriginalMerchantIdentity() {
        var request=request();var identities=new ArrayList<String>();
        doAnswer(call->{
            com.mercato.Entity.fulfillment.Order order=call.getArgument(0);identities.add(order.getOrderId());
            if(identities.size()==1)throw new IllegalStateException("Synthetic uncertain transport outcome");
            return new CashfreeOrderResponse("same-cf-id","recovered-session");
        }).when(cashfree).createOrder(any(),eq(buyer));
        long before=orders.count();
        assertThrows(IllegalStateException.class,()->orderService.placeOrder(request));
        var response=orderService.placeOrder(request);
        assertEquals(1,new HashSet<>(identities).size(),"A transport retry must reuse its durable checkout intent");
        assertEquals(before+1,orders.count());assertEquals(identities.get(0),response.getOrder().getOrderId());
    }
    @Test void changedIntentBodyAndDistinctStaleQuoteCannotCreateAnotherOrder() {
        var request=request();long before=orders.count();orderService.placeOrder(request);
        request.setExpectedTotal(total.add(new BigDecimal("0.01")));
        var mismatch=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->orderService.placeOrder(request));
        assertEquals(409,mismatch.getStatusCode().value());
        var stale=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->orderService.placeOrder(request()));
        assertEquals(409,stale.getStatusCode().value());assertEquals(before+1,orders.count());
    }
    @Test void cartEditsCannotBeLostWhenPurchasedQuantitiesAreRemoved() throws Exception {
        var placed=orderService.placeOrder(request());var read=new CountDownLatch(1);var release=new CountDownLatch(1);
        var owner=new java.util.concurrent.atomic.AtomicInteger();var pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> allocation=pool.submit(()->tx.executeWithoutResult(status->{
                var order=orders.lockByOrderId(placed.getOrder().getOrderId()).orElseThrow();
                var cart=cartService.getCartByUser(buyer);owner.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));read.countDown();
                await(release);cartService.removePurchasedItems(cart,order);
            }));
            assertTrue(read.await(10,TimeUnit.SECONDS));
            Future<?> edit=pool.submit(()->cartService.addProductToCart(add(first,2),new com.mercato.Utils.CartContext(buyer.getUserId(),null)));
            waitUntilBlockedOrDone(edit,owner.get());release.countDown();allocation.get(10,TimeUnit.SECONDS);edit.get(10,TimeUnit.SECONDS);
            assertEquals(2,jdbc.queryForObject("SELECT quantity FROM cart_items i JOIN products p ON p.id=i.product_fk WHERE cart_fk=? AND p.product_id=?",Integer.class,cartId,first));
            assertEquals(2,reserved(first));assertEquals(0,reserved(second));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void newlyReaddedSelectionIsNotConsumedByAnEarlierCheckout() {
        var placed=orderService.placeOrder(request());var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);
        cartService.deleteProductFromCart(first,context);cartService.addProductToCart(add(first,3),context);
        tx.executeWithoutResult(status->{var order=orders.lockByOrderId(placed.getOrder().getOrderId()).orElseThrow();cartService.removePurchasedItems(cartService.getCartByUser(buyer),order);});
        assertEquals(3,jdbc.queryForObject("SELECT quantity FROM cart_items i JOIN products p ON p.id=i.product_fk WHERE cart_fk=? AND p.product_id=?",Integer.class,cartId,first));
    }
    @Test void retryRestoredMissingSelectionTransfersItsHoldThroughPaidAllocationWithExactStock() {
        retryRestoredSelectionAllocates(false);
    }
    @Test void retryRestoredReducedSelectionTransfersItsHoldThroughPaidAllocationWithExactStock() {
        retryRestoredSelectionAllocates(true);
    }
    void retryRestoredSelectionAllocates(boolean reduce) {
        String orderId=twoUnitCheckout();var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);
        var original=jdbc.queryForMap("SELECT source_cart_item_id,source_selection_generation,unit_price FROM order_lines_snapshot WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",orderId);
        if(reduce){var update=new com.mercato.Payloads.Request.CartItemRequestDTO();update.setProductId(first);update.setQuantity(1);cartService.updateProductQuantityInCart(update,context);}
        else cartService.deleteProductFromCart(first,context);
        orderService.retryPayment(orderId);
        assertEquals(2,reserved(first));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_lines_snapshot l JOIN cart_items i ON i.cart_item_id=l.retry_cart_item_id AND i.selection_generation=l.retry_selection_generation WHERE l.order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,orderId));
        tx.executeWithoutResult(status->{var order=orders.lockByOrderId(orderId).orElseThrow();var payment=order.getPayment();payment.setCapturedAmount(order.getTotalAmount());payment.setCapturedAt(Instant.now());payment.setStatus(com.mercato.Entity.fulfillment.payment.PaymentStatus.SUCCESS);});
        assertDoesNotThrow(()->allocation.allocate(orderId));allocation.allocate(orderId);
        assertEquals(2,reserved(first));assertEquals(0,holdCount());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_fk=?",Integer.class,cartId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_reservations WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,orderId));
        assertEquals(2,jdbc.queryForObject("SELECT sum(reserved_qty) FROM order_reservations WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,orderId));
        assertNotNull(jdbc.queryForObject("SELECT allocation_completed_at FROM order_snapshot WHERE order_id=?",java.sql.Timestamp.class,orderId));
        assertEquals(original,jdbc.queryForMap("SELECT source_cart_item_id,source_selection_generation,unit_price FROM order_lines_snapshot WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",orderId));
    }
    @Test void unrelatedAdequateNewSelectionCannotReachPaymentWhenItsHoldUsesAllStock() {
        String orderId=twoUnitCheckout();var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);
        cartService.deleteProductFromCart(first,context);cartService.addProductToCart(add(first,2),context);
        clearInvocations(cashfree);
        var failure=assertThrows(InsufficientInventoryException.class,()->orderService.retryPayment(orderId));
        assertEquals(first,failure.getProductId());assertEquals(0,failure.getAvailableQty());verifyNoInteractions(cashfree);
        assertEquals(2,reserved(first));assertEquals(1,holdCount());
        assertEquals(2,jdbc.queryForObject("SELECT sum(quantity) FROM cart_items WHERE cart_fk=?",Integer.class,cartId));
    }
    String twoUnitCheckout() {
        return orderService.placeOrder(twoUnitRequest()).getOrder().getOrderId();
    }
    OrderCaptureRequestDTO twoUnitRequest() {
        var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);cartService.deleteProductFromCart(second,context);
        var update=new com.mercato.Payloads.Request.CartItemRequestDTO();update.setProductId(first);update.setQuantity(2);cartService.updateProductQuantityInCart(update,context);
        jdbc.update("UPDATE products SET physical_qty=2 WHERE product_id=?",first);
        var quote=cartService.getCart(context);var request=request();request.setExpectedCartVersion(quote.getVersion());request.setExpectedTotal(quote.getTotal());
        return request;
    }
    @Test void exactUncertainReplayRestoresMissingSelectionBeforePaymentAtExactStock(){exactReplayRestores(false);}
    @Test void exactUncertainReplayRestoresReducedSelectionBeforePaymentAtExactStock(){exactReplayRestores(true);}
    void exactReplayRestores(boolean reduce){
        var request=twoUnitRequest();
        doThrow(new IllegalStateException("Synthetic uncertain create")).when(cashfree).createOrder(any(),eq(buyer));
        assertThrows(IllegalStateException.class,()->orderService.placeOrder(request));
        String id=jdbc.queryForObject("SELECT order_id FROM order_snapshot WHERE checkout_operation_id=?",String.class,request.getOperationId());
        editSelection(reduce);doReturn(new CashfreeOrderResponse("recovered-cf","recovered-session")).when(cashfree).createOrder(any(),eq(buyer));
        orderService.placeOrder(request);assertRetryAllocation(id);
    }
    void editSelection(boolean reduce){
        var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);
        if(reduce){var update=new com.mercato.Payloads.Request.CartItemRequestDTO();update.setProductId(first);update.setQuantity(1);cartService.updateProductQuantityInCart(update,context);}
        else cartService.deleteProductFromCart(first,context);
    }
    void assertRetryAllocation(String id){
        assertEquals(2,reserved(first));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_lines_snapshot l JOIN cart_items i ON i.cart_item_id=l.retry_cart_item_id AND i.selection_generation=l.retry_selection_generation WHERE l.order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,id));
        tx.executeWithoutResult(t->{var o=orders.lockByOrderId(id).orElseThrow();o.getPayment().setCapturedAt(Instant.now());o.getPayment().setCapturedAmount(o.getTotalAmount());});
        allocation.allocate(id);allocation.allocate(id);
        assertEquals(2,reserved(first));assertEquals(0,holdCount());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_reservations WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,id));
    }
    @Test void exactReplayRejectsUnrelatedAdequateHoldBeforeGatewayExposure(){
        var request=twoUnitRequest();orderService.placeOrder(request);editSelection(false);
        cartService.addProductToCart(add(first,2),new com.mercato.Utils.CartContext(buyer.getUserId(),null));clearInvocations(cashfree,gateway);
        assertThrows(InsufficientInventoryException.class,()->orderService.placeOrder(request));verifyNoInteractions(cashfree,gateway);
        assertEquals(2,reserved(first));assertEquals(2,jdbc.queryForObject("SELECT sum(quantity) FROM cart_items WHERE cart_fk=?",Integer.class,cartId));
    }
    @Test void recoveredReadyIntentAndByOrderLookupStayReadOnlyThenPrepareThroughRetry()throws Exception{
        var request=twoUnitRequest();String id=orderService.placeOrder(request).getOrder().getOrderId();editSelection(false);
        long version=jdbc.queryForObject("SELECT version FROM carts WHERE id=?",Long.class,cartId);clearInvocations(gateway);
        var intent=(Map<?,?>)recovery.recoverIntent(request.getOperationId());assertEquals("PAYMENT_READY",intent.get("state"));assertNull(intent.get("paymentSessionId"));
        var lookup=(Map<?,?>)recovery.recover(id);assertEquals(false,lookup.get("canResume"));assertEquals(true,lookup.get("canPrepare"));assertNull(lookup.get("paymentSessionId"));
        assertEquals(version,jdbc.queryForObject("SELECT version FROM carts WHERE id=?",Long.class,cartId));assertEquals(0,holdCount());verifyNoInteractions(gateway);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/user/orders/"+id+"/payment-session"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentSessionId").value("test-payment-session"));
        assertRetryAllocation(id);
    }
    @Test void recoveredSessionPostRejectsUnrelatedAdequateHoldWithoutExposingPayment()throws Exception{
        var request=twoUnitRequest();String id=orderService.placeOrder(request).getOrder().getOrderId();editSelection(false);
        cartService.addProductToCart(add(first,2),new com.mercato.Utils.CartContext(buyer.getUserId(),null));clearInvocations(cashfree,gateway);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new com.mercato.ExceptionHandler.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/user/orders/"+id+"/payment-session"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.availableQty").value(0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentSessionId").doesNotExist());
        assertEquals(2,reserved(first));assertEquals(2,jdbc.queryForObject("SELECT sum(quantity) FROM cart_items WHERE cart_fk=?",Integer.class,cartId));verifyNoInteractions(cashfree,gateway);
    }
    @Test void partialUnpaidCancellationClosesOriginalCheckoutAndCommitsTerminationOnce(){cancelledCheckout(false,false);}
    @Test void wholeUnpaidCancellationClosesOriginalCheckoutAndCommitsTerminationOnce(){cancelledCheckout(true,false);}
    @Test void uncertainUnpaidCancellationCannotCreateGatewayOnExactReplay(){cancelledCheckout(true,true);}
    void cancelledCheckout(boolean whole,boolean uncertain){
        var request=twoUnitRequest();
        if(uncertain)doThrow(new IllegalStateException("Synthetic uncertain create")).when(cashfree).createOrder(any(),eq(buyer));
        if(uncertain)assertThrows(IllegalStateException.class,()->orderService.placeOrder(request));else orderService.placeOrder(request);
        String id=jdbc.queryForObject("SELECT order_id FROM order_snapshot WHERE checkout_operation_id=?",String.class,request.getOperationId());
        if(whole){var cancel=new com.mercato.Payloads.Request.OrderCancelRequestDTO();cancel.setOrderId(id);cancel.setReason("Changed decision");cancel.setExpectedVersion(0L);cancel.setOperationId("whole-cancel");orderService.cancelOrder(cancel,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);orderService.cancelOrder(cancel,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);}
        else {var cancel=lineCancel(id,1);updates.updateOrderLine(cancel,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);updates.updateOrderLine(cancel,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);}
        assertTermination(id,1);assertClosedRecovery(request,id);clearInvocations(cashfree,gateway);
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.placeOrder(request));
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.retryPayment(id));verifyNoInteractions(cashfree,gateway);assertTermination(id,1);
        assertEquals(whole?2:1,jdbc.queryForObject("SELECT cancelled_qty FROM order_lines_snapshot WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",Integer.class,id));
    }
    com.mercato.Payloads.Request.OrderLineUpdateRequestDTO lineCancel(String id,int qty){
        var r=new com.mercato.Payloads.Request.OrderLineUpdateRequestDTO();r.setFulfillmentId(jdbc.queryForObject("SELECT fulfillment_id FROM order_lines_snapshot WHERE order_fk=(SELECT id FROM order_snapshot WHERE order_id=?)",String.class,id));r.setOrderLineNumber(1);r.setAction(com.mercato.Entity.fulfillment.OrderLineAction.CANCEL);r.setQty(qty);r.setReason("Changed decision");r.setExpectedVersion(0L);r.setOperationId("cancel-quantity");return r;
    }
    void assertTermination(String id,int count){for(String key:List.of("terminate:","reconcile-cancel:"))assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,key+id));}
    void assertClosedRecovery(OrderCaptureRequestDTO request,String id){
        var intent=(Map<?,?>)recovery.recoverIntent(request.getOperationId());assertEquals("ORDER_UPDATED",intent.get("state"));assertNull(intent.get("paymentSessionId"));
        var byOrder=(Map<?,?>)recovery.recover(id);assertEquals(false,byOrder.get("canResume"));assertEquals(false,byOrder.get("canPrepare"));assertNull(byOrder.get("paymentSessionId"));
    }
    @Test void currentAuthorizationAndFinancialHaltFenceEveryRecoveryAndReplay(){
        for(String evidence:List.of("authorization","halt","capture")){
            if(!evidence.equals("authorization"))fixture();var request=twoUnitRequest();String id=orderService.placeOrder(request).getOrder().getOrderId();
            tx.executeWithoutResult(t->{var p=orders.lockByOrderId(id).orElseThrow().getPayment();if(evidence.equals("authorization"))p.setAuthorizedAt(Instant.now());else if(evidence.equals("halt"))p.setFinancialHalt(true);else{p.setCapturedAt(Instant.now());p.setCapturedAmount(new BigDecimal("200.00"));}});
            assertClosedRecovery(request,id);clearInvocations(cashfree,gateway);
            assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.placeOrder(request));
            assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.retryPayment(id));verifyNoInteractions(cashfree,gateway);
        }
    }
    @Test void cancellationDuringCreateCommitsPostNetworkTerminationWithoutExposingSession()throws Exception{cancelDuringNetwork(false,false,false);}
    @Test void cancellationDuringResumeCommitsTerminationWithoutExposingSession()throws Exception{cancelDuringNetwork(true,false,false);}
    @Test void cancellationDuringUncertainCreateCommitsPostNetworkTermination()throws Exception{cancelDuringNetwork(false,true,false);}
    @Test void cancellationDuringUncertainResumeCommitsPostNetworkTermination()throws Exception{cancelDuringNetwork(true,true,false);}
    @Test void cancellationDuringMissingProviderRecoveryCommitsPostNetworkTermination()throws Exception{cancelDuringNetwork(true,false,true);}
    void cancelDuringNetwork(boolean resume,boolean uncertain,boolean missing)throws Exception{
        var request=twoUnitRequest();String existing=resume?orderService.placeOrder(request).getOrder().getOrderId():null;
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        if(resume&&!missing)when(gateway.get(anyString())).thenAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();await(release);if(uncertain)throw new IllegalStateException("Synthetic uncertain transport");return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("order_status","ACTIVE").put("payment_session_id","late-session");});
        else {
            if(missing)when(gateway.get(anyString())).thenThrow(new com.mercato.Commerce.CashfreeGateway.GatewayFailure(404,"order_not_found"));
            doAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();await(release);if(uncertain)throw new IllegalStateException("Synthetic uncertain transport");return new CashfreeOrderResponse("late-cf","late-session");}).when(cashfree).createOrder(any(),eq(buyer));
        }
        try{
            var pending=pool.submit(()->resume?orderService.retryPayment(existing):orderService.placeOrder(request).getPaymentSessionId());assertTrue(entered.await(10,TimeUnit.SECONDS));
            String id=resume?existing:jdbc.queryForObject("SELECT order_id FROM order_snapshot WHERE checkout_operation_id=?",String.class,request.getOperationId());
            updates.updateOrderLine(lineCancel(id,1),com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);
            // Simulate the worker having observed a missing provider order before create completed.
            jdbc.update("UPDATE durable_jobs SET status='SUCCEEDED' WHERE business_key IN (?,?)","terminate:"+id,"reconcile-cancel:"+id);
            release.countDown();var failure=assertThrows(ExecutionException.class,()->pending.get(10,TimeUnit.SECONDS));
            if(uncertain)assertInstanceOf(IllegalStateException.class,failure.getCause());else assertInstanceOf(com.mercato.ExceptionHandler.CustomBadRequestException.class,failure.getCause());
            assertClosedRecovery(request,id);assertTermination(id,1);
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"terminate-after-payment-io:"+id));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE business_key=?",Integer.class,"reconcile-after-payment-io:"+id));
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void replayAfterBagEditsReturnsTheOriginalSnapshotAndSession() {
        var request=request();var original=orderService.placeOrder(request);
        cartService.addProductToCart(add(first,2),new com.mercato.Utils.CartContext(buyer.getUserId(),null));
        request.setExpectedTotal(new BigDecimal(request.getExpectedTotal().stripTrailingZeros().toPlainString()));
        var replay=orderService.placeOrder(request);
        assertEquals(original.getOrder().getOrderId(),replay.getOrder().getOrderId());assertEquals(original.getPaymentSessionId(),replay.getPaymentSessionId());
        assertEquals(3,jdbc.queryForObject("SELECT quantity FROM cart_items i JOIN products p ON p.id=i.product_fk WHERE cart_fk=? AND p.product_id=?",Integer.class,cartId,first));
        verify(cashfree,times(1)).createOrder(any(),eq(buyer));
        assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM order_money_units u JOIN order_lines_snapshot l ON l.id=u.order_line_fk JOIN order_snapshot o ON o.id=l.order_fk WHERE o.order_id=?",Integer.class,original.getOrder().getOrderId()));
    }
    @Test void operationNamespaceIsScopedToTheAuthenticatedBuyer() {
        var firstRequest=request();var original=orderService.placeOrder(firstRequest);
        fixture();var otherRequest=request();otherRequest.setOperationId(firstRequest.getOperationId());
        var other=orderService.placeOrder(otherRequest);
        assertNotEquals(original.getOrder().getOrderId(),other.getOrder().getOrderId());
        assertEquals(buyer.getEmail(),other.getOrder().getCustomer().getEmail());
    }
    @Test void aFreshQuoteAndDifferentIntentCanLegitimatelyCreateAnotherCheckout() {
        var original=orderService.placeOrder(request());
        var quote=cartService.getCart(new com.mercato.Utils.CartContext(buyer.getUserId(),null));
        var fresh=request();fresh.setExpectedCartVersion(quote.getVersion());fresh.setExpectedTotal(quote.getTotal());
        var other=orderService.placeOrder(fresh);
        assertNotEquals(original.getOrder().getOrderId(),other.getOrder().getOrderId());
    }
    @Test void missingIntentIdentityOrVersionFailsBeforeQuoteAndReservationEffects() {
        long before=orders.count();var missingOperation=request();missingOperation.setOperationId(null);
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.placeOrder(missingOperation));
        var missingVersion=request();missingVersion.setExpectedCartVersion(null);
        assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.placeOrder(missingVersion));
        assertEquals(before,orders.count());assertEquals(0L,jdbc.queryForObject("SELECT version FROM carts WHERE id=?",Long.class,cartId));
        assertEquals(1,reserved(first));assertEquals(4,reserved(second));verifyNoInteractions(cashfree);
    }
    @Test void simultaneousIntentReplaysNeverHoldDatabaseLocksOverGatewayIo() throws Exception {
        var request=request();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        var identities=new java.util.concurrent.ConcurrentLinkedQueue<String>();
        doAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            com.mercato.Entity.fulfillment.Order order=call.getArgument(0);identities.add(order.getOrderId());
            if(calls.incrementAndGet()==1){entered.countDown();await(release);}
            return new CashfreeOrderResponse("same-provider-order","same-session");
        }).when(cashfree).createOrder(any(),eq(buyer));
        var pool=Executors.newFixedThreadPool(2);long before=orders.count();
        try {
            var a=pool.submit(()->orderService.placeOrder(request));assertTrue(entered.await(10,TimeUnit.SECONDS));
            var b=pool.submit(()->orderService.placeOrder(request));var second=b.get(10,TimeUnit.SECONDS);release.countDown();
            assertEquals(second.getOrder().getOrderId(),a.get(10,TimeUnit.SECONDS).getOrder().getOrderId());
            assertEquals(1,new HashSet<>(identities).size());assertEquals(before+1,orders.count());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void reducedAndThenIncreasedSelectionDoesNotLoseItsNewQuantities() {
        var placed=orderService.placeOrder(request());var context=new com.mercato.Utils.CartContext(buyer.getUserId(),null);
        var update=new com.mercato.Payloads.Request.CartItemRequestDTO();update.setProductId(second);update.setQuantity(2);
        cartService.updateProductQuantityInCart(update,context);cartService.addProductToCart(add(second,3),context);
        tx.executeWithoutResult(status->{var order=orders.lockByOrderId(placed.getOrder().getOrderId()).orElseThrow();cartService.removePurchasedItems(cartService.getCartForOrderAllocation(buyer,order),order);});
        assertEquals(5,jdbc.queryForObject("SELECT quantity FROM cart_items i JOIN products p ON p.id=i.product_fk WHERE cart_fk=? AND p.product_id=?",Integer.class,cartId,second));
    }
    @Test void oversizedCheckoutFailsBeforeRenewalOrNewIntentEffects() {
        jdbc.update("UPDATE cart_items SET quantity=2147483647 WHERE cart_fk=?",cartId);
        var quote=cartService.getCart(new com.mercato.Utils.CartContext(buyer.getUserId(),null));var request=request();request.setExpectedCartVersion(quote.getVersion());request.setExpectedTotal(quote.getTotal());
        long before=orders.count();int held=holdCount();
        var failure=assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->orderService.placeOrder(request));
        assertTrue(failure.getMessage().contains("10000"));assertEquals(before,orders.count());assertEquals(held,holdCount());
        assertEquals(1,reserved(first));assertEquals(4,reserved(second));verifyNoInteractions(cashfree);
        assertEquals(4294967294L,jdbc.queryForObject("SELECT sum(quantity::bigint) FROM cart_items WHERE cart_fk=?",Long.class,cartId));
    }
    @Test void guestOnlyMergeKeepsTheChosenPriceAndDiscountAfterListingChanges() {
        String token="guest-"+UUID.randomUUID();String guestProduct="G-"+UUID.randomUUID().toString().substring(0,8);
        tx.executeWithoutResult(status->{
            var category=categories.findAll().get(0);
            var p=products.saveAndFlush(Product.builder().productId(guestProduct).productName("Guest selection").description("Synthetic guest selection").primaryImageUrl("/images/test.jpg").category(category).seller(buyer).retailPrice(new BigDecimal("100.00")).discountPercent(new BigDecimal("25.00")).physicalQty(20).build());
            var guest=new Cart();guest.setGuestToken(token);carts.saveAndFlush(guest);guest.addProduct(p,2);carts.saveAndFlush(guest);guest.getCartItems().forEach(reservations::reserve);
            p.setRetailPrice(new BigDecimal("200.00"));p.setDiscountPercent(BigDecimal.ZERO);products.saveAndFlush(p);
        });
        cartService.mergeGuestCartOnLogin(buyer.getUserId(),token);
        tx.executeWithoutResult(status->{var item=carts.findById(cartId).orElseThrow().findItemByProductId(guestProduct).orElseThrow();
            assertEquals(new BigDecimal("75.00"),item.getItemPrice());assertEquals(new BigDecimal("100.00"),item.getSelectedRetailPrice());assertEquals(new BigDecimal("25.00"),item.getSelectedDiscountPercent());assertEquals(2,item.getQuantity());});
    }
    @Test void mergeTakesBothCartMutexesBeforeTakingAnyProductLocks() throws Exception {
        String token="guest-"+UUID.randomUUID();Long guestId=tx.execute(status->{var guest=new Cart();guest.setGuestToken(token);carts.saveAndFlush(guest);guest.addProduct(products.findByProductId(first).orElseThrow(),2);carts.saveAndFlush(guest);guest.getCartItems().forEach(reservations::reserve);return guest.getId();});
        assertTrue(guestId>cartId);var pool=Executors.newSingleThreadExecutor();var merge=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        try {
            tx.executeWithoutResult(status->{carts.lockById(guestId).orElseThrow();int owner=jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);
                merge.set(pool.submit(()->cartService.mergeGuestCartOnLogin(buyer.getUserId(),token)));
                try{waitUntilBlockedOrDone(merge.get(),owner);}catch(Exception e){throw new IllegalStateException(e);}
                jdbc.execute("SET LOCAL lock_timeout='1s'");assertDoesNotThrow(()->products.findByProductIdForUpdate(first).orElseThrow());
            });
            merge.get().get(10,TimeUnit.SECONDS);
            assertEquals(2,jdbc.queryForObject("SELECT quantity FROM cart_items i JOIN products p ON p.id=i.product_fk WHERE cart_fk=? AND p.product_id=?",Integer.class,cartId,first));
        }finally{pool.shutdownNow();}
    }
    com.mercato.Payloads.Request.CartRequestDTO add(String id,int quantity) {
        var item=new com.mercato.Payloads.Request.CartItemRequestDTO();item.setProductId(id);item.setQuantity(quantity);
        var request=new com.mercato.Payloads.Request.CartRequestDTO();request.setCartItems(List.of(item));return request;
    }
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic latch timed out");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}}
    void waitUntilBlockedOrDone(Future<?> request,int owner)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!request.isDone() && System.nanoTime()<deadline) {
            if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))",Integer.class,owner)>0)return;
            Thread.sleep(20);
        }
        if(!request.isDone())fail("Request neither completed nor reached the database mutex");
    }
    @Test void cleanupCannotDeleteAHoldRenewedWhileItWaitedForTheProductLock()throws Exception{
        // One product keeps this race independent of multi-product lock ordering.
        tx.executeWithoutResult(status->{var cart=carts.findById(cartId).orElseThrow();var item=cart.findItemByProductId(second).orElseThrow();reservations.release(item);cart.removeCartItem(item);carts.saveAndFlush(cart);});
        expire();var pool=Executors.newSingleThreadExecutor();var cleanup=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        try{
            tx.executeWithoutResult(status->{
                products.findByProductIdForUpdate(first).orElseThrow();int owner=jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);
                cleanup.set(pool.submit(()->reservations.releaseExpired()));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
                while(System.nanoTime()<deadline){
                    if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))",Integer.class,owner)>0){waiting=true;break;}
                    try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                }
                assertTrue(waiting,"Cleanup must be waiting after selecting the expired hold");
                reservations.reserve(carts.findById(cartId).orElseThrow().findItemByProductId(first).orElseThrow());
            });
            cleanup.get().get(10,TimeUnit.SECONDS);assertEquals(1,holdCount());assertEquals(1,reserved(first));
        }finally{pool.shutdownNow();}
    }
}
