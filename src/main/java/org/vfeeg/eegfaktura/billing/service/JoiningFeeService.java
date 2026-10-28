package org.vfeeg.eegfaktura.billing.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.domain.JoiningFeeRecord;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeRecordRequest;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.JoiningFeeRecordRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Joining fee per member of a community (konzept-beitrittsgebuehr.md incl. the extension of 27.10.2026): the state
 * for eegfaktura-web and the records „charge in the next final run“ / „paid outside eegfaktura“. A record is
 * refused (409) once a final document with the joining fee exists - at most one fee per member - and while a final
 * run of the community is running (it reads the records once; {@link FinalRunLock}). Records are stored with the
 * tenant in upper case, whatever case the caller sends.
 */
@Service
@Transactional
public class JoiningFeeService {

    static final int NOTE_MAX_LENGTH = 200;

    private final BillingDocumentRepository billingDocumentRepository;
    private final JoiningFeeRecordRepository joiningFeeRecordRepository;
    private final FinalRunLock finalRunLock;

    public JoiningFeeService(final BillingDocumentRepository billingDocumentRepository,
                             final JoiningFeeRecordRepository joiningFeeRecordRepository,
                             final FinalRunLock finalRunLock) {
        this.billingDocumentRepository = billingDocumentRepository;
        this.joiningFeeRecordRepository = joiningFeeRecordRepository;
        this.finalRunLock = finalRunLock;
    }

    /** The tenant as the records store it (validated case-insensitively, so one spelling for every caller). */
    static String recordTenant(String tenantId) {
        return tenantId.toUpperCase(Locale.ROOT);
    }

    /**
     * Every member with a final document of the community (runs DONE) or with a record: energy billed, the first
     * final document with the joining fee, charge requested (and not billed yet), paid externally.
     */
    @Transactional(readOnly = true)
    public List<JoiningFeeState> findJoiningFeeState(String tenantId) {
        Set<String> energyBilled = billingDocumentRepository.findEnergyBilledParticipants(tenantId,
                BillingService.ZAEHLPUNKTGEBUEHR_TEXT + "%");
        Map<String, JoiningFeeState.Document> fees = billedFees(tenantId);
        Map<String, JoiningFeeRecord> records = joiningFeeRecordRepository.findByTenantId(recordTenant(tenantId))
                .stream()
                .collect(Collectors.toMap(JoiningFeeRecord::getParticipantId, Function.identity()));
        Set<String> participants = new TreeSet<>(billingDocumentRepository.findParticipantsWithFinalDocuments(tenantId));
        participants.addAll(records.keySet());
        return participants.stream().map(participantId -> {
            JoiningFeeState state = new JoiningFeeState();
            state.setParticipantId(participantId);
            state.setEnergyBilled(energyBilled.contains(participantId));
            state.setJoiningFee(fees.get(participantId));
            JoiningFeeRecord record = records.get(participantId);
            if (record != null && record.getKind() == JoiningFeeRecord.Kind.CHARGE_REQUESTED) {
                state.setRequested(state.getJoiningFee() == null);
            }
            if (record != null && record.getKind() == JoiningFeeRecord.Kind.PAID_EXTERNALLY) {
                JoiningFeeState.PaidExternally paid = new JoiningFeeState.PaidExternally();
                paid.setPaidOn(record.getPaidOn());
                paid.setNote(record.getNote());
                state.setPaidExternally(paid);
            }
            return state;
        }).toList();
    }

    /** Creates or replaces the member's record (the two kinds exclude each other: one row per member). */
    public void saveRecord(String tenantId, String participantId, JoiningFeeRecordRequest request, String user) {
        String participant = participant(participantId);
        JoiningFeeRecord validated = validate(request);
        refuseWhileFinalRun(tenantId);
        refuseWhenBilled(tenantId, participant);
        JoiningFeeRecord record = joiningFeeRecordRepository
                .findByTenantIdAndParticipantId(recordTenant(tenantId), participant)
                .orElseGet(() -> {
                    JoiningFeeRecord created = new JoiningFeeRecord();
                    created.setTenantId(recordTenant(tenantId));
                    created.setParticipantId(participant);
                    return created;
                });
        record.setKind(validated.getKind());
        record.setPaidOn(validated.getPaidOn());
        record.setNote(validated.getNote());
        record.setCreatedBy(user);
        record.setCreatedAt(OffsetDateTime.now());
        try {
            joiningFeeRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // a parallel first record of the same member won (unique tenant_id + participant_id)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Für dieses Mitglied wurde gleichzeitig ein anderer Eintrag gespeichert.");
        }
    }

