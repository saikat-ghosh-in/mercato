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
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false","spring.flyway.enabled=false",
    "mercato.jobs.enabled=false"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class PostgresCheckoutReservationTest {
    private static final String DATABASE=PostgresTestSupport.database();
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Commerce","com.mercato.Jobs"})
    @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Commerce"})
    @Import({OrderServiceImpl.class,CartServiceImpl.class,CartReservationServiceImpl.class,CartPricingServiceImpl.class,PackageDeliveryPricing.class})
    static class Config {
        @Bean WorkflowCommands workflowCommands(){return mock(WorkflowCommands.class);}
        @Bean FulfillmentCancellation cancellation(){return mock(FulfillmentCancellation.class);}
        @Bean FulfillmentWorkflow workflow(){return new FulfillmentWorkflow(java.time.Clock.systemUTC(),new FulfillmentFunding());}
        @Bean AuthUtil auth(){return mock(AuthUtil.class);}
        @Bean CashfreeService cashfree(){return mock(CashfreeService.class);}
        @Bean CommissionService commissions(){return mock(CommissionService.class);}
        @Bean OrderLineUpdateService updates(){return mock(OrderLineUpdateService.class);}
        @Bean OrderRetryHoldService retryHolds(){return mock(OrderRetryHoldService.class);}
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
    @Autowired CashfreeService cashfree; @Autowired CommissionService commissions;
    @Autowired PlatformTransactionManager transactions; @Autowired JdbcTemplate jdbc;
    static final java.util.concurrent.atomic.AtomicLong PHONES=new java.util.concurrent.atomic.AtomicLong(9990000000L);
    TransactionTemplate tx; EcommUser buyer; String first,second,addressId; Long cartId; BigDecimal total;

    @BeforeEach void fixture(){
        tx=new TransactionTemplate(transactions);reset(auth,cashfree,commissions);
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
        when(cashfree.createOrder(any(),eq(buyer))).thenAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new CashfreeOrderResponse("test-cf-id","test-payment-session");
        });
        doAnswer(call->{com.mercato.Entity.fulfillment.Order order=call.getArgument(0);order.getPayment().setPaymentSessionId("test-payment-session");return null;})
            .when(cashfree).initiatePayment(any(),any(),any());
    }
    void expire(){jdbc.update("UPDATE cart_reservations SET expires_at=? WHERE cart_item_fk IN (SELECT cart_item_id FROM cart_items WHERE cart_fk=?)",java.sql.Timestamp.from(Instant.now().minusSeconds(60)),cartId);}
    OrderCaptureRequestDTO request(){var request=new OrderCaptureRequestDTO();request.setAddressId(addressId);request.setExpectedTotal(total);return request;}
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
