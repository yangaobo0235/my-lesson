package com.yangaobo.ai.security;

import java.util.List;

public record AuthenticatedUser(
        Long id,
        String username,
        List<String> roles
) {

    public boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        String expected = role.trim();
        return roles.stream()
                .filter(candidate -> candidate != null)
                .map(String::trim)
                .anyMatch(candidate ->
                        candidate.equalsIgnoreCase(expected));
    }
}
