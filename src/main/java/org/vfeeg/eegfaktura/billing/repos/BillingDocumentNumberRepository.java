package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;

import java.util.UUID;

@Repository
public interface BillingDocumentNumberRepository extends JpaRepository<BillingDocumentNumber, UUID> {

    @Query(value = "SELECT max(b.sequenceNumber) AS maxSequenceNumber" +
            " FROM BillingDocumentNumber b" +
            " WHERE b.tenantId = ?1 and b.year = ?2 and b.prefix = ?3" +
            " GROUP BY b.tenantId, b.year, b.prefix")
    public Long getMaxSequenceNumber(String tenantId, int year, String prefix);

}
