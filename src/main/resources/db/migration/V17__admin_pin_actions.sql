-- Admins reset PINs and unlock accounts from the dashboard; the self-service "Forgot PIN?" is gone.
-- Account actions go in the activity log with no item: item_name holds the account's name and email.
alter table public.activity_log drop constraint activity_log_action_check;
alter table public.activity_log
    add constraint activity_log_action_check check (action in (
        'SUBMITTED', 'APPROVED', 'REJECTED', 'CLAIMED', 'REMOVED',
        'COMMENT_DELETED', 'CLAIM_DISPUTED', 'CLAIM_RESOLVED', 'CLAIM_REOPENED', 'ARCHIVED',
        'CLAIM_REQUESTED', 'CLAIM_INFO_REQUESTED', 'CLAIM_VERIFIED', 'CLAIM_DENIED', 'CLAIM_WITHDRAWN',
        'CLAIM_PASS_EXTENDED', 'CLAIM_PASS_EXPIRED', 'HANDLED_BY_SDFO', 'PIN_RESET', 'ACCOUNT_UNLOCKED'));

alter table public.login_attempts drop constraint login_attempts_outcome_check;
alter table public.login_attempts
    add constraint login_attempts_outcome_check check (outcome in (
        'GOOGLE_OK', 'REJECTED_DOMAIN', 'INVALID_TOKEN', 'PIN_SET', 'PIN_SUCCESS', 'PIN_FAILED', 'LOCKED',
        'PIN_RESET', 'ACCOUNT_UNLOCKED'));

-- Nothing sets pin_reset_requested any more. Cancel any request left over from "Forgot PIN?" so it cannot
-- clear a PIN at the next sign-in.
update public.user_profiles set pin_reset_requested = false where pin_reset_requested;
