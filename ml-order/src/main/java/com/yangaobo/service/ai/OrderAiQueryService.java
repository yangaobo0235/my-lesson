package com.yangaobo.service.ai;

import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.update.UpdateChain;
import com.yangaobo.dto.ai.CartItemAiDTO;
import com.yangaobo.dto.ai.CourseKnowledgeAiDTO;
import com.yangaobo.dto.ai.CourseSummaryAiDTO;
import com.yangaobo.dto.ai.InternalAiResponse;
import com.yangaobo.dto.ai.OrderAiDTO;
import com.yangaobo.dto.ai.OrderItemAiDTO;
import com.yangaobo.dto.ai.UserProfileAiDTO;
import com.yangaobo.entity.Cart;
import com.yangaobo.entity.Order;
import com.yangaobo.entity.OrderDetail;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.CourseAiInternalFeign;
import com.yangaobo.feign.UserAiInternalFeign;
import com.yangaobo.mapper.CartMapper;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.mapper.OrderMapper;
import com.yangaobo.result.ResultCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final UserAiInternalFeign userFeign;
    private final CourseAiInternalFeign courseFeign;

    public OrderAiQueryService(
            OrderMapper orderMapper,
            OrderDetailMapper orderDetailMapper,
            CartMapper cartMapper,
            UserAiInternalFeign userFeign,
            CourseAiInternalFeign courseFeign) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.cartMapper = cartMapper;
        this.userFeign = userFeign;
        this.courseFeign = courseFeign;
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
        Cart existing = QueryChain.of(cartMapper)
                .where(CART.FK_USER_ID.eq(userId))
                .and(CART.FK_COURSE_ID.eq(courseId))
                .orderBy(CART.ID.desc())
                .one();
        if (existing != null) {
            return toCart(existing);
        }

        UserProfileAiDTO user = user(userId);
        CourseKnowledgeAiDTO course = course(courseId);
        CourseSummaryAiDTO summary = courseSummary(course);

        Cart item = new Cart();
        item.setFkUserId(userId);
        item.setUsername(user.username());
        item.setFkCourseId(courseId);
        item.setCourseTitle(course.title());
        item.setCourseCover(summary == null ? "" : summary.cover());
        item.setCoursePrice(BigDecimal.valueOf(
                summary == null || summary.price() == null
                        ? 0.0
                        : summary.price()));
        item.setCreated(LocalDateTime.now());
        item.setUpdated(LocalDateTime.now());
        if (cartMapper.insert(item) <= 0) {
            throw new ServiceException(
                    ResultCode.MYSQL_ERROR,
                    "AI internal cart insert failed");
        }

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
        return UpdateChain.of(cartMapper)
                .where(CART.FK_USER_ID.eq(userId))
                .and(CART.FK_COURSE_ID.eq(courseId))
                .remove();
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

    private UserProfileAiDTO user(Long userId) {
        InternalAiResponse<UserProfileAiDTO> result =
                userFeign.profile(userId);
        if (result == null || !result.successful()
                || result.data() == null) {
            throw new ServiceException(
                    ResultCode.USER_NOT_FOUND,
                    userId + "号用户数据不存在");
        }
        return result.data();
    }

    private CourseKnowledgeAiDTO course(Long courseId) {
        InternalAiResponse<CourseKnowledgeAiDTO> result =
                courseFeign.getCourse(courseId);
        if (result == null || !result.successful()
                || result.data() == null) {
            throw new ServiceException(
                    ResultCode.COURSE_NOT_FOUND,
                    courseId + "号课程数据不存在");
        }
        return result.data();
    }

    private CourseSummaryAiDTO courseSummary(CourseKnowledgeAiDTO course) {
        InternalAiResponse<List<CourseSummaryAiDTO>> result =
                courseFeign.search(course.title(), 5);
        if (result == null || !result.successful()
                || result.data() == null) {
            return null;
        }
        return result.data().stream()
                .filter(candidate -> course.id().equals(candidate.id()))
                .findFirst()
                .orElse(null);
    }
}
