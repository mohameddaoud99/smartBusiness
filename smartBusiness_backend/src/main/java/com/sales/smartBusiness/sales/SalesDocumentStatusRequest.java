package com.sales.smartBusiness.sales;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesDocumentStatusRequest {

    @NotNull(message = "Status is required")
    private SalesDocumentStatus status;
}
