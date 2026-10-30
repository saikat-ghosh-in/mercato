package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.FulfillmentOrderMapper;
import com.mercato.Payloads.Response.FulfillmentOrderResponseDTO;
import com.mercato.Repository.OrderLineRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements FulfillmentService {

    private final OrderLineRepository orderLineRepository;
    private final AuthUtil authUtil;
    private final FulfillmentWorkflow workflow;
    private final SellerOrderVisibility visibility;
    private final OrderProductImages images;

    @Override
    @Transactional(readOnly = true)
    public List<FulfillmentOrderResponseDTO> getOpenFulfillmentOrders() {
        EcommUser seller = authUtil.getLoggedInUser();

        List<OrderLine> lines = orderLineRepository
                .findAllBySellerEmail(seller.getEmail(),visibility.now());

        return groupByFulfillmentId(lines, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FulfillmentOrderResponseDTO> getClosedFulfillmentOrders() {
        EcommUser seller = authUtil.getLoggedInUser();

        List<OrderLine> lines = orderLineRepository
                .findAllBySellerEmail(seller.getEmail(),visibility.now());

        return groupByFulfillmentId(lines, false);
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentOrderResponseDTO getFulfillmentOrder(String fulfillmentId) {
        EcommUser seller = authUtil.getLoggedInUser();

        List<OrderLine> lines = orderLineRepository
                .findAllByFulfillmentIdAndSellerEmail(fulfillmentId, seller.getEmail(),visibility.now());

        if (lines.isEmpty()) {
            throw new ResourceNotFoundException("FulfillmentOrder", "fulfillmentId", fulfillmentId);
        }

        var result=FulfillmentOrderMapper.toDTO(fulfillmentId, lines,workflow);
        images.enrich(result.getOrderLines());return result;
    }


    private List<FulfillmentOrderResponseDTO> groupByFulfillmentId(List<OrderLine> lines, boolean open) {
        Map<String, List<OrderLine>> grouped = lines.stream()
                .collect(Collectors.groupingBy(OrderLine::getFulfillmentId));

        var result=grouped.entrySet().stream()
                .filter(entry -> open
                        ? entry.getValue().stream().anyMatch(line -> !line.isTerminal())
                        : entry.getValue().stream().allMatch(OrderLine::isTerminal))
                .map(entry -> FulfillmentOrderMapper.toDTO(entry.getKey(), entry.getValue(),workflow))
                .sorted(java.util.Comparator.comparing(FulfillmentOrderResponseDTO::getCreatedAt,java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())).thenComparing(FulfillmentOrderResponseDTO::getFulfillmentId))
                .toList();
        images.enrich(result.stream().flatMap(f->f.getOrderLines().stream()).toList());return result;
    }
}
