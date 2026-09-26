package com.sales.smartBusiness.brand;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BrandResponse {

    private Long id;
    private String name;
    /** How many products point here — filled in by the service, guides the delete confirmation. */
    private long productCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
