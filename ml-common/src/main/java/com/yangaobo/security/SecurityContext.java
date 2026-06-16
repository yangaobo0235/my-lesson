package com.yangaobo.security;

import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;

public final class SecurityContext {
    private static final ThreadLocal<AuthenticatedPrincipal> CURRENT = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static void set(AuthenticatedPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthenticatedPrincipal requirePrincipal() {
        AuthenticatedPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "请求缺少可信身份");
        }
        return principal;
    }

    public static Long requireUserId() {
        return requirePrincipal().id();
    }

    public static boolean isAdmin() {
        return requirePrincipal().hasAnyRole(SecurityRoles.ADMIN_ROLES);
    }

    public static void requireOwner(Long userId) {
        AuthenticatedPrincipal principal = requirePrincipal();
        if (!principal.id().equals(userId) && !principal.hasAnyRole(SecurityRoles.ADMIN_ROLES)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权访问其他用户的数据");
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
