package com.sales.smartBusiness.branch;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BranchResponse {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private BranchStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
