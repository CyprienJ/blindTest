-- Emails are matched case-insensitively; enforce uniqueness on the lowercased value.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
