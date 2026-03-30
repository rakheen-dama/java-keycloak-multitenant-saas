CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    author_type VARCHAR(20) NOT NULL CHECK (author_type IN ('MEMBER', 'CUSTOMER')),
    author_id   UUID NOT NULL,
    author_name VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_project_id ON comments (project_id, created_at ASC);
CREATE INDEX idx_comments_author ON comments (author_type, author_id);
