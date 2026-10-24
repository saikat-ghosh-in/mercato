package com.mercato.Service;
import com.mercato.Entity.Product;
import com.mercato.Entity.ListingRequest;
import com.mercato.Repository.ListingRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional
public class ListingHistoryService {
    private final ListingRequestRepository requests;
    private ListingRequest snapshot(Product product,boolean imported) {
        var request=new ListingRequest(); request.setSellerId(product.getSeller().getUserId());
        request.setProductId(product.getProductId()); request.setProductName(product.getProductName());
        request.setCategoryName(product.getCategory().getCategoryName());
        request.setPrice(product.getRetailPrice().multiply(BigDecimal.ONE.subtract(product.getDiscountPercent()==null?BigDecimal.ZERO:product.getDiscountPercent().movePointLeft(2))).setScale(2,RoundingMode.HALF_UP));
        request.setRequestedAt(imported && product.getCreatedAt()!=null?product.getCreatedAt():Instant.now());
        request.setImported(imported); request.setStatus("PENDING"); return request;
    }
    // Call while holding the product's write lock (new products are transaction-local).
    public void submitted(Product product) {
        closeOpen(product,"SUPERSEDED","Replaced by a newer submission.");
        requests.flush(); // Close the previous request before inserting under the unique open-request index.
        requests.save(snapshot(product,false));
    }
    public void reviewed(Product product) {
        var request=requests.findFirstByProductIdAndStatusOrderByIdDesc(product.getProductId(),"PENDING").orElseGet(()->snapshot(product,true));
        request.setStatus(product.getListingStatus()); request.setReviewedAt(product.getListingReviewedAt());
        request.setReviewedBy(product.getListingReviewedBy()); request.setNote(product.getListingReviewNote()); requests.save(request);
    }
    public void closeOpen(Product product,String status,String note) {
        requests.findFirstByProductIdAndStatusOrderByIdDesc(product.getProductId(),"PENDING").ifPresent(request->{request.setStatus(status);request.setReviewedAt(Instant.now());request.setNote(note);});
    }
}
