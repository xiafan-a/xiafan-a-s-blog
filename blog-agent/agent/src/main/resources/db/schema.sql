-- Schema mirrors fastApiProject/database.py (PostgreSQL)
-- Embeddings are stored as JSONB float arrays; similarity is computed in Java.

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    system_prompt TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted    INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_files (
    id              SERIAL PRIMARY KEY,
    knowledge_base_id INTEGER NOT NULL REFERENCES knowledge_bases(id),
    file_name       VARCHAR(512) NOT NULL,
    file_size       INTEGER NOT NULL,
    file_type       VARCHAR(100),
    file_hash       VARCHAR(64) NOT NULL,
    indexing_method VARCHAR(50) DEFAULT 'semantic',
    status          VARCHAR(20) DEFAULT 'pending',
    error_message   TEXT,
    file_metadata   JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id                 SERIAL PRIMARY KEY,
    knowledge_base_id  INTEGER NOT NULL REFERENCES knowledge_bases(id),
    knowledge_file_id  INTEGER REFERENCES knowledge_files(id),
    chunk_index        INTEGER NOT NULL,
    content            TEXT NOT NULL,
    embedding          JSONB,
    chunk_metadata     JSONB DEFAULT '{}'::jsonb,
    indexing_method    VARCHAR(50),
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted         INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id                 SERIAL PRIMARY KEY,
    knowledge_base_id  INTEGER NOT NULL REFERENCES knowledge_bases(id),
    role               VARCHAR(20) NOT NULL,
    content            TEXT NOT NULL,
    session_id         INTEGER,
    parent_message_id  INTEGER,
    context_window     INTEGER DEFAULT 10,
    context_summary    TEXT,
    sources            JSONB DEFAULT '[]'::jsonb,
    token_usage        JSONB DEFAULT '{}'::jsonb,
    feedback           INTEGER,
    message_metadata   JSONB DEFAULT '{}'::jsonb,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted         INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS conversation_session (
    id                SERIAL PRIMARY KEY,
    knowledge_base_id INTEGER NOT NULL REFERENCES knowledge_bases(id),
    title             VARCHAR(255),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted        INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS images (
    id            SERIAL PRIMARY KEY,
    image_name    VARCHAR(256) NOT NULL UNIQUE,
    original_name VARCHAR(512) NOT NULL,
    file_size     INTEGER NOT NULL,
    content_type  VARCHAR(100),
    width         INTEGER,
    height        INTEGER,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted    INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS guess_words (
    id         SERIAL PRIMARY KEY,
    word       VARCHAR(255) NOT NULL,
    hint       TEXT,
    difficulty INTEGER DEFAULT 1,
    is_passed  BOOLEAN DEFAULT FALSE,
    pass_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    embedding  JSONB
);

CREATE TABLE IF NOT EXISTS guess_records (
    id            SERIAL PRIMARY KEY,
    guess_word_id INTEGER NOT NULL REFERENCES guess_words(id),
    guess         VARCHAR(255) NOT NULL,
    similarity    DOUBLE PRECISION NOT NULL,
    is_correct    BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_files_kb ON knowledge_files(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_kb ON knowledge_chunks(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_file ON knowledge_chunks(knowledge_file_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_kb ON conversation_messages(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_session ON conversation_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_conversation_session_kb ON conversation_session(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_guess_records_word ON guess_records(guess_word_id);