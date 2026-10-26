ALTER TABLE user_accounts
    ADD COLUMN invitation_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_user_accounts_invitation_state CHECK (NOT (active AND invitation_pending)),
    ADD CONSTRAINT ck_user_accounts_credentials_version CHECK (credentials_version >= 0);

CREATE TABLE account_action_tokens (
    id UUID NOT NULL,
    user_account_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    credentials_version BIGINT NOT NULL,

    CONSTRAINT pk_account_action_tokens PRIMARY KEY (id),
    CONSTRAINT uk_account_action_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_action_tokens_user
        FOREIGN KEY (user_account_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_account_action_tokens_purpose CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_account_action_tokens_expiration CHECK (expires_at > issued_at)
);

CREATE INDEX ix_account_action_tokens_user_pending
    ON account_action_tokens (user_account_id, expires_at)
    WHERE consumed_at IS NULL;
