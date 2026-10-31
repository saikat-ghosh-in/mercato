package com.mercato.order;

import com.mercato.jobs.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresWorkflowMigrationTest {
    @AfterAll static void cleanup(){PostgresTestSupport.cleanup();}
    @Test void evidenceBackfillPreservesHistoryLocksUncertainRowsAndNeverSchedulesRetroactiveTimeouts()throws Exception{
        String url=PostgresTestSupport.database();
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").target("8").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var s=c.createStatement()){
            for(int i=1;i<=3;i++){
                s.execute("INSERT INTO payments(id,payment_id,amount,currency,payment_method,status,completed_at,captured_at,captured_amount) VALUES("+i+",'MIG-PAY-"+i+"',100,'INR','UNKNOWN','SUCCESS','2026-10-20T09:00:00Z',"+(i==3?"NULL,NULL":"'2026-10-20T10:00:00Z',100")+")");
                s.execute("INSERT INTO order_snapshot(id,order_id,payment_fk,charges,currency,subtotal,total_amount,recipient_phone,customer_email,customer_name,delivery_address_line1,delivery_city,delivery_pincode,delivery_state,order_status,payment_status,recipient_name,allocation_completed_at) VALUES("+i+",'MIG-ORDER-"+i+"',"+i+",0,'INR',100,100,'9999999999','legacy@test.invalid','Legacy','Captured address','City','123456','State','CONFIRMED','SUCCESS','Legacy','2026-10-20T11:00:00Z')");
                s.execute("INSERT INTO order_lines_snapshot(id,order_fk,fulfillment_id,order_line_number,ordered_qty,accepted_qty,shipped_qty,cancelled_qty,line_total,revenue,unit_price,product_id,product_name,order_line_status) VALUES("+i+","+i+",'MIG-FUL-"+i+"',1,5,"+(i==2?5:0)+","+(i==1?2:0)+","+(i==2?2:0)+",100,0,20,'MIG-PRODUCT','Legacy product','CONFIRMED')");
            }
            s.execute("INSERT INTO order_line_state_transitions(id,order_line_fk,from_status,to_status,action,triggered_by,qty_affected) VALUES(1,3,'CONFIRMED','PROCESSING','ACCEPT','SELLER',4)");
        }
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").load().migrate();
        try(var c=PostgresTestSupport.connect(url);var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT accepted_qty,preparing_qty,packed_qty,shipped_qty,delivered_qty FROM order_lines_snapshot WHERE id=1")){
                assertTrue(r.next());for(int i=1;i<=4;i++)assertEquals(2,r.getInt(i));assertEquals(0,r.getInt(5));
            }
            try(var r=s.executeQuery("SELECT confirmed_at,seller_actions_available_at,workflow_version,workflow_review_required FROM order_snapshot WHERE id=1")){
                assertTrue(r.next());assertEquals(java.time.Instant.parse("2026-10-20T11:00:00Z"),r.getTimestamp(1).toInstant());assertEquals(java.time.Instant.parse("2026-10-20T12:00:00Z"),r.getTimestamp(2).toInstant());assertEquals(1,r.getInt(3));assertTrue(r.getBoolean(4));
            }
            try(var r=s.executeQuery("SELECT confirmed_at,workflow_review_required FROM order_snapshot WHERE id=3")){assertTrue(r.next());assertNull(r.getObject(1));assertTrue(r.getBoolean(2));}
            try(var r=s.executeQuery("SELECT accepted_qty FROM order_lines_snapshot WHERE id=2")){assertTrue(r.next());assertEquals(5,r.getInt(1));}
            try(var r=s.executeQuery("SELECT accepted_qty FROM order_lines_snapshot WHERE id=3")){assertTrue(r.next());assertEquals(4,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM durable_jobs WHERE job_type='FULFILLMENT_DEADLINE'")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM fulfillment_work_items WHERE kind='LEGACY_WORKFLOW_REVIEW'")){assertTrue(r.next());assertEquals(3,r.getInt(1));}
            assertThrows(SQLException.class,()->s.execute("UPDATE order_snapshot SET confirmed_at=CURRENT_TIMESTAMP WHERE id=1"));
            assertThrows(SQLException.class,()->s.execute("INSERT INTO order_lines_snapshot(id,order_fk,fulfillment_id,order_line_number,ordered_qty,accepted_qty,shipped_qty,cancelled_qty,line_total,revenue,unit_price,product_id,product_name,order_line_status) VALUES(4,1,'MIG-FUL-1',2,5,6,0,0,100,0,20,'MIG-PRODUCT','Bad new row','CONFIRMED')"));
            assertThrows(SQLException.class,()->s.execute("INSERT INTO order_lines_snapshot(id,order_fk,fulfillment_id,order_line_number,ordered_qty,accepted_qty,shipped_qty,cancelled_qty,line_total,revenue,unit_price,product_id,product_name,order_line_status,workflow_legacy_review) VALUES(4,1,'MIG-FUL-1',2,5,6,0,0,100,0,20,'MIG-PRODUCT','Bad new row','CONFIRMED',true)"));
        }
    }
}
