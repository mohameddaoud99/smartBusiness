CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(60)  NOT NULL,
    last_name     VARCHAR(60)  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(30),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

-- Supports the status/role filters on the user list screen
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role   ON users (role);

CREATE TABLE user_history (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    event        VARCHAR(30)  NOT NULL,
    detail       VARCHAR(255),
    performed_by VARCHAR(50),
    occurred_at  TIMESTAMP    NOT NULL,

    CONSTRAINT fk_user_history_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- History is always read per user, newest first
CREATE INDEX idx_user_history_user ON user_history (user_id, occurred_at DESC);
