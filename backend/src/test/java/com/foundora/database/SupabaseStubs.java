package com.foundora.database;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.Statement;

/** The bits of Supabase's own schema that our migrations touch (V2 and V5 update storage.buckets). */
final class SupabaseStubs {

    private SupabaseStubs() {
    }

    static void create(DataSource dataSource) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("create schema if not exists storage");
            s.execute("""
                    create table if not exists storage.buckets (
                        id text primary key, name text, public boolean, file_size_limit bigint,
                        allowed_mime_types text[])""");
        }
    }
}
