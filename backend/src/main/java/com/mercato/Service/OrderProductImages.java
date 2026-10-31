package com.mercato.Service;

import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Current catalog imagery alongside immutable purchased names and prices. One query per response. */
@Component @RequiredArgsConstructor
public class OrderProductImages {
    private final ProductRepository products;
    public void enrich(Collection<OrderLineResponseDTO> lines) {
        var ids=lines.stream().map(OrderLineResponseDTO::getProduct).filter(Objects::nonNull).map(OrderLineResponseDTO.ProductDetails::getProductId).filter(Objects::nonNull).distinct().toList();
        if(ids.isEmpty())return;
        var images=new HashMap<String,String>();
        for(var row:products.primaryImages(ids))images.put((String)row[0],(String)row[1]);
        lines.stream().map(OrderLineResponseDTO::getProduct).filter(Objects::nonNull).forEach(p->p.setPrimaryImageUrl(images.get(p.getProductId())));
    }
}
