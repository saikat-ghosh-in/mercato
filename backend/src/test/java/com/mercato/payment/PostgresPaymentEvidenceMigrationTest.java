package com.mercato.payment;

import com.mercato.jobs.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PostgresPaymentEvidenceMigrationTest {
    @AfterAll static void cleanup() { PostgresTestSupport.cleanup(); }
    @Test void unknownHistoricalCaptureKeepsAmountsAndPolicyProvenanceButRequiresReconciliation() throws Exception {
        String url=PostgresTestSupport.database();
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password())
            .locations("classpath:db/migration").target("9").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var s=c.createStatement()) {
            s.execute("INSERT INTO payments(id,payment_id,amount,currency,payment_method,status,captured_at,captured_amount,gateway_reference) VALUES(1,'MIG-PAY',52,'INR','CARD','SUCCESS','2026-10-20T10:00:00Z',52,'MIG-REFERENCE')");
            s.execute("INSERT INTO payments(id,payment_id,amount,currency,payment_method,status) VALUES(2,'MIG-HOLD',52,'INR','CARD','AUTHORIZED')");
            s.execute("INSERT INTO payments(id,payment_id,amount,currency,payment_method,status) VALUES(3,'MIG-UNKNOWN-SUCCESS',52,'INR','CARD','SUCCESS')");
            s.execute("INSERT INTO order_snapshot(id,order_id,payment_fk,charges,currency,subtotal,total_amount,recipient_phone,customer_email,customer_name,delivery_address_line1,delivery_city,delivery_pincode,delivery_state,order_status,payment_status,recipient_name) VALUES(1,'MIG-ORDER',1,12,'INR',40,52,'9999999999','synthetic@test.invalid','Synthetic','Synthetic address','City','123456','State','CREATED','SUCCESS','Synthetic')");
        }
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password())
            .locations("classpath:db/migration").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT captured_amount,payment_policy,authorization_id,financial_halt,financial_halt_reason FROM payments WHERE id=1")) {
                assertTrue(r.next());assertEquals(new BigDecimal("52.00"),r.getBigDecimal(1));
                assertNull(r.getString(2));assertNull(r.getString(3));assertTrue(r.getBoolean(4));
                assertEquals("LEGACY_CAPTURE_REQUIRES_RECONCILIATION",r.getString(5));
            }
            try(var r=s.executeQuery("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILE_ORDER' AND business_key='reconcile-legacy-payment-evidence:MIG-PAY'")) {
                assertTrue(r.next());assertEquals(1,r.getInt(1));
            }
            try(var r=s.executeQuery("SELECT payment_policy,financial_halt FROM payments WHERE id=2")) {
                assertTrue(r.next());assertNull(r.getString(1));assertTrue(r.getBoolean(2),"Legacy authorization remains halted until explicit drain/reconciliation");
            }
            try(var r=s.executeQuery("SELECT financial_halt FROM payments WHERE id=3")) {
                assertTrue(r.next());assertTrue(r.getBoolean(1),"Historical SUCCESS without capture facts remains reviewable");
            }
        }
    }
}
