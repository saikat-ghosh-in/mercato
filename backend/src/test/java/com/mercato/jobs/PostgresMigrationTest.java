package com.mercato.jobs;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import com.mercato.jobs.PostgresTestSupport;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Required CI acceptance: a real PostgreSQL engine validates baseline, populated upgrades and locking. */
class PostgresMigrationTest {
    @Test void simulatorUpgradePreservesJobKeysAndBackfillsGenerationWithoutParsingOtherPayloads()throws Exception{
        String url=database("sandbox_simulator");
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").target("18").load().migrate();
        String id="00000000-0000-0000-0000-000000000091";
        try(Connection c=connect(url);Statement s=c.createStatement()){
            s.execute("INSERT INTO durable_jobs(id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at) VALUES('"+id+"','EASYSHIP_SIMULATE','sandbox-sim:"+id+":7:1','{\"id\":\""+id+"\",\"stage\":1,\"version\":1}',1,'READY',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO durable_jobs(id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at) VALUES('00000000-0000-0000-0000-000000000092','EASYSHIP_SIMULATE','sandbox-sim:00000000-0000-0000-0000-000000000092:0:1','{',1,'READY',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        migrate(url);
        try(Connection c=connect(url);Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT payload::jsonb->>'generation',business_key FROM durable_jobs WHERE id='"+id+"'")){
            assertTrue(rows.next());assertEquals("7",rows.getString(1));assertEquals("sandbox-sim:"+id+":7:1",rows.getString(2));
        }
    }
    @Test void emailTrackingUpgradePreservesMalformedJobsWithoutPublishingPrivatePayloads()throws Exception{
        String url=database("email_tracking");
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).locations("classpath:db/migration").target("7").load().migrate();
        try(Connection c=connect(url);Statement s=c.createStatement()){
            s.execute("INSERT INTO durable_jobs(id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at) VALUES('00000000-0000-0000-0000-000000000001','AUTH_EMAIL','verify-email:test','{',1,'READY',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO durable_jobs(id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at) VALUES('00000000-0000-0000-0000-000000000002','AUTH_EMAIL','legacy-mail','{\"to\":\"legacy@example.test\",\"text\":\"private-link\"}',1,'SUCCEEDED',1,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        migrate(url);
        try(Connection c=connect(url);Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT email_recipient,email_state,email_accepted_at FROM durable_jobs WHERE business_key='legacy-mail'")){
            assertTrue(rows.next());assertEquals("legacy@example.test",rows.getString(1));assertEquals("LEGACY_COMPLETED",rows.getString(2));assertNull(rows.getObject(3));
        }
    }
    @AfterAll static void cleanup(){PostgresTestSupport.cleanup();}
    @Test void freshAndBothPopulatedOciSchemasConvergeWithoutLosingLegacyData() throws Exception {
        String fresh=database("fresh");
        migrate(fresh);
        List<String> expected=schema(fresh);
        for(String name:List.of("mercato","mercato_beta")) {
            String url=database("upgrade_"+name);
            try(Connection connection=connect(url);Statement statement=connection.createStatement()) {
                statement.execute(baseline(name));
                statement.execute("INSERT INTO users(id,account_locked,email_verified,enabled,user_id,username,password,email) VALUES(1,false,false,false,'LEGACY-1','legacy','unused-hash','legacy@mercato.invalid')");
                statement.execute("INSERT INTO roles(role_id,role_name) VALUES(1,'ROLE_USER')");
                statement.execute("INSERT INTO user_role(user_id,role_id) VALUES(1,1)");
            }
            Flyway flyway=flyway(url);
            assertThrows(Exception.class,flyway::migrate,"Unversioned populated schemas must not be silently baselined");
            // Controlled test-only operator action after loading the exact recorded legacy schema.
            flyway.baseline();
            flyway.migrate();
            List<String> actual=schema(url);
            Set<String> missing=new TreeSet<>(expected);missing.removeAll(actual);
            Set<String> added=new TreeSet<>(actual);added.removeAll(expected);
            assertTrue(missing.isEmpty() && added.isEmpty(),()->"Normalized schema drift for "+name+"; missing="+missing+"; added="+added);
            try(Connection connection=connect(url);Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT enabled,self_deactivated,access_version FROM users WHERE user_id='LEGACY-1'")) {
                assertTrue(rows.next());assertFalse(rows.getBoolean(1));assertFalse(rows.getBoolean(2));assertEquals(0,rows.getLong(3));
            }
        }
        validateApplicationMapping(fresh);
    }
    @Test void additiveUpgradeKeepsCapturedLegacyTerms() throws Exception {
        String url=database("legacy_terms");
        try(Connection connection=connect(url);Statement statement=connection.createStatement()) {
            statement.execute(baseline("mercato"));
            statement.execute("INSERT INTO order_snapshot(id,charges,currency,subtotal,total_amount,recipient_phone,order_id,customer_email,customer_name,delivery_address_line1,delivery_city,delivery_pincode,delivery_state,order_status,payment_status,recipient_name) VALUES(1,50,'INR',100,150,'9999999999','LEGACY-ORDER','legacy@mercato.invalid','Legacy','Captured address','City','123456','State','CREATED','PENDING','Legacy')");
        }
        Flyway migration=flyway(url);migration.baseline();migration.migrate();
        try(Connection connection=connect(url);Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT shipping_charge,platform_fee,delivery_method FROM order_snapshot WHERE id=1")) {
            assertTrue(rows.next());assertNull(rows.getObject(1));assertNull(rows.getObject(2));assertNull(rows.getObject(3));
        }
    }
    @Test void existingLowerCommissionIsNeverIncreasedByTheLaunchMigration() throws Exception {
        String url=database("lower_commission");
        Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password())
            .locations("classpath:db/migration").target("1").load().migrate();
        try(Connection connection=connect(url);Statement statement=connection.createStatement()) {
            statement.execute("CREATE TABLE commission_settings(id bigint PRIMARY KEY,rate numeric(5,2) NOT NULL)");
            statement.execute("INSERT INTO commission_settings VALUES(1,0.25)");
        }
        migrate(url);
        try(Connection connection=connect(url);Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT rate,pricing_revision FROM commission_settings WHERE id=1")) {
            assertTrue(rows.next());assertEquals(new java.math.BigDecimal("0.25"),rows.getBigDecimal(1));assertEquals(2,rows.getInt(2));
        }
    }
    private static Flyway flyway(String url){return Flyway.configure().dataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()).baselineOnMigrate(false).baselineVersion("1").locations("classpath:db/migration").load();}
    private static void migrate(String url){flyway(url).migrate();}
    private static Connection connect(String url)throws SQLException{return PostgresTestSupport.connect(url);}
    private static String database(String name)throws SQLException {
        return PostgresTestSupport.database();
    }
    private static String baseline(String name)throws Exception {
        String source=Files.readString(Path.of("../docs/sql/baseline/oci-"+name+".schema.sql"));
        return source.lines().filter(line->!line.startsWith("\\") && !line.startsWith("SET ") && !line.startsWith("SELECT pg_catalog.set_config") && !line.equals("CREATE SCHEMA public;")).collect(java.util.stream.Collectors.joining("\n"));
    }
    private static List<String> schema(String url)throws SQLException {
        TreeSet<String> normalized=new TreeSet<>();
        try(Connection connection=connect(url);Statement statement=connection.createStatement()) {
            String[] queries={
                "SELECT 'COLUMN|'||c.relname||'|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'')||'|'||a.attidentity::text FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='public' AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped AND c.relname<>'flyway_schema_history'",
                "SELECT 'CONSTRAINT|'||c.relname||'|'||x.contype::text||'|'||pg_get_constraintdef(x.oid) FROM pg_constraint x JOIN pg_class c ON c.oid=x.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'",
                "SELECT 'INDEX|'||t.relname||'|'||pg_get_indexdef(i.indexrelid) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND t.relname<>'flyway_schema_history' AND NOT EXISTS(SELECT 1 FROM pg_constraint x WHERE x.conindid=i.indexrelid)",
                "SELECT 'SEQUENCE|'||sequencename||'|'||data_type||'|'||start_value||'|'||min_value||'|'||max_value||'|'||increment_by||'|'||cycle||'|'||cache_size FROM pg_sequences WHERE schemaname='public'"
            };
            for(String query:queries)try(ResultSet rows=statement.executeQuery(query)){while(rows.next())normalized.add(rows.getString(1));}
        }
        return List.copyOf(normalized);
    }
    private static void validateApplicationMapping(String url) {
        LocalContainerEntityManagerFactoryBean entityManager=new LocalContainerEntityManagerFactoryBean();
        entityManager.setDataSource(new DriverManagerDataSource(url,PostgresTestSupport.username(),PostgresTestSupport.password()));
        entityManager.setPackagesToScan("com.mercato");
        entityManager.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManager.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.jdbc.time_zone","UTC",
            "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "hibernate.implicit_naming_strategy","org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy"));
        try {entityManager.afterPropertiesSet();}finally{entityManager.destroy();}
    }
}
