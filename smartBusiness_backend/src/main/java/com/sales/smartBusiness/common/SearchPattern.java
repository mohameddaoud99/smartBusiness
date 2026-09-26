package com.sales.smartBusiness.common;

/**
 * Turns a free-text search box into the LIKE pattern the list queries expect.
 * <p>
 * Never returns null: PostgreSQL cannot infer the type of a NULL parameter inside
 * {@code LOWER()} ("function lower(bytea) does not exist"), so a blank search becomes
 * the match-all {@code "%"} instead.
 */
public final class SearchPattern {

    private SearchPattern() {
    }

    /** {@code "  Alpha "} → {@code "%alpha%"}; null or blank → {@code "%"}. */
    public static String like(String search) {
        return search == null || search.isBlank()
                ? "%"
                : "%" + search.trim().toLowerCase() + "%";
    }
}
