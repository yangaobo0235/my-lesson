package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.Citation;
import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CitationService {

    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("\\[(\\d+)]");

    private final RagProperties properties;

    public CitationService(RagProperties properties) {
        this.properties = properties;
    }

    public List<Citation> create(List<SearchHit> hits) {
        List<Citation> citations = new ArrayList<>(hits.size());
        for (int index = 0; index < hits.size(); index++) {
            SearchHit hit = hits.get(index);
            citations.add(new Citation(
                    index + 1,
                    hit.sourceType(),
                    hit.sourceId(),
                    hit.title(),
                    hit.sourceUrl(),
                    excerpt(hit.snippet()),
                    hit.version(),
                    hit.visibilityStatus()));
        }
        return List.copyOf(citations);
    }

    public ValidatedAnswer validate(
            String answer,
            List<Citation> availableCitations) {
        if (answer == null || answer.isBlank()) {
            return new ValidatedAnswer("", List.of());
        }
        Set<Integer> validIndexes = new LinkedHashSet<>();
        availableCitations.stream()
                .filter(this::visible)
                .forEach(citation -> validIndexes.add(citation.index()));
        Set<Integer> referenced = new LinkedHashSet<>();

        Matcher matcher = REFERENCE_PATTERN.matcher(answer);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (validIndexes.contains(index)) {
                referenced.add(index);
                matcher.appendReplacement(
                        sanitized,
                        Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(sanitized, "");
            }
        }
        matcher.appendTail(sanitized);
        List<Citation> citations = availableCitations.stream()
                .filter(citation -> referenced.contains(citation.index()))
                .toList();
        String sanitizedAnswer = sanitized.toString()
                .replaceAll("[ \\t]{2,}", " ").trim();
        return new ValidatedAnswer(
                sanitizedAnswer,
                citations,
                factMappings(sanitizedAnswer, referenced));
    }

    private boolean visible(Citation citation) {
        return citation.sourceId() != null
                && !citation.sourceId().isBlank()
                && citation.version() > 0
                && Set.of("ACTIVE", "PUBLISHED")
                        .contains(citation.visibilityStatus());
    }

    private List<FactCitation> factMappings(
            String answer,
            Set<Integer> referenced) {
        if (answer.isBlank() || referenced.isEmpty()) {
            return List.of();
        }
        List<FactCitation> mappings = new ArrayList<>();
        for (String sentence : answer.split("(?<=[。！？!?])")) {
            Matcher matcher = REFERENCE_PATTERN.matcher(sentence);
            List<Integer> indexes = new ArrayList<>();
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1));
                if (referenced.contains(index)) {
                    indexes.add(index);
                }
            }
            if (!indexes.isEmpty()) {
                mappings.add(new FactCitation(sentence.trim(), indexes));
            }
        }
        return List.copyOf(mappings);
    }

    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int limit = properties.getCitationExcerptLength();
        return normalized.length() <= limit
                ? normalized
                : normalized.substring(0, limit) + "...";
    }

    public record ValidatedAnswer(
            String answer,
            List<Citation> citations,
            List<FactCitation> factCitations
    ) {

        public ValidatedAnswer(String answer, List<Citation> citations) {
            this(answer, citations, List.of());
        }
    }

    public record FactCitation(
            String fact,
            List<Integer> citationIndexes
    ) {
        public FactCitation {
            citationIndexes = List.copyOf(citationIndexes);
        }
    }
}
