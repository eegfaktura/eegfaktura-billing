package org.vfeeg.eegfaktura.billing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional

public class BillingRunService {

    /** 409: the run holds final documents or is being calculated. */
    public static class RunNotDeletableException extends ResponseStatusException {
        public static final String MESSAGE = "Dieser Abrechnungslauf kann nicht gelöscht werden: er läuft gerade"
                + " oder enthält endgültige Belege.";

        public RunNotDeletableException() { super(HttpStatus.CONFLICT, MESSAGE); }
    }

    /** Document number of an INFO document, also in a preview: no number that was issued. */
    private static final String NO_NUMBER = "-";

    private final BillingRunRepository billingRunRepository;
    private final BillingDocumentRepository billingDocumentRepository;
    private final BillingDocumentItemRepository billingDocumentItemRepository;
    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final FileDataRepository fileDataRepository;

    public BillingRunService(final BillingRunRepository billingRunRepository,
                             final BillingDocumentRepository billingDocumentRepository,
                             final BillingDocumentItemRepository billingDocumentItemRepository,
                             final BillingDocumentFileRepository billingDocumentFileRepository,
                             final FileDataRepository fileDataRepository) {
        this.billingRunRepository = billingRunRepository;
        this.billingDocumentRepository = billingDocumentRepository;
        this.billingDocumentItemRepository = billingDocumentItemRepository;
        this.billingDocumentFileRepository = billingDocumentFileRepository;
        this.fileDataRepository = fileDataRepository;
    }

    public List<BillingRunDTO> findAll() {
        final List<BillingRun> billingRuns = billingRunRepository.findAll(Sort.by("id"));
        return billingRuns.stream()
                .map((billingRun) -> mapToDTO(billingRun, new BillingRunDTO()))
                .toList();
    }

    public List<BillingRunDTO> findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
            String tenantId, String clearingPeriodType, String clearingPeriodIdentifier) {

        final List<BillingRun> billingRuns = billingRunRepository.
                findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                        tenantId, clearingPeriodType, clearingPeriodIdentifier
                );
        return billingRuns.stream()
                .map((billingRun) -> mapToDTO(billingRun, new BillingRunDTO()))
                .toList();
    }

    public BillingRunDTO get(final UUID id) {
        return billingRunRepository.findById(id)
                .map(billingRun -> mapToDTO(billingRun, new BillingRunDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingRunDTO billingRunDTO) {
        final BillingRun billingRun = new BillingRun();
        mapToEntity(billingRunDTO, billingRun);
        return billingRunRepository.save(billingRun).getId();
    }

    public void update(final UUID id, final BillingRunDTO billingRunDTO) {
        final BillingRun billingRun = billingRunRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingRunDTO, billingRun);
        billingRunRepository.save(billingRun);
    }

    /**
     * Deletes a run with its preview documents (items, files, PDFs). Final documents are never deleted: a run that
     * holds them is refused (known-errors #22), as is a RUNNING one. A run without documents goes in any other
     * status (platform#189: an empty DONE run would block its period). The row lock holds a concurrent start back
     * until the deletion ended: its claim then finds no run.
     */
    public void delete(final UUID id) {
        final BillingRun billingRun = billingRunRepository.findForDeletion(id)
                .orElseThrow(NotFoundException::new);
        if (!isDeletable(billingRun)) {
            throw new RunNotDeletableException();
        }
        fileDataRepository.deleteByBillingRunId(id);
        billingDocumentFileRepository.deleteByBillingRunId(id);
        billingDocumentItemRepository.deleteByBillingRunId(id);
        billingDocumentRepository.deleteByBillingRunId(id);
        billingRunRepository.delete(billingRun);
    }

    private boolean isDeletable(final BillingRun billingRun) {
        final BillingRunStatus status = billingRun.getRunStatus();
        if (status == BillingRunStatus.RUNNING) {
            return false;
        }
        // a final number protects the document whatever the status of the run says
        if (billingDocumentRepository.existsByBillingRunIdAndDocumentNumberIsNotNullAndDocumentNumberNot(
                billingRun.getId(), NO_NUMBER)) {
            return false;
        }
        return status == BillingRunStatus.NEW || status == BillingRunStatus.FAILED
                || !billingDocumentRepository.existsByBillingRunId(billingRun.getId());
    }

    private BillingRunDTO mapToDTO(final BillingRun billingRun, final BillingRunDTO billingRunDTO) {
        billingRunDTO.setId(billingRun.getId());
        billingRunDTO.setClearingPeriodType(billingRun.getClearingPeriodType());
        billingRunDTO.setClearingPeriodIdentifier(billingRun.getClearingPeriodIdentifier());
        billingRunDTO.setTenantId(billingRun.getTenantId());
        billingRunDTO.setRunStatus(billingRun.getRunStatus());
        billingRunDTO.setRunStatusDateTime(billingRun.getRunStatusDateTime());
        billingRunDTO.setErrorSummary(billingRun.getErrorSummary());
        billingRunDTO.setMailStatus(billingRun.getMailStatus());
        billingRunDTO.setMailStatusDateTime(billingRun.getMailStatusDateTime());
        billingRunDTO.setSendMailProtocol(billingRun.getSendMailProtocol());
        billingRunDTO.setSepaStatus(billingRun.getSepaStatus());
        billingRunDTO.setSepaStatusDateTime(billingRun.getSepaStatusDateTime());
        billingRunDTO.setNumberOfInvoices(billingRun.getNumberOfInvoices());
        billingRunDTO.setNumberOfCreditNotes(billingRun.getNumberOfCreditNotes());
        return billingRunDTO;
    }

    private BillingRun mapToEntity(final BillingRunDTO billingRunDTO, final BillingRun billingRun) {
        billingRun.setClearingPeriodType(billingRunDTO.getClearingPeriodType());
        billingRun.setClearingPeriodIdentifier(billingRunDTO.getClearingPeriodIdentifier());
        billingRun.setTenantId(billingRunDTO.getTenantId());
        billingRun.setRunStatus(billingRunDTO.getRunStatus());
        billingRun.setRunStatusDateTime(billingRunDTO.getRunStatusDateTime());
        billingRun.setMailStatus(billingRunDTO.getMailStatus());
        billingRun.setMailStatusDateTime(billingRunDTO.getMailStatusDateTime());
        billingRun.setSendMailProtocol(billingRunDTO.getSendMailProtocol());
        billingRun.setSepaStatus(billingRunDTO.getSepaStatus());
        billingRun.setSepaStatusDateTime(billingRunDTO.getSepaStatusDateTime());
        billingRun.setNumberOfInvoices(billingRunDTO.getNumberOfInvoices());
        billingRun.setNumberOfCreditNotes(billingRunDTO.getNumberOfCreditNotes());
        return billingRun;
    }


}
