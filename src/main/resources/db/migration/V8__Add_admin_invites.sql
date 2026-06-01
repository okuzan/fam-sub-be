create table admin_invite (
    id uuid primary key,
    email varchar(255) not null,
    token_hash varchar(128) not null unique,
    status varchar(32) not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone,
    revoked_at timestamp with time zone,
    invited_by_account_id uuid not null,
    accepted_by_account_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_admin_invite_email on admin_invite(email);
create index idx_admin_invite_status on admin_invite(status);
create index idx_admin_invite_token_hash on admin_invite(token_hash);
