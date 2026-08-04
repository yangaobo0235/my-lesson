package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.CartInsertDTO;
import com.yangaobo.dto.CartPageDTO;
import com.yangaobo.dto.CartUpdateDTO;
import com.yangaobo.entity.Cart;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 购物车表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface CartService extends IService<Cart> {
    boolean insert(CartInsertDTO dto);
    Cart select(Long id);
//    List<CartSimpleListVO> simpleList();
    PageVO<Cart> page(CartPageDTO dto);
    boolean update(CartUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);
    /**
     * 根据用户主键清空购物车记录
     *
     * @param userId 用户主键
     * @return true 成功，false 失败
     */
    boolean clearByUserId(Long userId);
    /**
     * 根据用户主键和课程主键列表删除购物车记录
     *
     * @param userId    用户主键
     * @param courseIds 课程主键列表
     * @return true 成功，false 失败
     */
    boolean deleteByUserIdAndCourseIds(Long userId, List<Long> courseIds);

    /**
     * 创建预支付订单记录（未支付）
     *
     * @param dto 预支付DTO实体类
     * @return 返回订单的 SN
     */
//    String prePay(PrePayDTO dto);



}
