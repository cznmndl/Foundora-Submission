-- A 4-digit PIN asked for after every Google sign-in. Only its BCrypt hash is stored, never the PIN.
-- Three wrong PINs lock PIN entry until pin_locked_until. "Forgot PIN?" sets pin_reset_requested, and the next
-- Google sign-in clears the old PIN so a new one can be created.
alter table public.user_profiles
    add column pin_hash            text,
    add column pin_failed_attempts integer not null default 0,
    add column pin_locked_until    timestamptz,
    add column pin_reset_requested boolean not null default false;

alter table public.user_profiles add constraint user_profiles_pin_check check (
    pin_failed_attempts between 0 and 3
    and (pin_hash is null or pin_hash like '$2%'));
