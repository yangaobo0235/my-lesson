package com.yangaobo.dto.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCursorPageTest {

    @Test
    void cursorPagesDoNotRepeatOrSkipItems() {
        AiCursorPage<Item> first = AiCursorPage.of(
                List.of(new Item(1), new Item(2), new Item(3)),
                2,
                Item::id);
        AiCursorPage<Item> second = AiCursorPage.of(
                List.of(new Item(3), new Item(4)),
                2,
                Item::id);

        assertEquals(List.of(1L, 2L), ids(first.items()));
        assertEquals(2L, first.nextCursor());
        assertTrue(first.hasMore());
        assertEquals(List.of(3L, 4L), ids(second.items()));
        assertEquals(List.of(1L, 2L, 3L, 4L),
                List.of(
                        first.items().get(0).id(),
                        first.items().get(1).id(),
                        second.items().get(0).id(),
                        second.items().get(1).id()));
    }

    @Test
    void emptyPageReturnsEmptyItemsAndNoCursor() {
        AiCursorPage<Item> page = AiCursorPage.of(List.of(), 10, Item::id);

        assertTrue(page.items().isEmpty());
        assertNull(page.nextCursor());
        assertFalse(page.hasMore());
    }

    private List<Long> ids(List<Item> items) {
        return items.stream().map(Item::id).toList();
    }

    private record Item(long id) {
    }
}
