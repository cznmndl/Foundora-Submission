package com.foundora.database.entity;

import java.util.Locale;

/** URL forms of enum names, e.g. IDS_DOCUMENTS as {@code ids-documents}, for links and filters. */
final class Slugs {

    private Slugs() {
    }

    static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The constant with this slug, ignoring case and surrounding spaces; null if there is none. */
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
