package com.sales.smartBusiness.numbering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NumberingSequenceRepository extends JpaRepository<NumberingSequence, Long> {

    List<NumberingSequence> findByCompanyId(Long companyId);

    Optional<NumberingSequence> findByCompanyIdAndDocumentType(Long companyId, DocumentType documentType);

    /** Serialises concurrent {@code allocate} calls for the same company and type. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM NumberingSequence s WHERE s.company.id = :companyId AND s.documentType = :type")
    Optional<NumberingSequence> lockByCompanyIdAndDocumentType(@Param("companyId") Long companyId,
                                                              @Param("type") DocumentType type);
}
