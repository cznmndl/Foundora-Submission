package com.foundora.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations on a throwaway Postgres (never Supabase). V1-V5 run first and rows are
 * written the way the live site writes them today; then V6 onward must apply on top without losing anything.
 */
class MigrationTest {

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void migrateWithOldData() throws Exception {
        postgres = EmbeddedPostgres.start();
        dataSource = postgres.getPostgresDatabase();
        SupabaseStubs.create(dataSource);

        flyway("5").migrate();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                    insert into public.items (id, item_name, description, category, location, date_lost_found,
                        report_type, status, reporter_name, reporter_email, reporter_uid, image_path, created_at,
                        claimed_at)
                    values
                      ('11111111-1111-1111-1111-111111111111', 'Blue umbrella', 'Folding', 'UMBRELLAS',
                       'Library 2nd floor', '2026-09-01', 'FOUND', 'APPROVED', 'Juan', 'juan_delacruz@dlsl.edu.ph',
                       'uid-juan', 'items/a.jpg', now(), null),
                      ('22222222-2222-2222-2222-222222222222', 'Calculator', 'Casio', 'ELECTRONICS',
                       'Canteen', '2026-09-02', 'LOST', 'CLAIMED', 'Maria', 'maria_santos@dlsl.edu.ph',
                       null, null, now(), now() - interval '40 days')""");
            s.execute("""
                    insert into public.activity_log (item_id, item_name, action, actor_email, actor_name, created_at)
                    values ('22222222-2222-2222-2222-222222222222', 'Calculator', 'CLAIMED', 'ana.cruz@dlsl.edu.ph',
                            'Ana', now())""");
        }
        // A profile as V7–V12 stored it (a course, no community), for V13's backfill.
        flyway("12").migrate();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                    insert into public.user_profiles (uid, email, full_name, role, course, year_level, section)
                    values ('uid-old', 'old_student@dlsl.edu.ph', 'Old Student', 'STUDENT', 'BS CpE', 2, 'O2A'),
                           ('uid-staff', 'some.staff@dlsl.edu.ph', 'Some Staff', 'STAFF', null, null, null)""");
        }
        flyway(null).migrate();
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas("flyway").defaultSchema("flyway");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @Test
    void appliesEveryMigration() {
        assertThat(flyway(null).info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(flyway(null).info().pending()).isEmpty();
    }

    @Test
    void keepsExistingReportsUnchanged() throws Exception {
        assertThat(query("select item_name || '|' || location || '|' || coalesce(location_spot, '-') || '|' "
                + "|| coalesce(image_path, '-') || '|' || (archived_at is null) from public.items "
                + "where id = '11111111-1111-1111-1111-111111111111'"))
                .isEqualTo("Blue umbrella|Library 2nd floor|-|items/a.jpg|true");
        assertThat(query("select status from public.items where id = '22222222-2222-2222-2222-222222222222'"))
                .isEqualTo("CLAIMED");
        assertThat(query("select count(*) from public.activity_log where action = 'CLAIMED'")).isEqualTo("1");
    }

    @Test
    void newTablesHaveRowLevelSecurityWithNoPolicies() throws Exception {
        for (String table : new String[]{"user_profiles", "comments", "claim_records", "claim_requests"}) {
            assertThat(query("select relrowsecurity from pg_class where oid = 'public." + table + "'::regclass"))
                    .as(table).isEqualTo("t");
            assertThat(query("select count(*) from pg_policies where schemaname = 'public' and tablename = '"
                    + table + "'")).as(table).isEqualTo("0");
        }
    }

    @Test
    void enforcesTheNewRules() throws Exception {
        exec("insert into public.user_profiles (uid, email, full_name, role, course, year_level, section) "
                + "values ('uid-x', 'x_y@dlsl.edu.ph', 'X Y', 'STUDENT', 'BSIT', 2, 'O1A')");
        assertThatThrownBy(() -> exec("insert into public.user_profiles (uid, email, full_name, role, year_level) "
                + "values ('uid-z', 'z_z@dlsl.edu.ph', 'Z', 'STUDENT', 6)")).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("insert into public.user_profiles (uid, email, full_name, role) "
                + "values ('uid-dup', 'x_y@dlsl.edu.ph', 'Dup', 'STUDENT')")).isInstanceOf(SQLException.class);

        exec("insert into public.comments (item_id, author_email, author_name, author_role, body) values "
                + "('11111111-1111-1111-1111-111111111111', 'x_y@dlsl.edu.ph', 'X Y', 'STUDENT', 'Mine!')");
        assertThatThrownBy(() -> exec("insert into public.comments (item_id, author_email, author_name, author_role, body) "
                + "values ('11111111-1111-1111-1111-111111111111', 'x_y@dlsl.edu.ph', 'X Y', 'STUDENT', '  ')"))
                .as("empty comment").isInstanceOf(SQLException.class);

        String claim = "insert into public.claim_records (item_id, claimant_name, claimant_affiliation, released_at, "
                + "released_by_email, released_by_name, status) values "
                + "('22222222-2222-2222-2222-222222222222', 'Pedro', 'BSIT', now(), 'a@dlsl.edu.ph', 'A', '%s')";
        exec(claim.formatted("ACTIVE"));
        assertThatThrownBy(() -> exec(claim.formatted("ACTIVE"))).as("second open claim")
                .isInstanceOf(SQLException.class);
        exec(claim.formatted("REOPENED")); // reopened claims are history; any number is fine

        exec("insert into public.activity_log (item_id, item_name, action, actor_email, actor_name) values "
                + "('11111111-1111-1111-1111-111111111111', 'Blue umbrella', 'COMMENT_DELETED', 'a', 'A')");
        assertThatThrownBy(() -> exec("insert into public.activity_log (item_name, action, actor_email, actor_name) "
                + "values ('x', 'NOT_AN_ACTION', 'a', 'A')")).isInstanceOf(SQLException.class);

        String request = "insert into public.claim_requests (item_id, requester_uid, requester_email, requester_name, "
                + "proof, status) values ('11111111-1111-1111-1111-111111111111', 'uid-%1$s', '%1$s@dlsl.edu.ph', "
                + "'%1$s', 'Lost it at the library on Monday', '%2$s')";
        exec(request.formatted("p_one", "VERIFIED"));
        assertThatThrownBy(() -> exec(request.formatted("p_one", "PENDING"))).as("second open request, same person")
                .isInstanceOf(SQLException.class);
        exec(request.formatted("p_two", "PENDING"));
        assertThatThrownBy(() -> exec(request.formatted("p_three", "VERIFIED"))).as("second verified request")
                .isInstanceOf(SQLException.class);
        exec(request.formatted("p_one", "DENIED")); // closed requests are history
        assertThatThrownBy(() -> exec(request.formatted("p_four", "MATCHED"))).isInstanceOf(SQLException.class);
        exec("insert into public.activity_log (item_id, item_name, action, actor_email, actor_name) values "
                + "('11111111-1111-1111-1111-111111111111', 'Blue umbrella', 'CLAIM_PASS_EXPIRED', 'system', 'S')");

        assertThatThrownBy(() -> exec("update public.items set archived_at = now() "
                + "where id = '11111111-1111-1111-1111-111111111111'")).as("only claimed reports are archived")
                .isInstanceOf(SQLException.class);
        // V13: communities. A profile with a course became College, staff stayed without one; grades must fit.
        assertThat(query("select community from public.user_profiles where uid = 'uid-old'")).isEqualTo("COLLEGE");
        assertThat(query("select coalesce(community, '-') from public.user_profiles where uid = 'uid-staff'"))
                .isEqualTo("-");
        exec("insert into public.user_profiles (uid, email, full_name, role, community, grade_level, section) "
                + "values ('uid-j', 'j_h@dlsl.edu.ph', 'J H', 'STUDENT', 'JUNIOR_HIGH', 8, 'Rizal')");
        assertThatThrownBy(() -> exec("insert into public.user_profiles (uid, email, full_name, role, community, "
                + "grade_level) values ('uid-k', 'k_h@dlsl.edu.ph', 'K', 'STUDENT', 'JUNIOR_HIGH', 11)"))
                .as("grade outside the community").isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("insert into public.user_profiles (uid, email, full_name, role, community) "
                + "values ('uid-m', 'm_h@dlsl.edu.ph', 'M', 'STUDENT', 'KINDER')")).isInstanceOf(SQLException.class);

        exec("update public.items set private_details = 'Initials JDC' "
                + "where id = '11111111-1111-1111-1111-111111111111'");
        assertThatThrownBy(() -> exec("update public.items set private_details = repeat('x', 501) "
                + "where id = '11111111-1111-1111-1111-111111111111'")).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("update public.items set location_spot = repeat('x', 201) "
                + "where id = '11111111-1111-1111-1111-111111111111'")).isInstanceOf(SQLException.class);

        // V14: money needs an amount and container; HANDLED needs a note.
        String money = "insert into public.items (item_name, description, category, location, date_lost_found, "
                + "report_type, status, reporter_name, reporter_email, money_amount, money_container) values "
                + "('Money', '', 'MONEY', 'Sentrum', '2026-10-01', 'FOUND', '%s', 'Juan', 'j@dlsl.edu.ph', %s, %s)";
        exec(money.formatted("APPROVED", "500.00", "'ENVELOPE'"));
        assertThatThrownBy(() -> exec(money.formatted("APPROVED", "null", "'ENVELOPE'"))).as("no amount")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec(money.formatted("APPROVED", "0", "'ENVELOPE'"))).as("zero amount")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec(money.formatted("APPROVED", "500", "null"))).as("no container")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec(money.formatted("APPROVED", "500", "'SOCK'"))).as("unknown container")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec(money.formatted("HANDLED", "500", "'LOOSE'"))).as("handled without a note")
                .isInstanceOf(SQLException.class);
        exec("update public.items set status = 'HANDLED', handled_note = 'Turned over to Finance' "
                + "where category = 'MONEY'");
        exec("insert into public.activity_log (item_name, action, actor_email, actor_name, reason) values "
                + "('Money', 'HANDLED_BY_SDFO', 'a', 'A', 'Turned over to Finance')");
        assertThatThrownBy(() -> exec("update public.claim_requests set money_amount = -1")).as("negative claim")
                .isInstanceOf(SQLException.class);
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String query(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getString(1);
        }
    }
}
