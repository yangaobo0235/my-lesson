package com.yangaobo.component;

import cn.hutool.core.util.ObjectUtil;
import com.yangaobo.constant.ML;
import com.yangaobo.dto.OrderMessage;
import com.yangaobo.entity.Course;
import com.yangaobo.entity.Order;
import com.yangaobo.entity.OrderDetail;
import com.yangaobo.entity.User;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.CourseFeign;
import com.yangaobo.feign.UserFeign;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.mapper.OrderMapper;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * @author 杨奥博
 */
@Slf4j
@Component
@RocketMQMessageListener(
        // 消费者组名
        consumerGroup = "ml-consumer-group",
        // 监听的主题名
        topic = "ml-topic",
        // 监听的标签名，默认为 `*` 表示监听全部标签
        selectorExpression = "ml-tag",
        // 并发模式CONCURRENTLY: 同组消费者并发消费消息，默认值
        // 顺序模式ORDERLY: 同组消费者按顺序依次消费消息
        consumeMode = ConsumeMode.CONCURRENTLY,
        // 集群模式CLUSTERING: 同组消费者平均分摊消费消息，每人只需消费部分消息，默认值
        // 广播模式BROADCASTING: 同组消费者每人都要将全部消息消费一遍
        messageModel = MessageModel.CLUSTERING)
public class OrderMessageListener implements RocketMQListener<OrderMessage> {

    @Resource
    private UserFeign userFeign;
    @Resource
    private CourseFeign courseFeign;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private OrderDetailMapper orderDetailMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private MyRedis redis;

    /**
     * 该方法在Broker投递消息时触发并执行
     *
     * @param orderMessage 消息内容
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(OrderMessage orderMessage) {

        if (orderMessage == null || orderMessage.getRequestId() == null) {
            throw new IllegalArgumentException("Seckill order requestId is required");
        }
        if (!reserve(orderMessage)) {
            log.info("重复秒杀订单消息已幂等忽略, requestId={}",
                    orderMessage.getRequestId());
            acknowledgeAfterCommit(orderMessage);
            return;
        }

        Long fkUserId = orderMessage.getFkUserId();
        Long fkCourseId = orderMessage.getFkCourseId();
        BigDecimal skPrice = BigDecimal.valueOf(orderMessage.getSkPrice());
        BigDecimal price = BigDecimal.valueOf(orderMessage.getPrice());

        log.info("{} 号用户成功秒杀到了 {} 号课程，共花费 {} 元", fkUserId, fkCourseId, skPrice);

        // 准备实体类
        Order order = new Order();
        order.setSn("SK" + orderMessage.getRequestId()
                .toString().replace("-", ""));
        order.setTotalAmount(price);
        order.setPayAmount(skPrice);
        order.setPayType(ML.Order.NO_PAY);
        order.setStatus(ML.Order.UNPAID);
        order.setFkUserId(fkUserId);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        order.setUsername(user.getUsername());
        order.setInfo("通过秒杀活动下单");
        order.setCreated(LocalDateTime.now());
        order.setUpdated(LocalDateTime.now());

        // DB添加订单表记录
        if (orderMapper.insert(order) <= 0) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库添加订单失败");
        }

        // 添加订单明细表记录
        Long orderId = order.getId();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setFkCourseId(fkCourseId);
        orderDetail.setFkOrderId(orderId);
        Result<Course> courseResult = courseFeign.select(fkCourseId);
        if (ObjectUtil.isNull(courseResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "课程微服务远程调用失败，请联系管理员。");
        }
        Course course = courseResult.getData();
        if (ObjectUtil.isNull(course)) {
            throw new ServiceException(ResultCode.COURSE_NOT_FOUND, fkCourseId + "号课程数据不存在");
        }
        orderDetail.setCourseTitle(course.getTitle());
        orderDetail.setCourseCover(course.getCover());
        orderDetail.setCoursePrice(BigDecimal.valueOf(course.getPrice()));
        orderDetail.setCreated(LocalDateTime.now());
        orderDetail.setUpdated(LocalDateTime.now());
        if (orderDetailMapper.insert(orderDetail) <= 0) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库添加订单明细失败");
        }
        jdbcTemplate.update(
                """
                UPDATE seckill_order_consume
                SET status = 'SUCCEEDED', order_id = ?, updated_at = now()
                WHERE request_id = ?
                """,
                orderId,
                orderMessage.getRequestId().toString());
        acknowledgeAfterCommit(orderMessage);
    }

    private void acknowledgeAfterCommit(OrderMessage message) {
        Runnable acknowledgement = () -> acknowledgeReservation(message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            acknowledgement.run();
                        }
                    });
            return;
        }
        acknowledgement.run();
    }

    private void acknowledgeReservation(OrderMessage message) {
        if (redis == null) {
            return;
        }
        String qualificationKey = ML.Redis.SECKILL_QUALIFICATION_PREFIX
                + message.getFkSeckillId() + ':'
                + message.getFkCourseId() + ':'
                + message.getFkUserId();
        String requestId = message.getRequestId().toString();
        String existing = redis.get(qualificationKey);
        if (existing == null) {
            redis.setNxEx(qualificationKey, requestId, 24, TimeUnit.HOURS);
        } else if (!requestId.equals(existing)) {
            log.error(
                    "秒杀资格与订单请求不一致, qualificationKey={}, expected={}, actual={}",
                    qualificationKey,
                    requestId,
                    existing);
            return;
        }
        redis.zRem(ML.Redis.SECKILL_RECONCILE_PENDING_KEY, requestId);
    }

    private boolean reserve(OrderMessage message) {
        return jdbcTemplate.update(
                """
                INSERT IGNORE INTO seckill_order_consume (
                    request_id, qualification_id, seckill_id,
                    user_id, course_id, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PROCESSING', now(), now())
                """,
                message.getRequestId().toString(),
                message.getQualificationId() == null
                        ? message.getRequestId().toString()
                        : message.getQualificationId().toString(),
                message.getFkSeckillId(),
                message.getFkUserId(),
                message.getFkCourseId()) == 1;
    }
}
