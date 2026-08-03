package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.config.BusinessToolProperties;
import com.yangaobo.ai.tool.dto.SearchCoursesRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseToolsTest {

    @Test
    void shouldTrimKeywordAndClampLimit() {
        AiBusinessGateway gateway = mock(AiBusinessGateway.class);
        BusinessToolProperties properties = new BusinessToolProperties();
        CourseTools tools = new CourseTools(gateway, properties);
        when(gateway.searchCourses("摄影", 10))
                .thenReturn(List.of());

        assertThat(tools.searchCourses(
                new SearchCoursesRequest("  摄影  ", 99)))
                .isEmpty();
        verify(gateway).searchCourses("摄影", 10);
    }
}
