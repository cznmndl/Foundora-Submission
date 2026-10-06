-- Who an item was released to, recorded by the admin who marked it as claimed. Kept forever for disputes.
-- A reopened claim keeps its row (status REOPENED); the item can then be claimed again with a new row.
create table public.claim_records (
    id                   uuid        primary key default gen_random_uuid(),
    item_id              uuid        not null references public.items (id),
    claimant_uid         text,        -- set when the claimant was picked from registered users
    claimant_email       text,
    claimant_name        text        not null,
    claimant_affiliation text        not null,  -- course, year and section, or department / office
    released_at          timestamptz not null,
    released_by_email    text        not null,
    released_by_name     text        not null,
    notes                text,
    status               text        not null default 'ACTIVE' check (status in ('ACTIVE', 'DISPUTED', 'REOPENED')),
    dispute_note         text,
    status_changed_at    timestamptz,
    status_changed_by    text,
    created_at           timestamptz not null default now(),
    constraint claim_records_lengths_check check (
        char_length(claimant_name) <= 200 and char_length(claimant_affiliation) <= 200
        and char_length(coalesce(notes, '')) <= 1000 and char_length(coalesce(dispute_note, '')) <= 1000)
);

create index claim_records_item_id_idx on public.claim_records (item_id, created_at);
-- At most one claim per item that hasn't been reopened.
create unique index claim_records_one_open_per_item_idx on public.claim_records (item_id) where status <> 'REOPENED';

alter table public.claim_records enable row level security;
