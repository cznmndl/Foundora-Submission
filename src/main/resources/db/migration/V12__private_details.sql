-- Identifying details only SDFO sees (marks, names, stickers, contents). Reporters keep the public description
-- general and put these here, so a claimant can be asked about them. Optional; older reports have none.
alter table public.items add column private_details text;
alter table public.items add constraint items_private_details_length_check
    check (char_length(coalesce(private_details, '')) <= 500);
