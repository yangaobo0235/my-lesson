package com.yangaobo.ai.rag.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KeywordScoreCalculator {

    private static final Pattern LATIN_TERM =
            Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CHINESE_TERM =
            Pattern.compile("[\\u4e00-\\u9fff]+");

    public double score(String query, String title, String snippet, int rank) {
        String normalizedQuery = normalize(query);
        String normalizedTitle = normalize(title);
        String normalizedSnippet = normalize(snippet);
        if (normalizedQuery.isBlank()) {
            return 0.0;
        }

        double score = 0.0;
        if (normalizedTitle.equals(normalizedQuery)) {
            score = 1.0;
        } else if (normalizedTitle.contains(normalizedQuery)) {
            score = 0.9;
        } else if (normalizedSnippet.contains(normalizedQuery)) {
            score = 0.8;
        } else {
            Set<String> queryTerms = terms(normalizedQuery);
            Set<String> contentTerms = terms(
                    normalizedTitle + " " + normalizedSnippet);
            long matches = queryTerms.stream()
                    .filter(contentTerms::contains)
                    .count();
            if (!queryTerms.isEmpty()) {
                score = 0.35 + 0.45 * matches / queryTerms.size();
            }
        }
        double rankPenalty = Math.min(0.15, Math.max(0, rank - 1) * 0.01);
        return Math.max(0.0, Math.min(1.0, score - rankPenalty));
    }

    Set<String> terms(String text) {
        Set<String> result = new LinkedHashSet<>();
        String normalized = normalize(text);
        Matcher latinMatcher = LATIN_TERM.matcher(normalized);
        while (latinMatcher.find()) {
            result.add(latinMatcher.group());
        }

        Matcher chineseMatcher = CHINESE_TERM.matcher(normalized);
        while (chineseMatcher.find()) {
            String chinese = chineseMatcher.group();
            if (chinese.length() == 1) {
                result.add(chinese);
            }
            for (int index = 0; index + 1 < chinese.length(); index++) {
                result.add(chinese.substring(index, index + 2));
            }
        }
        return result;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).trim();
    }
}
