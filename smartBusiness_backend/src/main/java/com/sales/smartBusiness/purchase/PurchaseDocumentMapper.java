package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
interface PurchaseDocumentMapper {

    /** The documents made from this one are looked up by the service. */
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceReference", source = "source.reference")
    @Mapping(target = "sourceType", source = "source.type")
    @Mapping(target = "derived", ignore = true)
    PurchaseDocumentResponse toResponse(PurchaseDocument document);

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    PurchaseDocumentSummaryResponse toSummary(PurchaseDocument document);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sourceLineId", source = "sourceLine.id")
    @Mapping(target = "fulfilledQuantity", ignore = true)
    @Mapping(target = "remainingQuantity", ignore = true)
    @Mapping(target = "sourceRemaining", ignore = true)
    PurchaseDocumentLineResponse toLineResponse(PurchaseDocumentLine line);

    PurchaseDocumentTaxResponse toTaxResponse(PurchaseDocumentTax tax);

    /** Only the plain header fields: the supplier, the lines and the taxes are the service's job. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "total", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "creditedAmount", ignore = true)
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "taxes", ignore = true)
    PurchaseDocument toEntity(PurchaseDocumentRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(PurchaseDocumentRequest request, @MappingTarget PurchaseDocument document);
}
