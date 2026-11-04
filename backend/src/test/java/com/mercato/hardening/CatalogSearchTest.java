package com.mercato.hardening;

import com.mercato.Entity.Category;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Repository.CategoryRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Transactional
class CatalogSearchTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;

    private Category category;
    private EcommUser seller;
    private Product tee;

    @BeforeEach
    void setUp() {
        seller = users.findByUsername("reviewer").orElseThrow();
        category = new Category();
        category.setCategoryName("Running apparel");
        categories.save(category);
        tee = createProduct("Cotton T-Shirt", "Demo Athletics", "TS-001", 5, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tshirt", "T-Shirt", "T SHIRT", "t–shirt", "tshirts"})
    void findsTshirtAcrossCommonSpellings(String keyword) throws Exception {
        mvc.perform(get("/api/public/products").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(tee.getProductId()));
    }

    @Test
    void combinesBrandCategoryNameAndCodeWhileKeepingCatalogFilters() throws Exception {
        createProduct("Linen T-Shirt", "Demo Athletics", "TS-002", 5, true);
        createProduct("Unlisted cotton T-Shirt", "Demo Athletics", "TS-001", 5, false);
        createProduct("Alternate cotton T-Shirt", "Demo Athletics", "TS-001", 0, true);
        mvc.perform(get("/api/public/products")
                        .param("keyword", "running TS001 athletics cotton")
                        .param("category", category.getCategoryName())
                        .param("inStock", "true")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(tee.getProductId()));
    }

    @Test
    void punctuationOnlyQueryDoesNotBecomeASqlWildcard() throws Exception {
        mvc.perform(get("/api/public/products").param("keyword", "%_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private Product createProduct(String name, String brand, String code, int stock, boolean active) {
        return products.saveAndFlush(Product.builder()
                .productName(name)
                .brand(brand)
                .productCode(code)
                .description("An everyday lightweight top.")
                .primaryImageUrl("https://example.com/product.jpg")
                .category(category)
                .seller(seller)
                .retailPrice(BigDecimal.valueOf(399))
                .physicalQty(stock)
                .active(active)
                .build());
    }
}
