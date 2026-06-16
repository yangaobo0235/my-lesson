package com.yangaobo.ai.tool.model;

import java.util.Set;
import java.util.function.Function;

public record BusinessToolSpec<I, O>(
        String name,
        String description,
        Class<I> inputType,
        boolean writeOperation,
        Set<String> allowedRoles,
        Function<I, O> action
) {

    public BusinessToolSpec {
        allowedRoles = allowedRoles == null
                ? Set.of()
                : Set.copyOf(allowedRoles);
    }
}
