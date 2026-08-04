package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.MenuInsertDTO;
import com.yangaobo.dto.MenuPageDTO;
import com.yangaobo.dto.MenuUpdateDTO;
import com.yangaobo.entity.Menu;
import com.yangaobo.vo.MenuSimpleListVO;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 菜单表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface MenuService extends IService<Menu> {
    boolean insert(MenuInsertDTO dto);
    Menu select(Long id);
    List<MenuSimpleListVO> simpleList();
    PageVO<Menu> page(MenuPageDTO dto);
    boolean update(MenuUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 按角色主键修改该角色的菜单列表
     *
     * @param roleId  角色主键
     * @param menuIds 菜单主键列表
     */
    boolean updateMenusByRoleId(Long roleId, List<Long> menuIds);


}
