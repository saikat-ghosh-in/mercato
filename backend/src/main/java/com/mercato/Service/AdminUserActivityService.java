package com.mercato.Service;
import com.mercato.Entity.*;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Payloads.Response.AdminUserActivityDTO;
import com.mercato.Payloads.Response.AdminUserActivityDTO.*;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminUserActivityService {
    private final UserRepository users;
    private final CartRepository carts;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final ListingRequestRepository requests;
    private final AuthUtil auth;
    @Transactional(readOnly=true)
    public AdminUserActivityDTO inspect(String id,String section,int page,String status) {
        var actor=auth.getLoggedInUser();
        if(actor==null || !actor.isAdmin()) throw new AccessDeniedException("Admin access required");
        if(!Set.of("overview","orders","products","requests").contains(section) || page<0 || page>100000
                || !Set.of("ALL","PENDING","APPROVED","REJECTED","SUPERSEDED","WITHDRAWN").contains(status)) throw new IllegalArgumentException("Invalid activity page or filter");
        var user=users.findByUserId(id).orElseThrow(()->new ResourceNotFoundException("User","id",id));
        boolean seller=user.isSeller() || (user.getSellerStatus()!=null && user.getSellerStatus()!=SellerStatus.NONE);
        if(!seller && Set.of("products","requests").contains(section)) throw new IllegalArgumentException("This account has no seller activity");
        var cart=carts.findByUser_UserId(id).orElse(null);
        CartSnapshot snapshot=cart==null?null:new CartSnapshot(cart.getCartId(),cart.getUpdatedAt(),cart.getCartQuantity(),cart.getSubtotal(),cart.getTotalCharges(),cart.getTotal(),cart.getDeliveryMethod().name());
        PageBlock<?> result;
        if(section.equals("overview")) {
            var lines=cart==null?List.<CartLine>of():cart.getCartItems().stream().sorted(Comparator.comparing(item->item.getProduct().getProductId()))
                    .map(item->new CartLine(item.getProduct().getProductId(),item.getProduct().getProductName(),item.getQuantity(),item.getItemPrice(),item.getLineTotal(),item.getProduct().isPurchasable() && !item.isOutOfStock())).toList();
            int start=Math.min(page*10,lines.size()); result=new PageBlock<>(lines.subList(start,Math.min(start+10,lines.size())),page,(lines.size()+9)/10,lines.size());
        } else if(section.equals("orders")) {
            result=block(orders.userActivityOrders(user.getEmail(),PageRequest.of(page,10,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))));
        } else if(section.equals("products")) {
            result=block(products.findBySellerUserId(id,PageRequest.of(page,10,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))))
                    .map(product->new ProductLine(product.getProductId(),product.getProductName(),product.getCategory().getCategoryName(),product.getSellingPrice(),product.getListingStatus()==null?"APPROVED":product.getListingStatus(),product.isActive(),product.getCreatedAt())));
        } else {
            var paging=PageRequest.of(page,10,Sort.by(Sort.Order.desc("requestedAt"),Sort.Order.desc("id")));
            result=block(status.equals("ALL")?requests.findBySellerId(id,paging):requests.findBySellerIdAndStatus(id,status,paging));
        }
        return new AdminUserActivityDTO(seller,seller?user.getSellerDisplayName():null,seller?(user.getSellerStatus()==null?"APPROVED":user.getSellerStatus().name()):null,
                orders.countByCustomerEmail(user.getEmail()),seller?products.countBySellerUserId(id):0,seller?requests.countBySellerId(id):0,seller?requests.countBySellerIdAndStatus(id,"PENDING"):0,snapshot,result);
    }
    private static <T> PageBlock<T> block(Page<T> page) {return new PageBlock<>(page.getContent(),page.getNumber(),page.getTotalPages(),page.getTotalElements());}
}
