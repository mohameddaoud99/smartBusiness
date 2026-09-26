package com.sales.smartBusiness.warehouse;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WarehouseResponse {

    private Long id;
    private String name;
    private String address;
    private boolean defaultWarehouse;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
