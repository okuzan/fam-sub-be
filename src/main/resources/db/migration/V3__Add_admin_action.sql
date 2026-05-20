CREATE TABLE admin_action (
    id UUID PRIMARY KEY,
    action_type VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID,
    subscriber_id UUID,
    from_month VARCHAR(7),
    to_month VARCHAR(7),
    summary TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_account_id UUID NOT NULL
);

CREATE INDEX idx_admin_action_created_at
    ON admin_action (created_at);

CREATE INDEX idx_admin_action_type
    ON admin_action (action_type);

CREATE INDEX idx_admin_action_target
    ON admin_action (target_type, target_id);
