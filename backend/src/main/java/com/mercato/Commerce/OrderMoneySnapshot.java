package com.mercato.Commerce;

import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Service.OrderServiceImpl;
import com.mercato.Utils.*;
import java.util.*;
import java.math.*;
import java.nio.charset.StandardCharsets;

/** Called only while preparing a new, locked checkout. No legacy reconstruction. */
public final class OrderMoneySnapshot {
    private OrderMoneySnapshot(){}
    public static void snapshot(Order order,List<CartItem> selections) {
        if(order.getFinancialPolicyVersion()!=null || order.getOrderLines().stream().anyMatch(l->!l.getMoneyUnits().isEmpty()))
            throw new IllegalStateException("Monetary units are already snapshotted");
        long count=0;
        for(var line:order.getOrderLines()) {
            if(line.getOrderedQty()<1)throw new IllegalArgumentException("Positive unit quantities are required");
            count=Math.addExact(count,(long)line.getOrderedQty());
            if(count>OrderServiceImpl.MAX_CHECKOUT_UNITS)throw new IllegalArgumentException("A checkout supports at most 10000 monetary units");
        }
        if(count==0 || selections.size()!=order.getOrderLines().size())throw new IllegalArgumentException("Every line requires its selected quote");
        Map<String,CartItem> selected=new HashMap<>();
        for(var item:selections)if(selected.put(item.getProduct().getProductId(),item)!=null)throw new IllegalArgumentException("Duplicate selected product");
        Map<String,OrderMoneyUnit> units=new TreeMap<>();Map<String,BigDecimal> weights=new TreeMap<>();
        long merchandise=0,shipping=0;
        for(var line:order.getOrderLines().stream().sorted(Comparator.comparingInt(OrderLine::getOrderLineNumber)).toList()) {
            var item=selected.get(line.getProductId());
            if(item==null || item.getQuantity()!=line.getOrderedQty() || paise(item.getItemPrice())!=paise(line.getUnitPrice())
                || Math.multiplyExact(paise(line.getUnitPrice()),line.getOrderedQty())!=paise(line.getLineTotal()))
                throw new IllegalArgumentException("Line differs from the selected quote");
            Map<String,BigDecimal> lineWeights=new TreeMap<>();
            for(int n=1;n<=line.getOrderedQty();n++) {
                String key=String.format(java.util.Locale.ROOT,"%05d:%05d",line.getOrderLineNumber(),n);
                var unit=new OrderMoneyUnit();unit.setOrderLine(line);unit.setUnitNumber(n);unit.setFinancialPolicyVersion(1);
                unit.setUnitId(UUID.nameUUIDFromBytes((order.getOrderId()+"|"+line.getOrderLineNumber()+"|"+n).getBytes(StandardCharsets.UTF_8)).toString());
                unit.setNetMerchandisePaise(paise(line.getUnitPrice()));unit.setPriceProvenance("UNKNOWN");
                if("PRODUCT_PRICE_AT_SELECTION".equals(item.getSelectedPriceProvenance()) && item.getSelectedRetailPrice()!=null && item.getSelectedDiscountPercent()!=null) {
                    long retail=paise(item.getSelectedRetailPrice());
                    if(retail<unit.getNetMerchandisePaise())throw new IllegalArgumentException("Selected discount exceeds retail price");
                    unit.setRetailPaise(retail);unit.setDiscountPaise(retail-unit.getNetMerchandisePaise());unit.setDiscountPercent(item.getSelectedDiscountPercent());unit.setPriceProvenance(item.getSelectedPriceProvenance());
                }
                unit.setCommissionRatePercent(line.getCommissionRatePercent());
                unit.setCommissionCapPaise(line.getCommissionCapPerItem()==null?null:paise(line.getCommissionCapPerItem()));
                unit.setCommissionPolicy(line.getCommissionRatePercent()==null?"UNKNOWN":line.getCommissionCapPerItem()==null?"LEGACY_AGGREGATE":"PER_UNIT_CAPPED");
                units.put(key,unit);weights.put(key,BigDecimal.valueOf(unit.getNetMerchandisePaise()));lineWeights.put(key,BigDecimal.ONE);
                merchandise=Math.addExact(merchandise,unit.getNetMerchandisePaise());
            }
            var normal=DeliveryAllocation.split(item.getBuyerNormalDeliveryCharge(),lineWeights);
            var expedited=DeliveryAllocation.split(item.getBuyerExpeditedDeliveryCharge(),lineWeights);
            var seller=DeliveryAllocation.split(item.getSellerDeliveryCharge(),lineWeights);
            Map<String,BigDecimal> legacyFee=line.getCommissionRatePercent()!=null && line.getCommissionCapPerItem()==null
                ?DeliveryAllocation.split(CommissionMath.forUnits(line.getUnitPrice(),line.getOrderedQty(),line.getCommissionRatePercent(),null),lineWeights):Map.of();
            for(var key:lineWeights.keySet()) {
                var unit=units.get(key);unit.setBuyerNormalDeliveryPaise(paise(normal.get(key)));unit.setBuyerExpeditedDeliveryPaise(paise(expedited.get(key)));unit.setSellerDeliveryPaise(paise(seller.get(key)));
                if(line.getCommissionRatePercent()!=null) {
                    if(line.getCommissionRatePercent().signum()<0)throw new IllegalArgumentException("Commission rate cannot be negative");
                    unit.setCommissionPaise(paise(legacyFee.isEmpty()?CommissionMath.forUnits(line.getUnitPrice(),1,line.getCommissionRatePercent(),line.getCommissionCapPerItem()):legacyFee.get(key)));
                }
                shipping=Math.addExact(shipping,Math.addExact(unit.getBuyerNormalDeliveryPaise(),unit.getBuyerExpeditedDeliveryPaise()));
            }
        }
        if(merchandise!=paise(order.getSubtotal()) || shipping!=paise(order.getShippingCharge())
            || Math.addExact(shipping,Math.addExact(paise(order.getPlatformFee()),paise(order.getHandlingCharge())))!=paise(order.getCharges())
            || Math.addExact(merchandise,paise(order.getCharges()))!=paise(order.getTotalAmount()))
            throw new IllegalArgumentException("Quoted money components do not balance");
        var platform=DeliveryAllocation.split(order.getPlatformFee(),weights);var handling=DeliveryAllocation.split(order.getHandlingCharge(),weights);
        for(var entry:units.entrySet()) {
            var unit=entry.getValue();unit.setPlatformPaise(paise(platform.get(entry.getKey())));unit.setHandlingPaise(paise(handling.get(entry.getKey())));
            unit.getOrderLine().getMoneyUnits().add(unit);
        }
        order.setFinancialPolicyVersion(1);
    }
    public static long paise(BigDecimal value) {
        if(value==null || value.signum()<0)throw new IllegalArgumentException("A known non-negative money source is required");
        try{return value.movePointRight(2).longValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Money must fit whole paise",invalid);}
    }
    /**
     * Caller supplies eligible, already attributed bucket identities, least advanced first.
     * This does not derive physical history from counters. Persist the selected IDs in the
     * same order-locked command transaction before updating counters or requesting refunds.
     */
    public static List<OrderMoneyUnit> selectUnits(OrderLine line,List<? extends Collection<String>> leastAdvancedBuckets,int quantity) {
        if(line==null || line.getMoneyUnits().isEmpty() || leastAdvancedBuckets==null || quantity<=0)
            throw new IllegalArgumentException("Known monetary units, explicit buckets and positive quantity are required");
        Map<String,OrderMoneyUnit> known=new HashMap<>();
        for(var unit:line.getMoneyUnits())known.put(unit.getUnitId(),unit);
        var seen=new HashSet<String>();var eligible=new ArrayList<OrderMoneyUnit>();
        for(var bucket:leastAdvancedBuckets) {
            if(bucket==null)throw new IllegalArgumentException("An explicit unit bucket is required");
            var members=new ArrayList<OrderMoneyUnit>();
            for(var id:bucket) {
                if(!known.containsKey(id) || !seen.add(id))
                    throw new IllegalArgumentException("Unit identities must be unique and belong to this line");
                members.add(known.get(id));
            }
            members.sort(Comparator.comparingInt(OrderMoneyUnit::getUnitNumber));eligible.addAll(members);
        }
        if(quantity>eligible.size())throw new IllegalArgumentException("Selected quantity exceeds eligible monetary units");
        return List.copyOf(eligible.subList(0,quantity));
    }
}
