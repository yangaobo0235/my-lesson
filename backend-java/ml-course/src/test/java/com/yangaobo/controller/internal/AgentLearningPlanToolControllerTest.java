package com.yangaobo.controller.internal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLearningPlanToolControllerTest {

    @Test
    void pathVariableParameterNamesAreAvailableToSpring() {
        boolean draftIdPresent = Arrays.stream(
                        AgentLearningPlanToolController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("confirm"))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .anyMatch(parameter -> parameter.isNamePresent()
                        && parameter.getName().equals("draftId"));

        assertTrue(draftIdPresent);
    }
}
