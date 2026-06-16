package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.exception.AdminAccessDeniedException;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KnowledgeAdminGuard {

    private final Set<String> adminRoles;

    public KnowledgeAdminGuard(
            @Value("${ai.knowledge.admin-roles:管理员,超级管理员,ADMIN,ROLE_ADMIN}")
            String configuredRoles) {
        this.adminRoles = Arrays.stream(configuredRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void requireAdmin() {
        AuthenticatedUser user = UserContext.requireUser();
        boolean administrator = user.roles().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(adminRoles::contains);
        if (!administrator) {
            throw new AdminAccessDeniedException();
        }
    }

    public Set<String> allowedRoles() {
        return adminRoles;
    }
}
