package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
interface SalesDocumentMapper {

    /** The documents made from this one are looked up by the service. */
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceReference", source = "source.reference")
    @Mapping(target = "sourceType", source = "source.type")
    @Mapping(target = "derived", ignore = true)
    SalesDocumentResponse toResponse(SalesDocument document);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.name")
    SalesDocumentSummaryResponse toSummary(SalesDocument document);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sourceLineId", source = "sourceLine.id")
    @Mapping(target = "fulfilledQuantity", ignore = true)
    @Mapping(target = "remainingQuantity", ignore = true)
    @Mapping(target = "sourceRemaining", ignore = true)
    SalesDocumentLineResponse toLineResponse(SalesDocumentLine line);

    SalesDocumentTaxResponse toTaxResponse(SalesDocumentTax tax);

    /** Only the plain header fields: the customer, the lines and the taxes are the service's job. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "total", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "creditedAmount", ignore = true)
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "taxes", ignore = true)
    SalesDocument toEntity(SalesDocumentRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(SalesDocumentRequest request, @MappingTarget SalesDocument document);
}
