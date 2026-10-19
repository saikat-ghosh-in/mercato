package com.mercato.Configuration;

import com.mercato.Entity.Category;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Repository.CategoryRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Component
@Profile("demo & !prod")
@Order(1)
@RequiredArgsConstructor
public class DemoCatalogInitializer implements CommandLineRunner {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final UserRepository users;

    @Override
    @Transactional
    public void run(String... args) {
        if (products.count() > 0) return;
        EcommUser seller = users.findByUsername("reviewer").orElseThrow();
        seller.setSellerDisplayName("Mercato Studio");
        String[][] entries = {
            {"Home & Living", "Linen lounge chair", "12999", "15", "photo-1567538096630-e0c55bd6374c"},
            {"Fashion", "The everyday canvas tote", "1499", "10", "photo-1553062407-98eeb64c6a62"},
            {"Electronics", "Wireless over-ear headphones", "4999", "20", "photo-1505740420928-5e560c06d30e"},
            {"Beauty & Care", "Botanical daily essentials", "1299", "0", "photo-1608571423902-eed4a5ad8108"},
            {"Home & Living", "Warm glow table lamp", "3299", "12", "photo-1507473885765-e6ed057f782c"},
            {"Fashion", "Classic leather everyday watch", "3999", "15", "photo-1524805444758-089113d48a6d"},
            {"Electronics", "Portable studio speaker", "2499", "10", "photo-1608043152269-423dbba4e7e1"},
            {"Beauty & Care", "Signature eau de parfum", "2799", "8", "photo-1541643600914-78b084683601"},
            {"Home & Living", "Natural ceramic vase", "1199", "5", "photo-1578500494198-246f612d3b3d"},
            {"Fashion", "Essential white sneakers", "2999", "18", "photo-1549298916-b41d501d3772"},
            {"Electronics", "Everyday compact camera", "24999", "5", "photo-1516035069371-29a1b244cc32"},
            {"Home & Living", "Soft woven cushion", "899", "0", "photo-1584100936595-c0654b55a2e2"}
        };
        java.util.Map<String, Category> byName = new java.util.HashMap<>();
        for (String[] entry : entries) {
            Category category = byName.computeIfAbsent(entry[0], name -> {
                Category c = new Category();
                c.setCategoryName(name);
                return categories.save(c);
            });
            products.save(Product.builder().productName(entry[1])
                .description("A considered everyday essential from the Mercato Studio review collection. Demo catalog item for local testing.")
                .brand("Mercato Studio Demo").productCode("DEMO-" + entry[4].substring(6, 14))
                .weightKg(new BigDecimal(entry[1].equals("Linen lounge chair") ? "12" : "0.5"))
                .lengthCm(new BigDecimal(entry[1].equals("Linen lounge chair") ? "110" : "20"))
                .widthCm(new BigDecimal(entry[1].equals("Linen lounge chair") ? "60" : "15"))
                .heightCm(new BigDecimal(entry[1].equals("Linen lounge chair") ? "45" : "10"))
                .highlights(java.util.List.of("Local demonstration product with 25 units of starting stock", "Editable product information for staging review", "Synthetic listing — not a live offer"))
                .specifications(java.util.List.of(new com.mercato.Entity.ProductSpecification("Collection", "Local review catalog"), new com.mercato.Entity.ProductSpecification("Category", entry[0])))
                .materials("Demonstration information. Replace with verified manufacturer materials before publishing.")
                .care("Follow the manufacturer's care instructions. This is synthetic review data.")
                .warranty("Demo only: no commercial warranty is offered for this synthetic listing.")
                .packageContents("One demonstration item. Packaging and accessories are illustrative.")
                .deliveryReturns("Local testing only. No physical delivery or return is provided.")
                .secondaryImageUrls(new java.util.ArrayList<>(java.util.List.of("https://images.unsplash.com/" + entry[4] + "?auto=format&fit=crop&w=900&q=85")))
                .category(category).seller(seller).physicalQty(25)
                .retailPrice(new BigDecimal(entry[2])).discountPercent(new BigDecimal(entry[3]))
                .primaryImageUrl("https://images.unsplash.com/" + entry[4] + "?auto=format&fit=crop&w=700&q=85")
                .build());
        }
    }
}
