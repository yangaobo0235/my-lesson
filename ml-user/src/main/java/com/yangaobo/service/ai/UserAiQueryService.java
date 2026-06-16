package com.yangaobo.service.ai;

import cn.hutool.core.util.DesensitizedUtil;
import com.mybatisflex.core.query.QueryChain;
import com.yangaobo.dto.ai.UserProfileAiDTO;
import com.yangaobo.dto.ai.UserRoleAiDTO;
import com.yangaobo.entity.User;
import com.yangaobo.mapper.RoleMapper;
import com.yangaobo.service.RoleService;
import com.yangaobo.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.yangaobo.entity.table.RoleTableDef.ROLE;

@Service
public class UserAiQueryService {

    private final UserService userService;
    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public UserAiQueryService(
            UserService userService,
            RoleService roleService,
            RoleMapper roleMapper) {
        this.userService = userService;
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    public UserProfileAiDTO profile(Long userId) {
        User user = userService.select(userId);
        return new UserProfileAiDTO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getProvince(),
                user.getAvatar(),
                user.getZodiac(),
                maskPhone(user.getPhone()),
                user.getGender(),
                user.getAge(),
                user.getInfo());
    }

    public List<UserRoleAiDTO> roles(Long userId) {
        userService.select(userId);
        List<Long> roleIds = roleService.listRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return QueryChain.of(roleMapper)
                .where(ROLE.ID.in(roleIds))
                .orderBy(ROLE.IDX.asc(), ROLE.ID.asc())
                .list()
                .stream()
                .map(role -> new UserRoleAiDTO(role.getId(), role.getTitle()))
                .toList();
    }

    private String maskPhone(String phone) {
        return phone == null || phone.isBlank()
                ? phone
                : DesensitizedUtil.mobilePhone(phone);
    }
}
