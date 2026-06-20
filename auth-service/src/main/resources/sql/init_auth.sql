CREATE TABLE IF NOT EXISTS user_info (
    user_id   BIGINT      PRIMARY KEY,
    username  VARCHAR(64) UNIQUE,
    password  VARCHAR(256),
    nickname  VARCHAR(128),
    avatar    VARCHAR(512),
    status    VARCHAR(16) DEFAULT 'ACTIVE',
    created_at BIGINT     NOT NULL,
    updated_at BIGINT     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_wechat (
    wechat_id  BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    openid     VARCHAR(128) NOT NULL,
    unionid    VARCHAR(128),
    nickname   VARCHAR(128),
    avatar     VARCHAR(512),
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_wechat_openid ON user_wechat(openid);
CREATE INDEX IF NOT EXISTS idx_user_wechat_userid ON user_wechat(user_id);
