package com.sales.smartBusiness.company;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.*;

@Mapper(config = BaseMapperConfig.class)
public interface CompanyMapper {

    /**
     * Logo/stamp data URIs need a disk read, which does not belong in a declarative
     * mapper — {@link CompanyService} fills them in after calling this method.
     */
    @Mapping(target = "logoDataUri", ignore = true)
    @Mapping(target = "stampDataUri", ignore = true)
    CompanyResponse toResponse(Company company);

    /** Status and the stored image references are managed elsewhere, never through this DTO. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "logoPath", ignore = true)
    @Mapping(target = "logoContentType", ignore = true)
    @Mapping(target = "stampPath", ignore = true)
    @Mapping(target = "stampContentType", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CompanyRequest request, @MappingTarget Company company);
}
