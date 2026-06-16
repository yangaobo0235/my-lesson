package com.yangaobo.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    private int vectorTopK = 12;
    private double vectorSimilarityThreshold = 0.55;
    private int keywordTopK = 12;
    private int fusionTopK = 20;
    private int answerTopN = 6;
    private double minimumRelevantScore = 0.55;
    private double strongRelevantScore = 0.75;
    private int citationExcerptLength = 300;
    private String sourceBaseUrl = "http://127.0.0.1:24101";
    private final Rerank rerank = new Rerank();

    public int getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(int vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public double getVectorSimilarityThreshold() {
        return vectorSimilarityThreshold;
    }

    public void setVectorSimilarityThreshold(double vectorSimilarityThreshold) {
        this.vectorSimilarityThreshold = vectorSimilarityThreshold;
    }

    public int getKeywordTopK() {
        return keywordTopK;
    }

    public void setKeywordTopK(int keywordTopK) {
        this.keywordTopK = keywordTopK;
    }

    public int getFusionTopK() {
        return fusionTopK;
    }

    public void setFusionTopK(int fusionTopK) {
        this.fusionTopK = fusionTopK;
    }

    public int getAnswerTopN() {
        return answerTopN;
    }

    public void setAnswerTopN(int answerTopN) {
        this.answerTopN = answerTopN;
    }

    public double getMinimumRelevantScore() {
        return minimumRelevantScore;
    }

    public void setMinimumRelevantScore(double minimumRelevantScore) {
        this.minimumRelevantScore = minimumRelevantScore;
    }

    public double getStrongRelevantScore() {
        return strongRelevantScore;
    }

    public void setStrongRelevantScore(double strongRelevantScore) {
        this.strongRelevantScore = strongRelevantScore;
    }

    public int getCitationExcerptLength() {
        return citationExcerptLength;
    }

    public void setCitationExcerptLength(int citationExcerptLength) {
        this.citationExcerptLength = citationExcerptLength;
    }

    public String getSourceBaseUrl() {
        return sourceBaseUrl;
    }

    public void setSourceBaseUrl(String sourceBaseUrl) {
        this.sourceBaseUrl = sourceBaseUrl;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public static class Rerank {

        private boolean enabled;
        private String model = "qwen3-rerank";
        private String endpoint =
                "https://dashscope.aliyuncs.com/api/v1/services/"
                        + "rerank/text-rerank/text-rerank";
        private int topN = 8;
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 10000;
        private String instruct =
                "根据用户问题判断候选资料的相关性，优先保留能直接支持答案的资料。";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        public String getInstruct() {
            return instruct;
        }

        public void setInstruct(String instruct) {
            this.instruct = instruct;
        }
    }
}
