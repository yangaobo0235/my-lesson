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
                    excerpt(hit.snippet())));
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
        availableCitations.forEach(
                citation -> validIndexes.add(citation.index()));
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
        return new ValidatedAnswer(
                sanitized.toString().replaceAll("[ \\t]{2,}", " ").trim(),
                citations);
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
            List<Citation> citations
    ) {
    }
}
