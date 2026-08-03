package com.yangaobo.ai.observability.controller;

import com.yangaobo.ai.observability.model.RunTimeline;
import com.yangaobo.ai.observability.repository.RunTimelineRepository;
import com.yangaobo.ai.rag.model.RetrievalTrace;
import com.yangaobo.ai.rag.repository.RetrievalTraceRepository;
import com.yangaobo.ai.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/runs")
public class RunObservabilityController {

    private final RunTimelineRepository timelineRepository;
    private final RetrievalTraceRepository retrievalTraceRepository;

    public RunObservabilityController(
            RunTimelineRepository timelineRepository,
            RetrievalTraceRepository retrievalTraceRepository) {
        this.timelineRepository = timelineRepository;
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    @GetMapping("/{runId}/timeline")
    public RunTimeline timeline(@PathVariable UUID runId) {
        return timelineRepository.findOwned(
                runId, UserContext.requireUser().id());
    }

    @GetMapping("/{runId}/retrieval-trace")
    public List<RetrievalTrace> retrievalTrace(@PathVariable UUID runId) {
        Long userId = UserContext.requireUser().id();
        timelineRepository.findOwned(runId, userId);
        return retrievalTraceRepository.findByRunOwned(runId, userId);
    }
}