    /** Takes the request or the external payment back; idempotent. */
    public void deleteRecord(String tenantId, String participantId) {
        String participant = participant(participantId);
        refuseWhileFinalRun(tenantId);
        refuseWhenBilled(tenantId, participant);
        joiningFeeRecordRepository.findByTenantIdAndParticipantId(recordTenant(tenantId), participant)
                .ifPresent(joiningFeeRecordRepository::delete);
    }

    /** The member id as billing stores it (lower-case UUID); 400 when it is no UUID. */
    private static String participant(String participantId) {
        try {
            return UUID.fromString(participantId).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest("participantId ist keine gültige Id.");
        }
    }

    /** 409 while a final run of the community runs: it read the records already; the lock is held to the commit. */
    private void refuseWhileFinalRun(String tenantId) {
        if (!finalRunLock.tryLock(tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ein endgültiger Abrechnungslauf dieser "
                    + "Gemeinschaft läuft gerade – die Beitrittsgebühr kann erst danach geändert werden.");
        }
    }

    private void refuseWhenBilled(String tenantId, String participantId) {
        if (billedFees(tenantId).containsKey(participantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dem Mitglied wurde die Beitrittsgebühr bereits verrechnet.");
        }
    }

    private Map<String, JoiningFeeState.Document> billedFees(String tenantId) {
        Map<String, JoiningFeeState.Document> fees = new HashMap<>();
        for (BillingDocumentRepository.BilledJoiningFee fee : billingDocumentRepository.findBilledJoiningFees(tenantId)) {
            fees.computeIfAbsent(fee.getParticipantId(), id -> {
                JoiningFeeState.Document document = new JoiningFeeState.Document();
                document.setDocumentNumber(fee.getDocumentNumber());
                document.setDocumentDate(fee.getDocumentDate());
                return document;
            });
        }
        return fees;
    }

    /** 400 for an unknown kind, a missing, malformed or future payment date, fields of the other kind, a long note. */
    private static JoiningFeeRecord validate(JoiningFeeRecordRequest request) {
        JoiningFeeRecord record = new JoiningFeeRecord();
        if (request == null || StringUtils.isBlank(request.getKind())) {
            throw badRequest("kind fehlt (CHARGE_REQUESTED oder PAID_EXTERNALLY).");
        }
        try {
            record.setKind(JoiningFeeRecord.Kind.valueOf(request.getKind()));
        } catch (IllegalArgumentException e) {
            throw badRequest("kind muss CHARGE_REQUESTED oder PAID_EXTERNALLY sein.");
        }
        String note = StringUtils.trimToNull(request.getNote());
        if (record.getKind() == JoiningFeeRecord.Kind.CHARGE_REQUESTED) {
            if (request.getPaidOn() != null || note != null) {
                throw badRequest("paidOn und note gibt es nur bei PAID_EXTERNALLY.");
            }
            return record;
        }
        if (StringUtils.isBlank(request.getPaidOn())) {
            throw badRequest("Das Zahlungsdatum (paidOn) fehlt.");
        }
        LocalDate paidOn;
        try {
            paidOn = LocalDate.parse(request.getPaidOn().trim());
        } catch (DateTimeParseException e) {
            throw badRequest("Das Zahlungsdatum (paidOn) muss das Format JJJJ-MM-TT haben.");
        }
        if (paidOn.isAfter(LocalDate.now(BillingService.BILLING_ZONE))) {
            throw badRequest("Das Zahlungsdatum darf nicht in der Zukunft liegen.");
        }
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw badRequest("Die Notiz darf höchstens " + NOTE_MAX_LENGTH + " Zeichen lang sein.");
        }
        record.setPaidOn(paidOn);
        record.setNote(note);
        return record;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
