-- Students belong to one DLSL community. College students give course, year level and section;
-- Elementary, Junior High and Senior High students give grade level and section. Staff and admins still give
-- a department or office, so community stays null for them.
alter table public.user_profiles add column community text
    check (community in ('ELEMENTARY', 'JUNIOR_HIGH', 'SENIOR_HIGH', 'COLLEGE'));
alter table public.user_profiles add column grade_level integer;

-- Everyone who already gave a course is a college student, so nobody has to redo setup.
update public.user_profiles set community = 'COLLEGE' where course is not null and course <> '';

-- A grade level only for basic education, and within its community's grades.
alter table public.user_profiles add constraint user_profiles_grade_level_check check (
    (community is null and grade_level is null)
    or (community = 'COLLEGE' and grade_level is null)
    or (community = 'ELEMENTARY' and grade_level between 1 and 6)
    or (community = 'JUNIOR_HIGH' and grade_level between 7 and 10)
    or (community = 'SENIOR_HIGH' and grade_level between 11 and 12));
