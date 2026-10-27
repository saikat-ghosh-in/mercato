package com.mercato.payment;

import com.mercato.jobs.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresMoneyUnitMigrationTest {
    @AfterAll static void cleanup(){PostgresTestSupport.cleanup();}
    @Test void legacyUnknownsStayUnknownAndUnitMoneyCannotBeRewritten()throws Exception {
        var url=PostgresTestSupport.database();
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").target("10").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var s=c.createStatement()) {
            s.execute("INSERT INTO order_snapshot(id,order_id,charges,currency,subtotal,total_amount,recipient_phone,customer_email,customer_name,delivery_address_line1,delivery_city,delivery_pincode,delivery_state,order_status,payment_status,recipient_name) VALUES(1,'LEGACY-MONEY',12,'INR',10,22,'9999999999','old@test.invalid','Old','Old address','City','123456','State','CREATED','PENDING','Old')");
            s.execute("INSERT INTO order_lines_snapshot(id,order_fk,order_line_number,fulfillment_id,product_id,product_name,unit_price,ordered_qty,accepted_qty,shipped_qty,cancelled_qty,revenue,line_total,order_line_status) VALUES(1,1,1,'LEGACY-MONEY-F','OLD-PRODUCT','Old product',10,1,0,0,0,0,10,'CREATED')");
        }
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var columns=c.getMetaData().getColumns(null,"public","order_snapshot","financial_policy_version")) {
            assertTrue(columns.next(),"An additive money provenance schema is required");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT financial_policy_version,handling_charge,checkout_operation_id FROM order_snapshot WHERE id=1")) {
                assertTrue(r.next());assertNull(r.getObject(1));assertNull(r.getObject(2));assertNull(r.getObject(3));
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM order_money_units")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO order_money_units(unit_id,order_line_fk,unit_number,financial_policy_version,net_merchandise_paise,price_provenance,buyer_normal_delivery_paise,buyer_expedited_delivery_paise,platform_paise,handling_paise,seller_delivery_paise,commission_policy) VALUES('synthetic-unit',1,1,1,1000,'UNKNOWN',0,0,0,0,0,'UNKNOWN')");
                assertThrows(SQLException.class,()->s.execute("UPDATE order_money_units SET net_merchandise_paise=999 WHERE unit_id='synthetic-unit'"));
                assertThrows(SQLException.class,()->s.execute("DELETE FROM order_money_units WHERE unit_id='synthetic-unit'"));
                assertThrows(SQLException.class,()->s.execute("INSERT INTO order_money_units(unit_id,order_line_fk,unit_number,financial_policy_version,net_merchandise_paise,price_provenance,buyer_normal_delivery_paise,buyer_expedited_delivery_paise,platform_paise,handling_paise,seller_delivery_paise,commission_policy) VALUES('missing-provenance',1,2,1,1000,'PRODUCT_PRICE_AT_SELECTION',0,0,0,0,0,'UNKNOWN')"));
                assertThrows(SQLException.class,()->s.execute("INSERT INTO order_money_units(unit_id,order_line_fk,unit_number,financial_policy_version,net_merchandise_paise,price_provenance,buyer_normal_delivery_paise,buyer_expedited_delivery_paise,platform_paise,handling_paise,seller_delivery_paise,commission_policy) VALUES('negative-unit',1,2,1,-1,'UNKNOWN',0,0,0,0,0,'UNKNOWN')"));
            }
        }
    }
}
