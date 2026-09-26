-- Organisational only: which site a user is based at. Never a permission scope —
-- what a user may do still applies to the whole company, regardless of this column.
ALTER TABLE users ADD COLUMN branch_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_branch
    FOREIGN KEY (branch_id) REFERENCES branches (id);

CREATE INDEX idx_users_branch ON users (branch_id);
