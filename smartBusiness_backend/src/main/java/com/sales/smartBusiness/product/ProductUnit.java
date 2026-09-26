package com.sales.smartBusiness.product;

import lombok.Getter;

/**
 * The units a quantity can be expressed in. A fixed catalogue rather than a
 * per-company settings screen — Finco shows it as a plain field on the product form,
 * not as something a company configures.
 */
@Getter
public enum ProductUnit {

    PIECE("Piece", "pcs"),
    KILOGRAM("Kilogram", "kg"),
    GRAM("Gram", "g"),
    LITER("Liter", "L"),
    METER("Meter", "m"),
    SQUARE_METER("Square meter", "m²"),
    BOX("Box", "box"),
    PACK("Pack", "pack"),
    HOUR("Hour", "h"),
    DAY("Day", "day");

    private final String label;
    private final String abbreviation;

    ProductUnit(String label, String abbreviation) {
        this.label = label;
        this.abbreviation = abbreviation;
    }
}
