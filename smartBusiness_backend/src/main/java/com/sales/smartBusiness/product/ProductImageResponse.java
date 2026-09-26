package com.sales.smartBusiness.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code dataUri} is "data:{contentType};base64,...", ready for an &lt;img [src]&gt;. */
@Getter
@AllArgsConstructor
public class ProductImageResponse {

    private Long id;
    private String dataUri;
}
