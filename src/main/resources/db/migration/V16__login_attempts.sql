-- One row per sign-in step: the Google check, then each PIN setup, entry, failure and lock.
-- attempt_number is which PIN try it was (1 to 3) for PIN entries, and empty for the other outcomes.
create table public.login_attempts (
    id             bigint generated always as identity primary key,
    uid            text,
    email          text,
    outcome        text        not null
                   check (outcome in ('GOOGLE_OK', 'REJECTED_DOMAIN', 'INVALID_TOKEN', 'PIN_SET', 'PIN_SUCCESS',
                                      'PIN_FAILED', 'LOCKED', 'PIN_RESET')),
    attempt_number integer,
    created_at     timestamptz not null default now()
);

create index login_attempts_created_at_idx on public.login_attempts (created_at desc);
create index login_attempts_uid_created_at_idx on public.login_attempts (uid, created_at desc);

-- RLS with no policies: only the server (table owner) can read or write login history.
alter table public.login_attempts enable row level security;

-- Already added to Dev by hand, so only add it where it is missing.
alter table public.user_profiles add column if not exists last_login_at timestamptz;
