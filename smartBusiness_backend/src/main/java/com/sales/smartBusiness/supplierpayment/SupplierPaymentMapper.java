package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.common.BaseMapperConfig;
import com.sales.smartBusiness.payment.PaymentRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class)
interface SupplierPaymentMapper {

    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "invoiceReference", source = "invoice.reference")
    @Mapping(target = "supplierName", source = "invoice.supplier.name")
    SupplierPaymentResponse toResponse(SupplierPayment payment);

    /** A payment to a supplier asks for the same things as one from a customer: the request is shared. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "status", ignore = true)
    SupplierPayment toEntity(PaymentRequest request);
}
