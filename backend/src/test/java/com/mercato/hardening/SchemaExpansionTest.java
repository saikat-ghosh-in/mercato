package com.mercato.hardening;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaExpansionTest {
    @Test void accountAccessMigrationIsAdditiveIdempotentAndConservativeForDisabledAccounts() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:access-schema;MODE=PostgreSQL");var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE users(id bigint primary key, enabled boolean); CREATE TABLE roles(role_id integer primary key, role_name varchar(20)); CREATE TABLE user_role(user_id bigint,role_id integer,primary key(user_id,role_id)); INSERT INTO users VALUES(1,false),(2,true); INSERT INTO roles VALUES(10,'ROLE_USER'),(11,'ROLE_SELLER'); INSERT INTO user_role VALUES(2,10),(2,11)");
            String sql=Files.readString(Path.of("../docs/sql/2026-10-account-access.sql"));statement.execute(sql);statement.execute(sql);
            try(var rows=statement.executeQuery("SELECT enabled,self_deactivated,seller_suspended,access_version FROM users WHERE id=1")) {
                assertTrue(rows.next());assertFalse(rows.getBoolean(1));assertFalse(rows.getBoolean(2));assertFalse(rows.getBoolean(3));assertEquals(0,rows.getLong(4));
            }
            try(var rows=statement.executeQuery("SELECT count(*) FROM user_role")){assertTrue(rows.next());assertEquals(3,rows.getInt(1));}
            statement.execute("INSERT INTO account_access_events(subject_id,actor_id,actor_username,action,reason,previous_state,next_state,created_at) VALUES('subject','actor','reviewer','DEACTIVATE','Synthetic migration check','Active','Deactivated',CURRENT_TIMESTAMP)");
            statement.execute("DELETE FROM users WHERE id=1");
            try(var rows=statement.executeQuery("SELECT count(*) FROM account_access_events")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
        }
    }
    @Test void additiveScriptCanRunTwiceWithoutChangingExistingProductData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:schema-expansion;MODE=PostgreSQL"); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, product_name VARCHAR(255)); INSERT INTO products VALUES (1, 'Existing product')");
            String sql = Files.readString(Path.of("sql/20261018-rich-product-details.sql"));
            statement.execute(sql);
            statement.execute(sql);
            try (var rows = statement.executeQuery("SELECT product_name, brand FROM products WHERE id=1")) {
                assertTrue(rows.next()); assertEquals("Existing product", rows.getString(1)); assertNull(rows.getString(2));
            }
            statement.execute("INSERT INTO product_highlights VALUES(1, 0, 'First highlight')");
            statement.execute("INSERT INTO product_specifications VALUES(1, 0, 'Material', 'Cotton')");
            statement.execute("DELETE FROM products WHERE id=1");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM product_highlights")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
    }
}
