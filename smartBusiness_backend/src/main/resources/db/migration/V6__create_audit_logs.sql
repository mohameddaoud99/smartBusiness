-- One security trail per company, replacing the per-user history table.
-- Append-only: rows are written by the services and never updated.
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT      NOT NULL,
    -- Who acted. NULL only for a failed sign-in, where nobody is authenticated yet.
    user_id     BIGINT,
    -- Snapshot of the email used, so the line stays readable whatever happens later
    username    VARCHAR(150) NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(20),
    entity_id   BIGINT,
    detail      VARCHAR(255),
    ip_address  VARCHAR(45),
    occurred_at TIMESTAMP    NOT NULL,

    CONSTRAINT fk_audit_logs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_audit_logs_user    FOREIGN KEY (user_id)    REFERENCES users (id)
);

-- The audit screen always reads one company, newest first
CREATE INDEX idx_audit_logs_company ON audit_logs (company_id, occurred_at DESC);

-- The History tab of a user reads that user's own trail
CREATE INDEX idx_audit_logs_entity ON audit_logs (company_id, entity_type, entity_id, occurred_at DESC);

-- Superseded by audit_logs: same events, one table instead of two
DROP TABLE user_history;
