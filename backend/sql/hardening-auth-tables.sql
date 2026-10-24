-- Session/MFA tables to be included only in the provenance-checked upgrade.
create table auth_mfa_credentials (user_id varchar(80) primary key, encrypted_seed varchar(512), pending_seed varchar(512), pending_expires_at timestamptz, last_accepted_step bigint not null default -1, enrolled_at timestamptz);
create table auth_mfa_recovery_codes (user_id varchar(80) not null references auth_mfa_credentials(user_id) on delete cascade, code_hash varchar(64) not null, primary key(user_id,code_hash));
create table auth_abuse_buckets (bucket_key varchar(64) primary key, window_start timestamptz not null, attempts integer not null default 0, blocked_until timestamptz);
create table auth_email_challenges (user_id varchar(80) primary key, code_hash varchar(64) not null, session_hash varchar(64) not null, expires_at timestamptz not null, attempts integer not null default 0);
create table auth_guest_migrations (token_hash varchar(64) primary key, claimed_at timestamptz not null);
create table auth_login_failures (id varchar(36) primary key, account_hash varchar(64) not null, failed_at timestamptz not null);
create index auth_login_failure_account_time on auth_login_failures(account_hash,failed_at);
