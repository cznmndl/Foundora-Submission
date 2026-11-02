-- Reported lost/found items. Only the Spring Boot server reads and writes this table,
-- connecting as the table owner (postgres), which bypasses Row Level Security.
create table public.items (
    id               uuid primary key default gen_random_uuid(),
    title            text        not null,
    description      text        not null,
    category         text        not null,
    location         text        not null,
    date_lost_found  date        not null,
    status           text        not null check (status in ('LOST', 'FOUND')),
    approval_status  text        not null default 'PENDING'
                                 check (approval_status in ('PENDING', 'APPROVED', 'REJECTED')),
    reporter_name    text        not null,
    reporter_email   text        not null,
    image_url        text,
    created_at       timestamptz not null default now()
);

create index items_approval_status_created_at_idx on public.items (approval_status, created_at desc);

-- RLS with no policies: the anon and authenticated roles used by Supabase's public
-- Data API (PostgREST) can't read or write any rows.
alter table public.items enable row level security;
