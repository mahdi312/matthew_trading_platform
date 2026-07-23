-- identity-service baseline schema (matches JPA @Entity mappings)

CREATE TABLE app_users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(80)  NOT NULL,
    password_hash       VARCHAR(255),
    display_name        VARCHAR(100) NOT NULL,
    email               VARCHAR(255),
    provider_subject    VARCHAR(255),
    auth_provider       VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    role                VARCHAR(20)  NOT NULL DEFAULT 'REGULAR_USER',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP,
    last_login_at       TIMESTAMP,
    hidden_tabs         VARCHAR(512),
    favorite_timeframes VARCHAR(256),
    CONSTRAINT uk_app_users_username UNIQUE (username)
);

CREATE TABLE app_settings (
    id            BIGSERIAL PRIMARY KEY,
    app_user_id   BIGINT       NOT NULL,
    setting_key   VARCHAR(128) NOT NULL,
    setting_value TEXT,
    CONSTRAINT uk_app_settings_user_key UNIQUE (app_user_id, setting_key)
);

CREATE TABLE role_permissions (
    id              BIGSERIAL PRIMARY KEY,
    subject_role    VARCHAR(20),
    subject_user_id BIGINT,
    tab_name        VARCHAR(40) NOT NULL,
    visible         BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_role_perm_subject_tab UNIQUE (subject_role, subject_user_id, tab_name)
);

CREATE TABLE user_profiles (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    avatar_color          VARCHAR(255),
    description           VARCHAR(255),
    active                BOOLEAN      NOT NULL DEFAULT FALSE,
    app_user_id           BIGINT,
    created_at            TIMESTAMP    NOT NULL,
    last_accessed_at      TIMESTAMP,
    asset_focus           VARCHAR(16)  NOT NULL DEFAULT 'MULTI',
    default_symbol        VARCHAR(32),
    chart_provider        VARCHAR(32)  DEFAULT 'AUTO',
    fundamental_provider  VARCHAR(32)  DEFAULT 'AUTO',
    watchlist             VARCHAR(1024),
    drawing_settings_json TEXT,
    CONSTRAINT uk_user_profiles_name UNIQUE (name)
);
