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

CREATE UNIQUE INDEX idx_user_wechat_openid ON user_wechat(openid);
CREATE INDEX idx_user_wechat_userid ON user_wechat(user_id);

CREATE TABLE IF NOT EXISTS user_taobao (
    taobao_id  BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    open_id    VARCHAR(128) NOT NULL,
    nick       VARCHAR(128),
    avatar     VARCHAR(512),
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX idx_user_taobao_openid ON user_taobao(open_id);
CREATE INDEX idx_user_taobao_userid ON user_taobao(user_id);

CREATE TABLE IF NOT EXISTS user_phone (
    phone_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    phone      VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX idx_user_phone_phone ON user_phone(phone);
CREATE INDEX idx_user_phone_userid ON user_phone(user_id);
