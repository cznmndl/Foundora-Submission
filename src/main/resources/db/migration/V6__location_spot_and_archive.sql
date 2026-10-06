-- Location becomes a fixed campus place (chosen from foundora.campus-locations) plus a free-text specific spot.
-- Reports filed before this keep their free-text location and have no spot.
alter table public.items
    add column location_spot text,
    -- Set when a claimed report is archived (foundora.claims.archive-after after it was claimed).
    add column archived_at   timestamptz;

alter table public.items
    add constraint items_location_spot_length_check check (char_length(coalesce(location_spot, '')) <= 200),
    add constraint items_archived_only_when_claimed_check check (archived_at is null or status in ('CLAIMED', 'REMOVED'));

-- The hourly archive job looks for claimed reports that aren't archived yet.
create index items_claimed_not_archived_idx on public.items (claimed_at) where status = 'CLAIMED' and archived_at is null;
