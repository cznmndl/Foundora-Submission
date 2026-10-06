-- New activity log actions: comment deletions by admins, claim disputes and reopenings, automatic archiving.
-- V4's check constraint was auto-named; drop it by lookup rather than by a guessed name.
do $$
declare c record;
begin
    for c in select conname from pg_constraint
             where conrelid = 'public.activity_log'::regclass and contype = 'c'
    loop
        execute format('alter table public.activity_log drop constraint %I', c.conname);
    end loop;
end $$;

alter table public.activity_log
    add constraint activity_log_action_check check (action in (
        'SUBMITTED', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED',
        'COMMENT_DELETED', 'CLAIM_DISPUTED', 'CLAIM_RESOLVED', 'CLAIM_REOPENED', 'ARCHIVED'));
