-- A student or staff member saying "this is mine" about a found item, with their proof. Admins compare every
-- open request for an item, then verify one (a pickup pass valid for foundora.claims.pass-valid-for), deny it,
-- or ask for more information. The release itself is still a claim_records row (V9).
create table public.claim_requests (
    id                     uuid        primary key default gen_random_uuid(),
    item_id                uuid        not null references public.items (id),
    requester_uid          text        not null,
    requester_email        text        not null,
    requester_name         text        not null,
    requester_affiliation  text,        -- course, year and section, or department, copied from the profile
    proof                  text        not null,
    image_path             text,        -- optional photo in the private bucket, under claims/
    status                 text        not null default 'PENDING' check (status in (
                               'PENDING', 'NEEDS_INFO', 'VERIFIED', 'DENIED', 'WITHDRAWN', 'EXPIRED',
                               'RELEASED', 'CLOSED')),
    admin_note             text,        -- the question asked, or why it was denied
    info_reply             text,        -- the requester's answer to the question
    reviewed_by_email      text,
    reviewed_by_name       text,
    reviewed_at            timestamptz,
    verified_at            timestamptz,
    expires_at             timestamptz, -- end of the pickup pass, while VERIFIED
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint      not null default 0,
    constraint claim_requests_lengths_check check (
        char_length(proof) <= 1000 and char_length(coalesce(admin_note, '')) <= 500
        and char_length(coalesce(info_reply, '')) <= 1000)
);

create index claim_requests_item_id_idx on public.claim_requests (item_id, created_at);
create index claim_requests_requester_idx on public.claim_requests (requester_email, created_at);
create index claim_requests_open_idx on public.claim_requests (status, created_at)
    where status in ('PENDING', 'NEEDS_INFO', 'VERIFIED');
-- One open request per person per item, and at most one verified (pickup pass) per item.
create unique index claim_requests_one_open_per_requester_idx on public.claim_requests (item_id, requester_email)
    where status in ('PENDING', 'NEEDS_INFO', 'VERIFIED');
create unique index claim_requests_one_verified_per_item_idx on public.claim_requests (item_id)
    where status = 'VERIFIED';

alter table public.claim_requests enable row level security;

-- Activity log actions for claim requests.
alter table public.activity_log drop constraint activity_log_action_check;
alter table public.activity_log
    add constraint activity_log_action_check check (action in (
        'SUBMITTED', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED',
        'COMMENT_DELETED', 'CLAIM_DISPUTED', 'CLAIM_RESOLVED', 'CLAIM_REOPENED', 'ARCHIVED',
        'CLAIM_REQUESTED', 'CLAIM_INFO_REQUESTED', 'CLAIM_VERIFIED', 'CLAIM_DENIED', 'CLAIM_WITHDRAWN',
        'CLAIM_PASS_EXTENDED', 'CLAIM_PASS_EXPIRED'));
