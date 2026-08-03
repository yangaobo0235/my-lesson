package com.yangaobo.ai.observability.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunTimeline(
        UUID runId,
        String status,
        String intent,
        String profileName,
        String profileVersion,
        String promptVersion,
        Double routeConfidence,
        boolean conservativeMode,
        int modelCallCount,
        int toolCallCount,
        String terminationReason,
        Instant createdAt,
        Instant finishedAt,
        List<Event> events
) {

    public RunTimeline {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public record Event(
            String type,
            String name,
            String status,
            Long latencyMs,
            Instant timestamp
    ) {
    }
}
