package com.yangaobo.service.ai;

import com.mybatisflex.core.query.QueryChain;
import com.yangaobo.dto.CartInsertDTO;
import com.yangaobo.dto.ai.CartItemAiDTO;
import com.yangaobo.dto.ai.OrderAiDTO;
import com.yangaobo.dto.ai.OrderItemAiDTO;
import com.yangaobo.entity.Cart;
import com.yangaobo.entity.Order;
import com.yangaobo.entity.OrderDetail;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.mapper.CartMapper;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.mapper.OrderMapper;
import com.yangaobo.result.ResultCode;
import com.yangaobo.service.CartService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yangaobo.entity.table.CartTableDef.CART;
import static com.yangaobo.entity.table.OrderDetailTableDef.ORDER_DETAIL;
import static com.yangaobo.entity.table.OrderTableDef.ORDER;

@Service
public class OrderAiQueryService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final CartMapper cartMapper;
    private final CartService cartService;

    public OrderAiQueryService(
            OrderMapper orderMapper,
            OrderDetailMapper orderDetailMapper,
            CartMapper cartMapper,
            CartService cartService) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.cartMapper = cartMapper;
        this.cartService = cartService;
    }

    public List<OrderAiDTO> orders(Long userId, int limit) {
        List<Order> orders = QueryChain.of(orderMapper)
                .where(ORDER.FK_USER_ID.eq(userId))
                .orderBy(ORDER.UPDATED.desc(), ORDER.ID.desc())
                .limit(limit)
                .list();
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderDetail>> detailsByOrder = QueryChain.of(orderDetailMapper)
                .where(ORDER_DETAIL.FK_ORDER_ID.in(orderIds))
                .orderBy(ORDER_DETAIL.FK_ORDER_ID.asc(), ORDER_DETAIL.ID.asc())
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        OrderDetail::getFkOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return orders.stream()
                .map(order -> toOrder(order, detailsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    public List<CartItemAiDTO> cart(Long userId) {
        return QueryChain.of(cartMapper)
                .where(CART.FK_USER_ID.eq(userId))
                .orderBy(CART.CREATED.desc(), CART.ID.desc())
                .list()
                .stream()
                .map(this::toCart)
                .toList();
    }

    public CartItemAiDTO addCartItem(Long userId, Long courseId) {
        CartInsertDTO request = new CartInsertDTO();
        request.setFkUserId(userId);
        request.setFkCourseId(courseId);
        cartService.insert(request);

        Cart cart = QueryChain.of(cartMapper)
                .where(CART.FK_USER_ID.eq(userId))
                .and(CART.FK_COURSE_ID.eq(courseId))
                .orderBy(CART.ID.desc())
                .one();
        if (cart == null) {
            throw new ServiceException(
                    ResultCode.CART_NOT_FOUND,
                    "Cart item was not found after creation");
        }
        return toCart(cart);
    }

    public boolean deleteCartItem(Long userId, Long courseId) {
        return cartService.deleteByUserIdAndCourseIds(userId, List.of(courseId));
    }

    private OrderAiDTO toOrder(Order order, List<OrderDetail> details) {
        List<OrderItemAiDTO> items = details.stream()
                .map(detail -> new OrderItemAiDTO(
                        detail.getFkCourseId(),
                        detail.getCourseTitle(),
                        detail.getCourseCover(),
                        detail.getCoursePrice()))
                .toList();
        return new OrderAiDTO(
                order.getId(),
                order.getSn(),
                order.getTotalAmount(),
                order.getPayAmount(),
                order.getPayType(),
                order.getStatus(),
                order.getInfo(),
                order.getCreated(),
                items);
    }

    private CartItemAiDTO toCart(Cart cart) {
        return new CartItemAiDTO(
                cart.getId(),
                cart.getFkCourseId(),
                cart.getCourseTitle(),
                cart.getCourseCover(),
                cart.getCoursePrice(),
                cart.getCreated());
    }
}
