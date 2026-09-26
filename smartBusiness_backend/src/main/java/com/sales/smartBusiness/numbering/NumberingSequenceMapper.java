package com.sales.smartBusiness.numbering;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class)
public interface NumberingSequenceMapper {

    @Mapping(target = "documentTypeLabel", expression = "java(sequence.getDocumentType().getLabel())")
    @Mapping(target = "preview", expression = "java(sequence.format(java.time.Year.now().getValue()))")
    NumberingSequenceResponse toResponse(NumberingSequence sequence);
}
