-- One-time recovery migration: reset only the protected built-in administrator account.
-- auth_version invalidates every previously issued access token for this account.
UPDATE sys_user
SET password_hash = '$2b$10$iKzJCqZ2.SdHiacURI6N3.fgIRi/2GE0UiGDefC.LfeQAC5K8nkeK',
    auth_version = auth_version + 1,
    updated_time = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND builtin = 1
  AND deleted = 0;
