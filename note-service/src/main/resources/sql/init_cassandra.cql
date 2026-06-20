-- Cassandra: Notes keyspace and tables
CREATE KEYSPACE IF NOT EXISTS notes
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE notes;

CREATE TABLE IF NOT EXISTS note (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    title TEXT,
    content TEXT,
    summary TEXT,
    object_key TEXT,
    status TEXT,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS comment (
    note_id BIGINT,
    comment_id BIGINT,
    user_id BIGINT,
    content TEXT,
    created_at BIGINT,
    PRIMARY KEY (note_id, comment_id)
);

CREATE INDEX IF NOT EXISTS ON note (user_id);
CREATE INDEX IF NOT EXISTS ON note (status);
