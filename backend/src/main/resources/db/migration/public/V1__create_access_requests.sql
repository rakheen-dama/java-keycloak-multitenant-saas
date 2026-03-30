CREATE TABLE access_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    organization_name   VARCHAR(255) NOT NULL,
    country             VARCHAR(100),
    industry            VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    otp_hash            VARCHAR(255),
    otp_expires_at      TIMESTAMPTZ,
    otp_attempts        INT DEFAULT 0,
    otp_verified_at     TIMESTAMPTZ,
    reviewed_by         VARCHAR(255),
    reviewed_at         TIMESTAMPTZ,
    keycloak_org_id     VARCHAR(255),
    provisioning_error  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_access_requests_status ON access_requests (status);
CREATE INDEX idx_access_requests_email ON access_requests (email);
