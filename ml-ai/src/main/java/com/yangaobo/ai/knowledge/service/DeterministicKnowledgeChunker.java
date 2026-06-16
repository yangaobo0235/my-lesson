package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.model.KnowledgeChunk;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicKnowledgeChunker {

    static final int TARGET_SIZE = 600;
    static final int MAX_SIZE = 1000;
    static final int OVERLAP_SIZE = 100;

    public List<KnowledgeChunk> chunk(KnowledgeDocument source) {
        String prefix = prefix(source);
        int bodyLimit = Math.max(100, MAX_SIZE - prefix.length() - 2);
        int contentBodyLimit = Math.max(100, bodyLimit - OVERLAP_SIZE - 1);
        int bodyTarget = Math.min(
                contentBodyLimit,
                Math.max(100, TARGET_SIZE - prefix.length() - 2));
        String content = normalize(source.content());
        if (content.isBlank()) {
            content = source.title();
        }

        List<String> units = splitIntoUnits(content, contentBodyLimit);
        List<String> bodies = mergeUnits(
                units,
                bodyTarget,
                contentBodyLimit);
        List<KnowledgeChunk> chunks = new ArrayList<>(bodies.size());
        for (int index = 0; index < bodies.size(); index++) {
            String body = bodies.get(index);
            if (index > 0) {
                String previous = bodies.get(index - 1);
                String overlap = previous.substring(
                        Math.max(0, previous.length() - OVERLAP_SIZE));
                body = overlap + "\n" + body;
            }
            String chunkContent = prefix + "\n\n" + body.trim();
            chunks.add(new KnowledgeChunk(index, chunkContent));
        }
        return List.copyOf(chunks);
    }

    private List<String> splitIntoUnits(String content, int bodyLimit) {
        String[] paragraphs = content.split("\\n\\s*\\n|(?<=。|！|？|；)\\s*");
        List<String> units = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isEmpty()) {
                continue;
            }
            int start = 0;
            while (start < value.length()) {
                int end = Math.min(value.length(), start + bodyLimit);
                units.add(value.substring(start, end));
                start = end;
            }
        }
        if (units.isEmpty()) {
            units.add(content);
        }
        return units;
    }

    private List<String> mergeUnits(
            List<String> units,
            int bodyTarget,
            int bodyLimit) {
        List<String> bodies = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            int separatorLength = current.isEmpty() ? 0 : 2;
            if (!current.isEmpty()
                    && current.length() + separatorLength + unit.length() > bodyLimit) {
                bodies.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(unit);
            if (current.length() >= bodyTarget) {
                bodies.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            if (!bodies.isEmpty()
                    && bodies.get(bodies.size() - 1).length()
                    + 2
                    + current.length() <= bodyLimit) {
                int last = bodies.size() - 1;
                bodies.set(last, bodies.get(last) + "\n\n" + current);
            } else {
                bodies.add(current.toString());
            }
        }
        return bodies;
    }

    private String prefix(KnowledgeDocument source) {
        StringBuilder result = new StringBuilder()
                .append("标题：")
                .append(source.title());
        Object author = source.metadata().get("author");
        Object category = source.metadata().get("category");
        if (author != null) {
            result.append("\n作者：").append(author);
        }
        if (category != null) {
            result.append("\n分类：").append(category);
        }
        return result.toString();
    }

    private String normalize(String content) {
        String withoutBlockTags = content
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li)>", "\n");
        String withoutTags = withoutBlockTags.replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
