package com.mercato.hardening;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaExpansionTest {
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
