package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.JoiningFeeRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoiningFeeRecordRepository extends JpaRepository<JoiningFeeRecord, UUID> {

    List<JoiningFeeRecord> findByTenantId(String tenantId);

    Optional<JoiningFeeRecord> findByTenantIdAndParticipantId(String tenantId, String participantId);
}
