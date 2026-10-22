CREATE TABLE IF NOT EXISTS file_metadata (
 file_id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, note_id BIGINT NOT NULL,
 object_key VARCHAR(512) NOT NULL, content_type VARCHAR(64) NOT NULL,
 declared_size BIGINT NOT NULL, actual_size BIGINT, state VARCHAR(16) NOT NULL,
 expires_at BIGINT NOT NULL, temp_removed TINYINT NOT NULL DEFAULT 0, INDEX idx_file_owner_note(owner_id,note_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS interaction_count (
 target_type VARCHAR(32) NOT NULL,target_id BIGINT NOT NULL,interaction_type VARCHAR(32) NOT NULL,
 total BIGINT NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(target_type,target_id,interaction_type)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS interaction_command (
 user_id BIGINT NOT NULL,request_id VARCHAR(64) NOT NULL,fingerprint VARCHAR(160) NOT NULL,
 active TINYINT NOT NULL,total BIGINT NOT NULL,PRIMARY KEY(user_id,request_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS note_view (
 event_id VARCHAR(64) PRIMARY KEY,note_id BIGINT NOT NULL,user_id BIGINT NOT NULL,
 created_at BIGINT NOT NULL,applied TINYINT NOT NULL DEFAULT 0,INDEX idx_view_note(note_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS note_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36) NOT NULL UNIQUE,
 topic VARCHAR(128) NOT NULL,event_key VARCHAR(160) NOT NULL,payload LONGTEXT NOT NULL,
 created_at BIGINT NOT NULL,sent_at BIGINT,INDEX idx_pending(sent_at,id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS auth_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36) NOT NULL UNIQUE,
 topic VARCHAR(128) NOT NULL,event_key VARCHAR(160) NOT NULL,payload LONGTEXT NOT NULL,
 created_at BIGINT NOT NULL,sent_at BIGINT,INDEX idx_pending(sent_at,id)
) ENGINE=InnoDB;
