package com.yangaobo.security;

import com.yangaobo.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityContextTest {

    @AfterEach
    void clearContext() {
        SecurityContext.clear();
    }

    @Test
    void ownerCanAccessOwnResource() {
        SecurityContext.set(new AuthenticatedPrincipal(7L, "student", List.of("学生")));
        assertDoesNotThrow(() -> SecurityContext.requireOwner(7L));
    }

    @Test
    void ordinaryUserCannotAccessAnotherUsersResource() {
        SecurityContext.set(new AuthenticatedPrincipal(7L, "student", List.of("学生")));
        assertThrows(ServiceException.class, () -> SecurityContext.requireOwner(8L));
    }

    @Test
    void administratorCanManageAnotherUsersResource() {
        SecurityContext.set(new AuthenticatedPrincipal(1L, "admin", List.of("管理员")));
        assertDoesNotThrow(() -> SecurityContext.requireOwner(8L));
    }
}
