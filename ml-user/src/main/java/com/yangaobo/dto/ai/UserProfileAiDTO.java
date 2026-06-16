package com.yangaobo.dto.ai;

public record UserProfileAiDTO(
        Long id,
        String username,
        String nickname,
        String email,
        String province,
        String avatar,
        String zodiac,
        String maskedPhone,
        Integer gender,
        Integer age,
        String info
) {
}
