package com.yangaobo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.constant.ML;
import com.yangaobo.dto.CartInsertDTO;
import com.yangaobo.dto.CartPageDTO;
import com.yangaobo.dto.CartUpdateDTO;
import com.yangaobo.entity.*;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.CourseFeign;
import com.yangaobo.feign.UserFeign;
import com.yangaobo.mapper.CartMapper;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.CartService;
import com.yangaobo.service.OrderDetailService;
import com.yangaobo.vo.CartSimpleListVO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.yangaobo.entity.table.CartTableDef.CART;
import static com.yangaobo.entity.table.OrderDetailTableDef.ORDER_DETAIL;
import static com.yangaobo.entity.table.OrderTableDef.ORDER;

/**
 * 购物车表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart>  implements CartService{
    @Resource
    private UserFeign userFeign;
    @Resource
    private CourseFeign courseFeign;

    private OrderDetailMapper orderDetailMapper;

    @Override
    public boolean insert(CartInsertDTO dto) {
        Long fkUserId = SecurityContext.requireUserId();
        dto.setFkUserId(fkUserId);
        Long fkCourseId = dto.getFkCourseId();

        // 购物车记录查重
        // select count(*) from cart where fk_user_id = ? and fk_course_id = ?
        if (QueryChain.of(mapper)
                .where(CART.FK_USER_ID.eq(fkUserId))
                .and(CART.FK_COURSE_ID.eq(fkCourseId))
                .exists()) {
            throw new ServiceException(ResultCode.CART_REPEAT, "购物车记录已重复，" + fkUserId + "用户已经添加了" + fkCourseId + "课程");
        }

        // 组装实体类
        Cart cart = BeanUtil.copyProperties(dto, Cart.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        cart.setUsername(user.getUsername());

        Result<Course> courseResult = courseFeign.select(fkCourseId);
        if (ObjectUtil.isNull(courseResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "课程微服务远程调用失败，请联系管理员。");
        }
        Course course = courseResult.getData();
        if (ObjectUtil.isNull(course)) {
            throw new ServiceException(ResultCode.COURSE_NOT_FOUND, fkCourseId + "号课程数据不存在");
        }
        cart.setCourseTitle(course.getTitle());
        cart.setCourseCover(course.getCover());
        cart.setCoursePrice(BigDecimal.valueOf(course.getPrice()));
        cart.setCreated(LocalDateTime.now());
        cart.setUpdated(LocalDateTime.now());
        // insert into cart (fk_user_id, username, fk_course_id, course_title, course_cover, course_price, created, updated) values (?, ?, ?, ?, ?, ?, ?, ?)
        if (mapper.insert(cart) <= 0) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库添加失败");
        }
        return true;
    }

    @Override
    public Cart select(Long id) {
        // select * from cart where id = ?
        Cart cart = mapper.selectOneWithRelationsById(id);
        if (ObjectUtil.isNull(cart)) {
            throw new ServiceException(ResultCode.CART_NOT_FOUND, "购物车记录不存在");
        }
        SecurityContext.requireOwner(cart.getFkUserId());
        return cart;
    }

    @Override
    public PageVO<Cart> page(CartPageDTO dto) {
        QueryChain<Cart> queryChain = QueryChain.of(mapper);

        // fkUserId条件
        Long fkUserId = SecurityContext.isAdmin() && ObjectUtil.isNotNull(dto.getFkUserId())
                ? dto.getFkUserId()
                : SecurityContext.requireUserId();
        dto.setFkUserId(fkUserId);
        queryChain.where(CART.FK_USER_ID.eq(fkUserId));

        // fkCourseId条件
        Long fkCourseId = dto.getFkCourseId();
        if (ObjectUtil.isNotNull(fkCourseId)) {
            queryChain.where(CART.FK_COURSE_ID.eq(fkCourseId));
        }

        // DB分页并转为VO
        Page<Cart> result = queryChain.withRelations().page(new Page<>(dto.getPageNum(), dto.getPageSize()));
        PageVO<Cart> pageVO = new PageVO<>();
        BeanUtil.copyProperties(result, pageVO);
        pageVO.setPageNum(result.getPageNumber());
        return pageVO;
    }

    @Override
    public boolean update(CartUpdateDTO dto) {
        Long id = dto.getId();
        Cart existing = select(id);
        Long fkUserId = existing.getFkUserId();
        dto.setFkUserId(fkUserId);
        Long fkCourseId = dto.getFkCourseId();

        // 检查购物车记录是否存在
        this.existsById(id);

        // 购物车记录查重
        // select count(*) from cart where fk_user_id = ? and fk_course_id = ? and id <> ?
        if (QueryChain.of(mapper)
                .where(CART.FK_USER_ID.eq(fkUserId))
                .and(CART.FK_COURSE_ID.eq(fkCourseId))
                .and(CART.ID.ne(id))
                .exists()) {
            throw new ServiceException(ResultCode.CART_REPEAT, "购物车记录已重复，" + fkUserId + "用户已经添加了" + fkCourseId + "课程");
        }

        // 组装实体类
        Cart cart = BeanUtil.copyProperties(dto, Cart.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        cart.setUsername(user.getUsername());

        Result<Course> courseResult = courseFeign.select(fkCourseId);
        if (ObjectUtil.isNull(courseResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "课程微服务远程调用失败，请联系管理员。");
        }
        Course course = courseResult.getData();
        if (ObjectUtil.isNull(course)) {
            throw new ServiceException(ResultCode.COURSE_NOT_FOUND, fkUserId + "号课程数据不存在");
        }
        cart.setCourseTitle(course.getTitle());
        cart.setCourseCover(course.getCover());
        cart.setCoursePrice(BigDecimal.valueOf(course.getPrice()));
        cart.setUpdated(LocalDateTime.now());
        // update cart set fk_user_id = ?, username = ?, fk_course_id = ?, course_title = ?, course_cover = ?, course_price = ?, updated = ? where id = ?
        if (!UpdateChain.of(cart)
                .where(CART.ID.eq(cart.getId()))
                .update()) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库更新失败");
        }
        return true;
    }

    @Override
    public boolean delete(Long id) {
        select(id);

        // delete from cart where id = ?
        if (mapper.deleteById(id) <= 0) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        Long userId = SecurityContext.requireUserId();

        // 检查购物车记录是否存在
        // select count(*) from cart where id in (?, ?, ?)
        if (QueryChain.of(mapper)
                .where(CART.ID.in(ids))
                .and(SecurityContext.isAdmin() ? CART.ID.isNotNull() : CART.FK_USER_ID.eq(userId))
                .count() < ids.size()) {
            throw new ServiceException(ResultCode.CART_NOT_FOUND, "至少一个购物车记录不存在");
        }

        // delete from cart where id in (?)
        if (mapper.deleteBatchByIds(ids) <= 0) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public boolean clearByUserId(Long userId) {
        SecurityContext.requireOwner(userId);

        // delete from cart where fk_user_id = ?
        if (!UpdateChain.of(mapper)
                .where(CART.FK_USER_ID.eq(userId))
                .remove()) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public boolean deleteByUserIdAndCourseIds(Long userId, List<Long> courseIds) {
        SecurityContext.requireOwner(userId);

        if (CollUtil.isEmpty(courseIds)) {
            throw new ServiceException(ResultCode.ILLEGAL_PARAM, "课程主键列表不能为空");
        }

        // 根据用户主键和课程主键列表删除购物车记录
        // delete from cart where fk_user_id = ? and fk_course_id in (?)
        if (!UpdateChain.of(mapper)
                .where(CART.FK_USER_ID.eq(userId))
                .and(CART.FK_COURSE_ID.in(courseIds))
                .remove()) {
            throw new ServiceException(ResultCode.MYSQL_ERROR, "数据库删除失败");
        }
        return true;
    }

    /**
     * 按主键检查购物车记录是否存在，如果不存在则直接抛出异常
     *
     * @param id 购物车记录主键
     */
    private void existsById(Long id) {
        // select count(*) from cart where id = ?
        if (!QueryChain.of(mapper)
                .where(CART.ID.eq(id))
                .exists()) {
            throw new ServiceException(ResultCode.CART_NOT_FOUND, id + "号购物车记录不存在");
        }
    }

}
