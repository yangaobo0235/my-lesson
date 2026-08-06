package com.yangaobo.search.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

@Document(
        indexName = "mylesson-knowledge-chunk-v1",
        createIndex = false,
        writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "/elasticsearch/knowledge-settings.json")
public class KnowledgeChunkDocument {

    @Id
    @Field(name = "chunk_id", type = FieldType.Keyword)
    private String chunkId;

    @Field(name = "source_type", type = FieldType.Keyword)
    private String sourceType;

    @Field(name = "source_id", type = FieldType.Keyword)
    private String sourceId;

    @Field(type = FieldType.Text, analyzer = "mylesson_ik_max", searchAnalyzer = "mylesson_ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "mylesson_ik_max", searchAnalyzer = "mylesson_ik_smart")
    private String content;

    @Field(name = "source_url", type = FieldType.Keyword)
    private String sourceUrl;

    @Field(name = "content_version", type = FieldType.Long)
    private long contentVersion;

    @Field(name = "chunk_index", type = FieldType.Integer)
    private int chunkIndex;

    @Field(name = "content_hash", type = FieldType.Keyword)
    private String contentHash;

    @Field(type = FieldType.Keyword)
    private String status;

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(long contentVersion) {
        this.contentVersion = contentVersion;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
