package com.yangaobo.dto.ai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UserProfileAiDTOTest {

    @Test
    void profileContractDoesNotExposeSensitiveFields() {
        Set<String> fields = Arrays.stream(UserProfileAiDTO.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertFalse(fields.contains("password"));
        assertFalse(fields.contains("idcard"));
        assertFalse(fields.contains("phone"));
        assertFalse(fields.contains("realname"));
    }
}
