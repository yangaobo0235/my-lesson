package com.yangaobo.security;

import java.util.List;

public record AuthenticatedPrincipal(Long id, String username, List<String> roles) {
    public boolean hasAnyRole(List<String> allowedRoles) {
        return roles.stream().anyMatch(role ->
                allowedRoles.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(role)));
    }
}
