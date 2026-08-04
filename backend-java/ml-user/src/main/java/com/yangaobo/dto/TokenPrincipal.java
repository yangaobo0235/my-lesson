package com.yangaobo.dto;

import java.util.List;

public record TokenPrincipal(
        Long id,
        String username,
        String nickname,
        List<String> roles
) {
}
