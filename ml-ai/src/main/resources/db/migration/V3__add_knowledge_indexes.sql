-- Indexes used by knowledge rebuild and replacement queries.
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_source_status
    ON ai_knowledge_source(status);

CREATE INDEX IF NOT EXISTS idx_vector_store_source
    ON vector_store (
        (metadata->>'source_type'),
        (metadata->>'source_id')
    );

COMMENT ON INDEX idx_ai_knowledge_source_status
    IS 'Speeds up active knowledge source status queries';

COMMENT ON INDEX idx_vector_store_source
    IS 'Speeds up replacement and counting of vector chunks by business source';
