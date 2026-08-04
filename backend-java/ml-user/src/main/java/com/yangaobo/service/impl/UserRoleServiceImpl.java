package com.yangaobo.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.entity.UserRole;
import com.yangaobo.mapper.UserRoleMapper;
import com.yangaobo.service.UserRoleService;
import org.springframework.stereotype.Service;

/**
 * 用户角色关系表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole>  implements UserRoleService{

}
