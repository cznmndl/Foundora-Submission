-- Money: loose cash, envelopes and coin purses (wallets stay under BAGS_WALLETS). Found money shows on the feed
-- only as "Money" and when it was surrendered; lost money never shows on the feed. The amount, denominations,
-- container and time are seen only by the reporter and SDFO, and claimants must give the same details.

alter table public.items drop constraint items_category_check;
alter table public.items add constraint items_category_check check (category in (
    'ELECTRONICS', 'IDS_DOCUMENTS', 'TUMBLERS_BOTTLES', 'UMBRELLAS', 'SCHOOL_SUPPLIES',
    'BAGS_WALLETS', 'CLOTHING_ACCESSORIES', 'KEYS', 'MONEY', 'OTHERS'));

-- HANDLED: unclaimed found money that SDFO dealt with under its own policy. Final, like REMOVED.
alter table public.items drop constraint items_status_check;
alter table public.items add constraint items_status_check
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED', 'HANDLED'));

alter table public.items
    add column money_amount        numeric(12, 2),
    add column money_denominations text,
    add column money_container     text check (money_container in ('ENVELOPE', 'COIN_PURSE', 'LOOSE', 'OTHER')),
    add column time_lost_found     time,         -- approximate time of day; money reports only for now
    add column handled_note        text,
    add column handled_by_name     text,
    add column handled_at          timestamptz;

alter table public.items
    add constraint items_money_check check (
        category <> 'MONEY' or (money_amount is not null and money_amount > 0 and money_container is not null)),
    add constraint items_money_lengths_check check (
        char_length(coalesce(money_denominations, '')) <= 200 and char_length(coalesce(handled_note, '')) <= 500),
    add constraint items_handled_note_check check (
        status <> 'HANDLED' or length(trim(coalesce(handled_note, ''))) > 0);

-- What a claimant says about money they lost, compared side by side with the finder's private details.
alter table public.claim_requests
    add column money_amount        numeric(12, 2) check (money_amount is null or money_amount > 0),
    add column money_denominations text,
    add column money_where         text,
    add column money_lost_on       date,
    add column money_lost_time     time,
    add column money_container     text check (money_container in ('ENVELOPE', 'COIN_PURSE', 'LOOSE', 'OTHER'));

alter table public.claim_requests add constraint claim_requests_money_lengths_check check (
    char_length(coalesce(money_denominations, '')) <= 200 and char_length(coalesce(money_where, '')) <= 200);

-- The admin's Lost money view lists open money reports.
create index items_money_idx on public.items (report_type, date_lost_found) where category = 'MONEY';

alter table public.activity_log drop constraint activity_log_action_check;
alter table public.activity_log
    add constraint activity_log_action_check check (action in (
        'SUBMITTED', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED',
        'COMMENT_DELETED', 'CLAIM_DISPUTED', 'CLAIM_RESOLVED', 'CLAIM_REOPENED', 'ARCHIVED',
        'CLAIM_REQUESTED', 'CLAIM_INFO_REQUESTED', 'CLAIM_VERIFIED', 'CLAIM_DENIED', 'CLAIM_WITHDRAWN',
        'CLAIM_PASS_EXTENDED', 'CLAIM_PASS_EXPIRED', 'HANDLED_BY_SDFO'));
