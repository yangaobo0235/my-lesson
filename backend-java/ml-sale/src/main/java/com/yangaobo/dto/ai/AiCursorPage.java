package com.yangaobo.dto.ai;

import java.util.List;
import java.util.function.ToLongFunction;

public record AiCursorPage<T>(
        List<T> items,
        Long nextCursor,
        boolean hasMore
) {

    public static <T> AiCursorPage<T> of(
            List<T> fetched,
            int requestedSize,
            ToLongFunction<T> idExtractor) {
        boolean hasMore = fetched.size() > requestedSize;
        List<T> items = hasMore
                ? List.copyOf(fetched.subList(0, requestedSize))
                : List.copyOf(fetched);
        Long nextCursor = items.isEmpty()
                ? null
                : idExtractor.applyAsLong(items.get(items.size() - 1));
        return new AiCursorPage<>(items, nextCursor, hasMore);
    }
}
