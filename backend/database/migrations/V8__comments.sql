-- Comments on approved and claimed posts. They appear at once; the author or an admin can delete one.
create table public.comments (
    id           uuid        primary key default gen_random_uuid(),
    item_id      uuid        not null references public.items (id),
    author_uid   text,
    author_email text        not null,
    author_name  text        not null,
    author_role  text        not null check (author_role in ('STUDENT', 'STAFF', 'ADMIN')),  -- role when posted
    body         text,
    image_path   text,        -- object path in the private item-images bucket, e.g. comments/<uuid>.jpg
    created_at   timestamptz not null default now(),
    constraint comments_not_empty_check check (length(trim(coalesce(body, ''))) > 0 or image_path is not null),
    constraint comments_body_length_check check (char_length(coalesce(body, '')) <= 1000)
);

create index comments_item_id_created_at_idx on public.comments (item_id, created_at);
-- For the cooldown between one person's comments.
create index comments_author_email_created_at_idx on public.comments (author_email, created_at desc);

alter table public.comments enable row level security;
