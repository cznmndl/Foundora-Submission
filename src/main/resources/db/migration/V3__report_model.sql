-- Reshape items into the report model from the project proposal.

alter table public.items rename column status to report_type;
alter table public.items rename column approval_status to status;
alter table public.items rename column title to item_name;

-- V1's check constraints were auto-named after the old columns; drop them by lookup, not by guessed name.
do $$
declare c record;
begin
    for c in select conname from pg_constraint
             where conrelid = 'public.items'::regclass and contype = 'c'
    loop
        execute format('alter table public.items drop constraint %I', c.conname);
    end loop;
end $$;

-- Map the old free-text categories onto the fixed list.
update public.items set category = case category
    when 'Electronics'     then 'ELECTRONICS'
    when 'IDs'             then 'IDS_DOCUMENTS'
    when 'Tumblers'        then 'TUMBLERS_BOTTLES'
    when 'Umbrellas'       then 'UMBRELLAS'
    when 'School Supplies' then 'SCHOOL_SUPPLIES'
    else 'OTHERS'
end;

-- Photos move from public URLs to object paths in the (now private) bucket.
alter table public.items add column image_path text;
update public.items
   set image_path = substring(image_url from '/item-images/(.+)$')
 where image_url is not null;
alter table public.items drop column image_url;

alter table public.items
    add column reporter_uid      text,
    add column reviewed_by_email text,
    add column reviewed_by_name  text,
    add column reviewed_at       timestamptz,
    add column review_reason     text,
    add column claimed_at        timestamptz,
    add column updated_at        timestamptz not null default now(),
    add column version           bigint      not null default 0;

update public.items set updated_at = created_at;
update public.items
   set review_reason = 'Rejected before reasons were recorded.'
 where status = 'REJECTED' and review_reason is null;

alter table public.items
    add constraint items_report_type_check check (report_type in ('LOST', 'FOUND')),
    add constraint items_status_check
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED')),
    add constraint items_category_check check (category in (
        'ELECTRONICS', 'IDS_DOCUMENTS', 'TUMBLERS_BOTTLES', 'UMBRELLAS', 'SCHOOL_SUPPLIES',
        'BAGS_WALLETS', 'CLOTHING_ACCESSORIES', 'KEYS', 'OTHERS')),
    add constraint items_reject_reason_check
        check (status <> 'REJECTED' or length(trim(coalesce(review_reason, ''))) > 0),
    add constraint items_lengths_check check (
        char_length(item_name) <= 80 and char_length(description) <= 1000
        and char_length(location) <= 120 and char_length(coalesce(review_reason, '')) <= 500);

alter index public.items_approval_status_created_at_idx rename to items_status_created_at_idx;
create index items_reporter_email_created_at_idx on public.items (reporter_email, created_at desc);
