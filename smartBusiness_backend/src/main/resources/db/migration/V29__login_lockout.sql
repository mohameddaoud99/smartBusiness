-- A user who keeps failing to sign in is locked out for a while, rather than left open to endless guessing.
-- failed_login_attempts resets to 0 on a successful sign-in; locked_until is null until the threshold is reached.

ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;
