package org.vfeeg.eegfaktura.billing.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.vfeeg.eegfaktura.billing.support.LegacyBaseDatabase;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Contract of the master-data view (M6, {@code known-errors.md} #10): every column the entity
 * {@code BillingMasterdata} maps exists in the <b>real</b> legacy view {@code base.billing_masterdata}
 * with a type the field can read. The view is built from the SQL files copied byte-identical from
 * eegfaktura-v3 ({@code src/test/resources/legacy-base/README.md}) in the own database of {@link
 * LegacyBaseDatabase}. Second, names only: v3's {@code billing_masterdata_v3}, which billing reads
 * after a community's cutover.
 */
class LegacyMasterdataViewContractTests {

    static final String V3_VIEW = "contracts/v3view/V190__billing_masterdata_v3.sql";

    /** sha256 of the copied files as recorded in the READMEs; a local edit must fail here. */
    static final Map<String, String> COPIED = Map.of(
            LegacyBaseDatabase.SCRIPTS.get(0), "8dd321dd994763f0702078bdad6c7aa6ee554f5dde5ef049664af9915d01422c",
            LegacyBaseDatabase.SCRIPTS.get(1), "0c38063b20306713031632a2bb3040faa0f778c3cd188ab08d2ae35520852d44",
            LegacyBaseDatabase.SCRIPTS.get(2), "d82172801b0b3f24e3b2e836e9d367043ecac771639054b93912edd8dadd7ebb",
            LegacyBaseDatabase.SCRIPTS.get(3), "699253d230b57cc7be72416a92d6634ca38a311f5ae0565fed0e46e709812113",
            V3_VIEW, "cb9434d332850c2eae8e2e704bc74aa53bbf6f248c4e48bdeb228094301e3dc9");

    static Map<String, String> view;

    @BeforeAll
    static void readTheView() throws Exception {
        try (Connection connection = LegacyBaseDatabase.connection()) {
            view = MasterdataColumns.databaseColumns(connection, "base", "billing_masterdata");
        }
    }

    @Test
    void theRealViewIsBuiltWithItsSixtyFourColumns() {
        assertThat(view.keySet(), hasSize(64));
    }

    @Test
    void everyEntityColumnExistsInTheViewWithAReadableType() {
        assertThat(MasterdataColumns.entityColumns().keySet(), hasSize(62));
        assertThat(MasterdataColumns.problems(MasterdataColumns.entityColumns(), view), is(empty()));
    }

    /** The meter type is an ordinal enum: the view must deliver 0 for a producer and 1 for a consumer. */
    @Test
    void meteringPointTypeIsDeliveredAsTheOrdinal() {
        assertThat(view.get("metering_point_type"), is("integer"));
        assertThat(view.get("participant_sepa_mandate_issue_date"), is("date"));
    }

    /**
     * Shows that the check catches a broken view: a renamed column (here inside a transaction that
     * is rolled back) is reported by its name.
     */
    @Test
    void aRenamedViewColumnIsReportedByName() throws Exception {
        try (Connection connection = LegacyBaseDatabase.connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER VIEW base.billing_masterdata RENAME COLUMN eec_city TO eec_town");
                Map<String, String> broken = MasterdataColumns.databaseColumns(connection, "base", "billing_masterdata");
                assertThat(MasterdataColumns.problems(MasterdataColumns.entityColumns(), broken),
                        contains("missing column eec_city (String field of BillingMasterdata)"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void aTypeTheFieldCannotReadIsReportedByName() {
        Map<String, String> changed = new HashMap<>(view);
        changed.put("tariff_vat_in_percent", "text");
        List<String> problems = MasterdataColumns.problems(MasterdataColumns.entityColumns(), changed);
        assertThat(problems, hasSize(1));
        assertThat(problems.get(0), startsWith("column tariff_vat_in_percent is text, the BigDecimal field"));
    }

    /** After a cutover billing reads v3's view through a UNION: it needs every legacy column by name. */
    @Test
    void v3ViewHasEveryLegacyAndEntityColumnByName() throws Exception {
        Set<String> v3 = MasterdataColumns.v3ViewColumnNames(V3_VIEW);
        assertThat(MasterdataColumns.missing(MasterdataColumns.entityColumns().keySet(), v3), is(empty()));
        assertThat(MasterdataColumns.missing(view.keySet(), v3), is(empty()));
        Set<String> extra = new TreeSet<>(v3);
        extra.removeAll(view.keySet());
        assertThat("v3 has 64 legacy columns plus eeg_id", List.copyOf(extra), contains("eeg_id"));
        assertThat(v3, hasSize(65));
    }

    @Test
    void copiedFilesAreUnchanged() throws Exception {
        for (Map.Entry<String, String> copied : COPIED.entrySet()) {
            try (InputStream in = new ClassPathResource(copied.getKey()).getInputStream()) {
                String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                assertThat(copied.getKey(), sha256, is(copied.getValue()));
            }
        }
    }
}
