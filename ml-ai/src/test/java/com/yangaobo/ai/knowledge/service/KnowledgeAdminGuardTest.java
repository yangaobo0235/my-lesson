package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.exception.AdminAccessDeniedException;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeAdminGuardTest {

    private final KnowledgeAdminGuard guard =
            new KnowledgeAdminGuard("管理员,ADMIN");

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldAllowConfiguredAdminRole() {
        UserContext.set(new AuthenticatedUser(
                1L,
                "admin",
                List.of("管理员")));

        assertThatCode(guard::requireAdmin).doesNotThrowAnyException();
    }

    @Test
    void shouldMatchAdminRoleIgnoringCase() {
        UserContext.set(new AuthenticatedUser(
                1L,
                "admin",
                List.of("admin")));

        assertThatCode(guard::requireAdmin).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNonAdminRole() {
        UserContext.set(new AuthenticatedUser(
                2L,
                "student",
                List.of("学生")));

        assertThatThrownBy(guard::requireAdmin)
                .isInstanceOf(AdminAccessDeniedException.class);
    }
}
