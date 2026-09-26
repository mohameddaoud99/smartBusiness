package com.sales.smartBusiness.payment;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class)
interface PaymentMapper {

    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "invoiceReference", source = "invoice.reference")
    @Mapping(target = "customerName", source = "invoice.customer.name")
    PaymentResponse toResponse(Payment payment);

    /** The company, the invoice and the status are the service's: a request cannot choose them. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "status", ignore = true)
    Payment toEntity(PaymentRequest request);
}
