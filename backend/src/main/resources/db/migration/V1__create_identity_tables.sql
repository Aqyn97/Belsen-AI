CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(100) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT users_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT users_timezone_not_blank CHECK (btrim(timezone) <> ''),
    CONSTRAINT users_password_hash_not_blank CHECK (password_hash IS NULL OR btrim(password_hash) <> ''),
    CONSTRAINT users_timestamp_order CHECK (updated_at >= created_at)
);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_identities_provider_subject_unique UNIQUE (provider, provider_subject),
    CONSTRAINT external_identities_provider_not_blank CHECK (btrim(provider) <> ''),
    CONSTRAINT external_identities_subject_not_blank CHECK (btrim(provider_subject) <> '')
);

CREATE INDEX external_identities_user_idx ON external_identities (user_id);
