package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.RoleInsertDTO;
import com.yangaobo.dto.RolePageDTO;
import com.yangaobo.dto.RoleUpdateDTO;
import com.yangaobo.entity.Role;
import com.yangaobo.vo.PageVO;
import com.yangaobo.vo.RoleSimpleListVO;

import java.util.List;

/**
 * 角色表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface RoleService extends IService<Role> {
    boolean insert(RoleInsertDTO dto);
    Role select(Long id);
    List<RoleSimpleListVO> simpleList();
    PageVO<Role> page(RolePageDTO dto);
    boolean update(RoleUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);
    /**
     * 按用户主键查询该用户的全部角色ID列表
     *
     * @param userId 用户主键
     * @return 该用户的全部角色ID列表
     */
    List<Long> listRoleIdsByUserId(Long userId);

    /**
     * 按用户主键修改该用户的角色列表
     *
     * @param userId  用户主键
     * @param roleIds 角色主键列表
     */
    boolean updateRolesByUserId(Long userId, List<Long> roleIds);



}
