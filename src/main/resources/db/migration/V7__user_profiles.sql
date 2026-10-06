-- One profile per signed-in person, filled in once at first sign-in and editable on My Profile.
-- Students give course, year level and section; staff and admins give a department or office.
create table public.user_profiles (
    uid         text        primary key,               -- Firebase UID
    email       text        not null unique,
    full_name   text        not null,                  -- from the Google account, refreshed at each sign-in
    role        text        not null check (role in ('STUDENT', 'STAFF', 'ADMIN')),  -- role at the last sign-in
    course      text,
    year_level  integer     check (year_level between 1 and 5),
    section     text,
    department  text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint user_profiles_lengths_check check (
        char_length(coalesce(course, '')) <= 120 and char_length(coalesce(section, '')) <= 20
        and char_length(coalesce(department, '')) <= 120 and char_length(full_name) <= 200)
);

-- Admins look people up by name or email when recording who claimed an item.
create index user_profiles_full_name_idx on public.user_profiles (lower(full_name));

-- RLS with no policies: only the server (table owner) can read or write profiles.
alter table public.user_profiles enable row level security;
