package org.vfeeg.eegfaktura.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vfeeg.eegfaktura.billing.domain.*;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.model.MeteringPoint;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.repos.*;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ParticipantAmountService {

    private final BillingRunRepository billingRunRepository;
    private final BillingDocumentItemRepository billingDocumentItemRepository;
    private final BillingDocumentRepository billingDocumentRepository;
    private final BillingDocumentFileService billingDocumentFileService;

    public ParticipantAmountService(final BillingRunRepository billingRunRepository,
                                    final BillingDocumentItemRepository billingDocumentItemRepository,
                                    final BillingDocumentRepository billingDocumentRepository,
                                    final BillingDocumentFileService billingDocumentFileService) {

        this.billingRunRepository = billingRunRepository;
        this.billingDocumentItemRepository = billingDocumentItemRepository;
        this.billingDocumentRepository = billingDocumentRepository;
        this.billingDocumentFileService = billingDocumentFileService;
    }

    public void addParticipantAmountData(ParticipantAmount participantAmount
            , BillingDocument billingDocument
            , List<BillingDocumentItem> billingDocumentItems) {

        billingDocumentItems.stream().filter(billingDocumentItem -> billingDocumentItem.getMeteringPointId()!=null)
                .forEach(billingDocumentItem -> {
                    MeteringPoint meteringPoint = new MeteringPoint();
                    meteringPoint.setId(billingDocumentItem.getMeteringPointId());
                    meteringPoint.setAmount(billingDocumentItem.getMeteringPointType()== MeteringPointType.CONSUMER ?
                            billingDocumentItem.getGrossValue() : billingDocumentItem.getGrossValue().negate());
                    participantAmount.getMeteringPoints().add(meteringPoint);
                    participantAmount.setAmount(participantAmount.getAmount().add(billingDocumentItem.getGrossValue()));
                });

        // Jenes Item ohne einer MeteringPointId ist (aktuell) die Mitgliedsgebühr
        billingDocumentItems.stream().filter(billingDocumentItem -> billingDocumentItem.getMeteringPointId()==null)
                .forEach(billingDocumentItem -> {
                    participantAmount.setParticipantFee(billingDocumentItem.getGrossValue());
                });

        List<BillingDocumentFileDTO> billingDocumentList = billingDocumentFileService
                .findByBillingDocumentId(billingDocument.getId());

        if (billingDocumentList.size()>0) {
            participantAmount.getBillingDocumentFileDTOs().add(billingDocumentList.get(0));
        }
    }

    @Transactional (readOnly = true)
    public List<ParticipantAmount> getParticipantAmountsByBillingRunId(UUID billingRunId) {
        HashMap<UUID, ParticipantAmount> participantAmountsMap = new HashMap<>();
        BillingRun billingRun = billingRunRepository.findById(billingRunId)
                .orElseThrow(() -> new NotFoundException("billingRun not found: "+billingRunId));

        List<BillingDocument> billingDocuments = billingDocumentRepository.findByBillingRunId(billingRunId);
        for (BillingDocument billingDocument : billingDocuments) {
            UUID participantUUID = UUID.fromString(billingDocument.getParticipantId());
            ParticipantAmount participantAmount =  participantAmountsMap.get(participantUUID);
            if (participantAmount == null) {
                participantAmount = new ParticipantAmount();
                participantAmount.setId(participantUUID);
                participantAmountsMap.put(participantUUID, participantAmount);
            }
            List<BillingDocumentItem> billingDocumentItems = billingDocumentItemRepository
                    .findByBillingDocument_Id(billingDocument.getId());
            addParticipantAmountData(participantAmount, billingDocument, billingDocumentItems);
        }

        return participantAmountsMap.values().stream().toList();

    }

}
