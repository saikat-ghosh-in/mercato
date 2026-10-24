package com.mercato.order;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Payloads.Request.OrderCancelRequestDTO;
import com.mercato.Repository.OrderRepository;
import com.mercato.Service.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancellationLockTest {
 @Mock OrderRepository orderRepository;
 @InjectMocks OrderServiceImpl service;
 @Test void cancellationReadsCurrentStateUnderAggregateLockBeforeDecidingEligibility(){
  var current=new Order();current.setOrderId("ORD-LOCK");current.setOrderStatus(OrderStatus.FULFILLMENT_COMPLETE);
  current.setPayment(new Payment());when(orderRepository.lockByOrderId("ORD-LOCK")).thenReturn(Optional.of(current));
  var request=new OrderCancelRequestDTO();request.setOrderId("ORD-LOCK");
  assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class,()->service.cancelOrder(request,TransitionTrigger.ADMIN));
  verify(orderRepository).lockByOrderId("ORD-LOCK");verify(orderRepository,never()).findByOrderId(anyString());
 }
}
