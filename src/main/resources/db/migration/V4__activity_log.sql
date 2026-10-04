-- Who approved, rejected, claimed or removed which report, and when.
create table public.activity_log (
    id          bigint generated always as identity primary key,
    item_id     uuid references public.items (id) on delete set null,
    item_name   text        not null,
    action      text        not null
                check (action in ('SUBMITTED', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED')),
    actor_email text        not null,
    actor_name  text        not null,
    reason      text,
    created_at  timestamptz not null default now()
);

create index activity_log_created_at_idx on public.activity_log (created_at desc);
create index activity_log_action_created_at_idx on public.activity_log (action, created_at desc);

-- Same lock-down as items: RLS with no policies, so only the server (table owner) can read it.
alter table public.activity_log enable row level security;
