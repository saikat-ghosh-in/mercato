package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Repository.*;
import com.mercato.Mapper.ProductMapper;
import com.mercato.Utils.AuthUtil;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PromotionService {
    private final SellerPromotionRepository campaigns;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderLineRepository lines;
    private final ProductMapper mapper;
    private final AuthUtil auth;
    @Value("${marketplace.promotions.discovery-daily:49}") private BigDecimal discovery;
    @Value("${marketplace.promotions.featured-daily:99}") private BigDecimal featured;
    @Value("${marketplace.promotions.spotlight-daily:199}") private BigDecimal spotlight;
    public record Request(@NotBlank String productId, @NotBlank String tier, int durationDays,
                          @NotNull LocalDate startDate, @NotNull BigDecimal expectedFee,
                          @NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String requestId, boolean authorizeDeduction) {}
    public record Tier(String code, String name, BigDecimal dailyRate, String placement) {}
    public List<Tier> tiers() { return List.of(new Tier("DISCOVERY","Discovery",discovery,"Sponsored collection shelf"), new Tier("FEATURED","Featured",featured,"Home discovery shelf + collection shelf"), new Tier("SPOTLIGHT","Spotlight",spotlight,"Priority in both sponsored shelves")); }
    private EcommUser seller() {
        var user=auth.getLoggedInUser();
        if (user==null || (!user.isSeller() && !user.isAdmin()) || (user.getSellerStatus()!=null && user.getSellerStatus()!=SellerStatus.APPROVED && !user.isAdmin())) throw new AccessDeniedException("Approved seller access required");
        return user;
    }
    public static BigDecimal cost(BigDecimal daily, int days) {
        if (!List.of(3,7,14).contains(days)) throw new IllegalArgumentException("Choose a 3, 7 or 14 day campaign");
        if (daily==null || daily.scale()>2 || daily.signum()<=0 || daily.compareTo(new BigDecimal("1000"))>0) throw new IllegalArgumentException("Promotion pricing is unavailable");
        return daily.multiply(BigDecimal.valueOf(days)).setScale(2,RoundingMode.HALF_UP);
    }
    public BigDecimal netFees(String sellerId) {
        return campaigns.findBySellerIdOrderByCreatedAtDesc(sellerId).stream().map(c->c.getFee().subtract(c.getCredited())).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private BigDecimal available(EcommUser seller) { return lines.eligiblePromotionEarnings(seller.getEmail()).subtract(netFees(seller.getUserId())).max(BigDecimal.ZERO).setScale(2,RoundingMode.DOWN); }
    @Transactional(readOnly=true)
    public Object workspace() {
        var seller=seller(); return Map.of("tiers",tiers(),"durations",List.of(3,7,14),"availableBudget",available(seller),"campaigns",campaigns.findBySellerIdOrderByCreatedAtDesc(seller.getUserId()));
    }
    @Transactional
    public SellerPromotion book(Request request) {
        var seller=seller();
        users.findByUserIdForUpdate(seller.getUserId()).orElseThrow(); // Serialize all bookings/cancellations for this seller.
        var prior=campaigns.findByRequestId(request.requestId());
        if(prior.isPresent()) {
            var p=prior.get();
            if(!p.getSellerId().equals(seller.getUserId())) throw new AccessDeniedException("Request belongs to another seller");
            if(!p.getProductId().equals(request.productId()) || !p.getTier().equals(request.tier()) || p.getDurationDays()!=request.durationDays() || !p.getStartsAt().atZone(ZoneOffset.UTC).toLocalDate().equals(request.startDate()) || p.getFee().compareTo(request.expectedFee())!=0) throw new IllegalArgumentException("Request ID was already used for another quote");
            return p;
        }
        if(!request.authorizeDeduction()) throw new IllegalArgumentException("Authorize the quoted deduction from seller earnings");
        var product=products.findByProductIdForUpdate(request.productId()).orElseThrow(()->new ResourceNotFoundException("Product","id",request.productId()));
        if(product.getSeller()==null || !product.getSeller().getUserId().equals(seller.getUserId())) throw new AccessDeniedException("You can promote only your own products");
        if(!product.isPurchasable() || product.getAvailableQty()<1) throw new IllegalArgumentException("Choose an approved, active product with available stock");
        BigDecimal daily=tiers().stream().filter(t->t.code().equals(request.tier())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown promotion tier")).dailyRate();
        BigDecimal fee=cost(daily,request.durationDays());
        if(fee.compareTo(request.expectedFee())!=0) throw new IllegalArgumentException("The promotion quote changed. Review the current price");
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        if(request.startDate().isBefore(today) || request.startDate().isAfter(today.plusDays(30))) throw new IllegalArgumentException("Start today or within the next 30 days (UTC)");
        Instant start=request.startDate().equals(today)?Instant.now():request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end=start.plus(Duration.ofDays(request.durationDays()));
        var booked=campaigns.findBySellerIdOrderByCreatedAtDesc(seller.getUserId()).stream().filter(c->c.getStatus().equals("BOOKED") && c.getEndsAt().isAfter(Instant.now())).toList();
        if(booked.size()>=3) throw new IllegalArgumentException("A maximum of three current or scheduled campaigns is allowed");
        if(booked.stream().anyMatch(c->c.getProductId().equals(request.productId()) && start.isBefore(c.getEndsAt()) && end.isAfter(c.getStartsAt()))) throw new IllegalArgumentException("This product already has a campaign during that period");
        if(available(seller).compareTo(fee)<0) throw new IllegalArgumentException("Not enough eligible earnings. Complete paid fulfillments before booking a promotion");
        SellerPromotion campaign=new SellerPromotion();
        campaign.setRequestId(request.requestId());campaign.setSellerId(seller.getUserId()); campaign.setProductId(request.productId());campaign.setTier(request.tier());campaign.setDurationDays(request.durationDays());campaign.setDailyRate(daily);campaign.setFee(fee);campaign.setCreatedAt(Instant.now());campaign.setStartsAt(start);campaign.setEndsAt(end);
        return campaigns.save(campaign);
    }
    @Transactional
    public SellerPromotion cancel(Long id) {
        var seller=seller(); users.findByUserIdForUpdate(seller.getUserId()).orElseThrow();
        var c=campaigns.findById(id).filter(p->p.getSellerId().equals(seller.getUserId())).orElseThrow(()->new ResourceNotFoundException("Campaign","id",id));
        if(!"BOOKED".equals(c.getStatus())) return c;
        Instant now=Instant.now();
        if(!now.isBefore(c.getEndsAt())) throw new IllegalArgumentException("Completed campaigns cannot be cancelled");
        long usedDays=now.isBefore(c.getStartsAt())?0:Math.min(c.getDurationDays(),Duration.between(c.getStartsAt(),now).toDays()+1);
        c.setCredited(c.getDailyRate().multiply(BigDecimal.valueOf(c.getDurationDays()-usedDays)));
        c.setStatus("CANCELLED");c.setCancelledAt(now);return c;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> placements(String placement, String category, String keyword) {
        if(!List.of("home","catalog").contains(placement)) throw new IllegalArgumentException("Unknown placement");
        Instant now=Instant.now();
        var booked=campaigns.findByStatus("BOOKED");
        long rotation=Math.max(1,booked.stream().mapToLong(SellerPromotion::getId).max().orElse(1));
        return booked.stream().filter(c->!now.isBefore(c.getStartsAt()) && now.isBefore(c.getEndsAt()))
            .filter(c->!placement.equals("home") || !c.getTier().equals("DISCOVERY"))
            .sorted(Comparator.comparingInt((SellerPromotion c)->c.getTier().equals("SPOTLIGHT")?0:c.getTier().equals("FEATURED")?1:2).thenComparingLong(c->Math.floorMod(c.getId() - now.getEpochSecond()/300,rotation)))
            .map(c->products.findByProductId(c.getProductId()).orElse(null))
            .filter(p->p!=null && p.isPurchasable() && p.getAvailableQty()>0 && p.getSeller().isEnabled() && !p.getSeller().isAccountLocked())
            .filter(p->category==null || category.isBlank() || p.getCategory().getCategoryName().equals(category))
            .filter(p->keyword==null || keyword.isBlank() || (p.getProductName()+" "+p.getDescription()).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
            .limit(3).map(p->Map.<String,Object>of("product",mapper.toDTO(p),"label","Sponsored")).toList();
    }
}
