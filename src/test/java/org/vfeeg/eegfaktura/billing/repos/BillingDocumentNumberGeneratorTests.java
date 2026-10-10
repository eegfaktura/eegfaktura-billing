package org.vfeeg.eegfaktura.billing.repos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the number format and sequence of {@link BillingDocumentNumberGeneratorImpl} with a mocked
 * repository. Uniqueness under concurrency (F4 integration part) needs the database: M5.
 */
@ExtendWith(MockitoExtension.class)
class BillingDocumentNumberGeneratorTests {

    private static final String TENANT = "TE100100";

    @Mock
    private BillingDocumentNumberRepository repository;

    private BillingDocumentNumberGeneratorImpl generator;

    @BeforeEach
    void setUp() {
        generator = new BillingDocumentNumberGeneratorImpl(repository);
        // no row yet = null from the query (Mockito would answer 0 for a Long)
        lenient().when(repository.getMaxSequenceNumber(anyString(), anyInt(), anyString())).thenReturn(null);
        lenient().when(repository.saveAndFlush(any(BillingDocumentNumber.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void nextIsMaxPlusOneAndFormattedWithPrefixYearAndDigits() {
        when(repository.getMaxSequenceNumber(TENANT, 2024, "R")).thenReturn(41L);

        BillingDocumentNumber next = generator.getNext(TENANT, 2024, "R", 1L, 5);

        assertThat(next.getDocumentNumber(), is("R202400042"));
        assertThat(next.getSequenceNumber(), is(42L));
        assertThat(next.getTenantId(), is(TENANT));
        assertThat(next.getYear(), is(2024));
        assertThat(next.getPrefix(), is("R"));
    }

    @Test
    void returnsWhatTheRepositorySaved() {
        BillingDocumentNumber saved = new BillingDocumentNumber();
        when(repository.saveAndFlush(any(BillingDocumentNumber.class))).thenReturn(saved);

        assertThat(generator.getNext(TENANT, 2024, "R", null, 5), is(sameInstance(saved)));
    }

    @Test
    void firstNumberOfASequenceIsTheStartValue() {
        BillingDocumentNumber next = generator.getNext(TENANT, 2025, "G", 100L, 4);

        assertThat(next.getSequenceNumber(), is(100L));
        assertThat(next.getDocumentNumber(), is("G20250100"));
    }

    @Test
    void startNullMeansZero() {
        BillingDocumentNumber next = generator.getNext(TENANT, 2025, "G", null, 3);

        assertThat(next.getSequenceNumber(), is(0L));
        assertThat(next.getDocumentNumber(), is("G2025000"));
    }

    @Test
    void startIsIgnoredOnceTheSequenceExists() {
        when(repository.getMaxSequenceNumber(TENANT, 2025, "G")).thenReturn(7L);

        assertThat(generator.getNext(TENANT, 2025, "G", 100L, 3).getDocumentNumber(), is("G2025008"));
    }

    @Test
    void lengthOutsideOneToTenFallsBackToFive() {
        when(repository.getMaxSequenceNumber(anyString(), anyInt(), anyString())).thenReturn(6L);

        assertThat(generator.getNext(TENANT, 2024, "R", null, 0).getDocumentNumber(), is("R202400007"));
        assertThat(generator.getNext(TENANT, 2024, "R", null, -3).getDocumentNumber(), is("R202400007"));
        assertThat(generator.getNext(TENANT, 2024, "R", null, 11).getDocumentNumber(), is("R202400007"));
    }

    @Test
    void lengthOneAndTenAreKept() {
        when(repository.getMaxSequenceNumber(anyString(), anyInt(), anyString())).thenReturn(6L);

        assertThat(generator.getNext(TENANT, 2024, "R", null, 1).getDocumentNumber(), is("R20247"));
        assertThat(generator.getNext(TENANT, 2024, "R", null, 10).getDocumentNumber(), is("R20240000000007"));
    }

    @Test
    void sequenceLongerThanLengthIsNotCut() {
        when(repository.getMaxSequenceNumber(TENANT, 2024, "R")).thenReturn(1234L);

        assertThat(generator.getNext(TENANT, 2024, "R", null, 2).getDocumentNumber(), is("R20241235"));
    }

    @Test
    void prefixNullIsEmptyPrefix() {
        BillingDocumentNumber next = generator.getNext(TENANT, 2024, null, null, 5);

        verify(repository).getMaxSequenceNumber(TENANT, 2024, "");
        assertThat(next.getPrefix(), is(""));
        assertThat(next.getDocumentNumber(), is("202400000"));
    }

    @Test
    void prefixEmptyGivesNumberWithoutPrefix() {
        when(repository.getMaxSequenceNumber(TENANT, 2024, "")).thenReturn(9L);

        assertThat(generator.getNext(TENANT, 2024, "", null, 5).getDocumentNumber(), is("202400010"));
    }

    @Test
    void prefixWithBlankIsPrintedTrimmed() {
        assertThat(generator.getNext(TENANT, 2024, " R", null, 5).getDocumentNumber(), is("R202400000"));
    }

    @Test
    void sequencesAreKeptPerTenantAndYear() {
        when(repository.getMaxSequenceNumber("RC100001", 2024, "R")).thenReturn(3L);

        generator.getNext("RC100001", 2024, "R", null, 5);

        ArgumentCaptor<BillingDocumentNumber> saved = ArgumentCaptor.forClass(BillingDocumentNumber.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTenantId(), is("RC100001"));
        assertThat(saved.getValue().getSequenceNumber(), is(4L));
    }

    /**
     * F4: " R" and "R" print the same number, so they must share one sequence. Today " R" starts its own
     * sequence at the start value and prints a number that "R" already issued.
     */
    @Test
    @Disabled("known-errors #15")
    void prefixWithBlankContinuesTheSequenceOfTheTrimmedPrefix() {
        lenient().when(repository.getMaxSequenceNumber(TENANT, 2024, "R")).thenReturn(41L);

        BillingDocumentNumber next = generator.getNext(TENANT, 2024, " R", null, 5);

        assertThat(next.getDocumentNumber(), is("R202400042"));
        assertThat(next.getSequenceNumber(), is(42L));
    }
}
