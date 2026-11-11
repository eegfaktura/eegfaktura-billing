package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.model.MeteringPoint;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Consumer amounts and the participant fee per participant of a run.
 * Producer sign not tested: accepted, not changed in billing, known-errors #24.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantAmountServiceTests {

    private static final UUID RUN_ID = UUID.randomUUID();
    private static final String PARTICIPANT = "6f1b9f0e-0000-4000-8000-000000000001";

    @Mock private BillingRunRepository billingRunRepository;
    @Mock private BillingDocumentItemRepository billingDocumentItemRepository;
    @Mock private BillingDocumentRepository billingDocumentRepository;
    @Mock private BillingDocumentFileService billingDocumentFileService;

    private ParticipantAmountService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantAmountService(billingRunRepository, billingDocumentItemRepository,
                billingDocumentRepository, billingDocumentFileService);
        lenient().when(billingRunRepository.findById(RUN_ID)).thenReturn(Optional.of(new BillingRun()));
        lenient().when(billingDocumentFileService.findByBillingDocumentId(any())).thenReturn(List.of());
    }

    @Test
    void consumerAmountsAndParticipantFeeOfOneDocument() {
        BillingDocument invoice = document(PARTICIPANT);
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(invoice));
        when(billingDocumentItemRepository.findByBillingDocument_Id(invoice.getId())).thenReturn(List.of(
                consumer("AT0030000000000000000000000000001", "12.34"),
                consumer("AT0030000000000000000000000000002", "0.66"),
                item(null, "Mitgliedsbeitrag 2024", "10.00"),
                item(null, BillingService.ZAEHLPUNKTGEBUEHR_TEXT + " AT...01", "1.20"),
                item(null, BillingService.ZAEHLPUNKTGEBUEHR_TEXT + " AT...02", "1.20")));

        List<ParticipantAmount> amounts = service.getParticipantAmountsByBillingRunId(RUN_ID);

        assertThat(amounts, hasSize(1));
        ParticipantAmount amount = amounts.get(0);
        assertThat(amount.getId(), is(UUID.fromString(PARTICIPANT)));
        assertThat(amount.getMeteringPoints().stream().map(MeteringPoint::getId).toList(),
                contains("AT0030000000000000000000000000001", "AT0030000000000000000000000000002"));
        assertThat(amount.getMeteringPoints().get(0).getAmount(), comparesEqualTo(new BigDecimal("12.34")));
        assertThat(amount.getMeteringPoints().get(1).getAmount(), comparesEqualTo(new BigDecimal("0.66")));
        assertThat(amount.getAmount(), comparesEqualTo(new BigDecimal("13.00")));
        assertThat(amount.getParticipantFee(), comparesEqualTo(new BigDecimal("10.00")));
        assertThat(amount.getMeteringPointFeeSum(), comparesEqualTo(new BigDecimal("2.40")));
    }

    @Test
    void documentsOfOneParticipantAreAddedUp() {
        BillingDocument first = document(PARTICIPANT);
        BillingDocument second = document(PARTICIPANT);
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(first, second));
        when(billingDocumentItemRepository.findByBillingDocument_Id(first.getId()))
                .thenReturn(List.of(consumer("AT01", "5.00")));
        when(billingDocumentItemRepository.findByBillingDocument_Id(second.getId()))
                .thenReturn(List.of(consumer("AT02", "2.50"), item(null, "Mitgliedsbeitrag", "8.00")));

        List<ParticipantAmount> amounts = service.getParticipantAmountsByBillingRunId(RUN_ID);

        assertThat(amounts, hasSize(1));
        assertThat(amounts.get(0).getMeteringPoints(), hasSize(2));
        assertThat(amounts.get(0).getAmount(), comparesEqualTo(new BigDecimal("7.50")));
        assertThat(amounts.get(0).getParticipantFee(), comparesEqualTo(new BigDecimal("8.00")));
    }

    @Test
    void participantsAreKeptApart() {
        String other = "6f1b9f0e-0000-4000-8000-000000000002";
        BillingDocument anna = document(PARTICIPANT);
        BillingDocument bert = document(other);
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna, bert));
        when(billingDocumentItemRepository.findByBillingDocument_Id(anna.getId()))
                .thenReturn(List.of(consumer("AT01", "5.00")));
        when(billingDocumentItemRepository.findByBillingDocument_Id(bert.getId()))
                .thenReturn(List.of(consumer("AT02", "3.00")));

        List<ParticipantAmount> amounts = service.getParticipantAmountsByBillingRunId(RUN_ID);

        assertThat(amounts, hasSize(2));
        ParticipantAmount bertAmount = amounts.stream()
                .filter(a -> a.getId().equals(UUID.fromString(other))).findFirst().orElseThrow();
        assertThat(bertAmount.getAmount(), comparesEqualTo(new BigDecimal("3.00")));
        assertThat(bertAmount.getParticipantFee(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void firstDocumentFileIsLinked() {
        BillingDocument invoice = document(PARTICIPANT);
        BillingDocumentFileDTO pdf = new BillingDocumentFileDTO();
        BillingDocumentFileDTO second = new BillingDocumentFileDTO();
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(invoice));
        when(billingDocumentItemRepository.findByBillingDocument_Id(invoice.getId())).thenReturn(List.of());
        when(billingDocumentFileService.findByBillingDocumentId(invoice.getId())).thenReturn(List.of(pdf, second));

        ParticipantAmount amount = service.getParticipantAmountsByBillingRunId(RUN_ID).get(0);

        assertThat(amount.getBillingDocumentFileDTOs(), hasSize(1));
        assertThat(amount.getBillingDocumentFileDTOs().get(0), is(sameInstance(pdf)));
        assertThat(amount.getAmount(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void runWithoutDocumentsGivesNoAmounts() {
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of());

        assertThat(service.getParticipantAmountsByBillingRunId(RUN_ID), is(empty()));
    }

    @Test
    void unknownRunIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(billingRunRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getParticipantAmountsByBillingRunId(unknown));
    }

    private static BillingDocument document(String participantId) {
        return BillingDocument.builder().id(UUID.randomUUID()).participantId(participantId).build();
    }

    private static BillingDocumentItem consumer(String meteringPointId, String grossValue) {
        return BillingDocumentItem.builder().meteringPointId(meteringPointId)
                .meteringPointType(MeteringPointType.CONSUMER).text("Energiebezug " + meteringPointId)
                .grossValue(new BigDecimal(grossValue)).build();
    }

    private static BillingDocumentItem item(String meteringPointId, String text, String grossValue) {
        return BillingDocumentItem.builder().meteringPointId(meteringPointId).text(text)
                .grossValue(new BigDecimal(grossValue)).build();
    }
}
