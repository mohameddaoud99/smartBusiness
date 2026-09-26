package com.sales.smartBusiness.category;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CategoryResponse {

    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    /** How many products point here — filled in by the service, guides the delete confirmation. */
    private long productCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
