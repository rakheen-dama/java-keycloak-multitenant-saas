CREATE TABLE IF NOT EXISTS magic_link_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash        VARCHAR(255) NOT NULL,
    customer_id       UUID NOT NULL,
    org_id            VARCHAR(255) NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at           TIMESTAMP WITH TIME ZONE,
    created_ip        VARCHAR(45),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_magic_link_tokens_hash UNIQUE (token_hash)
);

-- Index for rate limiting (count tokens per customer in time window)
CREATE INDEX IF NOT EXISTS idx_magic_link_tokens_customer_created
    ON magic_link_tokens (customer_id, created_at DESC);

-- Index for cleanup of expired tokens
CREATE INDEX IF NOT EXISTS idx_magic_link_tokens_expires
    ON magic_link_tokens (expires_at)
    WHERE used_at IS NULL;
