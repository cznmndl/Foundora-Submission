package com.foundora.database.entity;

import java.util.Locale;

final class Slugs {

    private Slugs() {
    }

    static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static <E extends Enum<E>> E find(Class<E> type, String slug) {
        if (slug == null) {
            return null;
        }
        String wanted = slug.trim();
        for (E value : type.getEnumConstants()) {
            if (of(value).equalsIgnoreCase(wanted)) {
                return value;
            }
        }
        return null;
    }
}
