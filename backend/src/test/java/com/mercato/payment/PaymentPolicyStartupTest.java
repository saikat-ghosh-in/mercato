package com.mercato.payment;

import com.mercato.Commerce.*;
import com.mercato.Service.CashfreeServiceImpl;
import com.mercato.Entity.fulfillment.payment.Payment;
import com.mercato.Repository.OrderRepository;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PaymentPolicyStartupTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CashfreeServiceImpl.class)
        .withBean(CashfreeGateway.class,()->mock(CashfreeGateway.class))
        .withBean(PaymentInbox.class,()->mock(PaymentInbox.class))
        .withBean(OrderRepository.class,()->mock(OrderRepository.class))
        .withBean(AuthUtil.class,()->mock(AuthUtil.class))
        .withBean(DurableJobs.class,()->mock(DurableJobs.class))
        .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class))
        .withPropertyValues("cashfree.return.url=https://example.invalid/return","cashfree.notify.url=https://example.invalid/webhook");
    @Test void defaultStartupAndNewPaymentUseCaptureOnAuthWithoutActivatingHolds() {
        runner.run(context->assertNull(context.getStartupFailure()));
        assertEquals(PaymentPolicy.CAPTURE_ON_AUTH,new Payment().getPaymentPolicy());
    }
    @Test void delayedModeCannotBeEnabledBeforeAccountProofAndFinancialIntegration() {
        runner.withPropertyValues("mercato.payments.default-policy=DELAYED_CAPTURE")
            .run(context->assertNotNull(context.getStartupFailure()));
    }
    @Test void authorizationActivationCannotBeRequestedThroughAnEnvironmentFlag() {
        runner.withPropertyValues("mercato.payments.authorization-enabled=true")
            .run(context->assertNotNull(context.getStartupFailure()));
    }
}
