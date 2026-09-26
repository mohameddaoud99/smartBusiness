package com.sales.smartBusiness.common;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A postal address, embedded wherever an entity needs one (customer or supplier billing
 * / shipping address, and so on). The owning entity supplies the column names through
 * {@code @AttributeOverride} so several copies on the same table never clash.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Address {

    private String street;
    private String city;
    private String region;
    private String postalCode;
    private String country;

    public boolean isEmpty() {
        return isBlank(street) && isBlank(city) && isBlank(region)
                && isBlank(postalCode) && isBlank(country);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
