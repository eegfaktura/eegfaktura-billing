package org.vfeeg.eegfaktura.billing.repos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * known-errors #32 (data part), V1_20: logo and footer images stored before 1.2.0 have no tenant_id. The migration
 * sets it from the one community whose billing configs reference the row as header or footer image, and leaves every
 * other row alone. The rows are written as before 1.2.0, then the migration runs again (it is idempotent).
 */
class FileDataTenantMigrationTests extends BillingScenarioBase {

    private final List<UUID> configs = new ArrayList<>();
    private final List<UUID> files = new ArrayList<>();
    private final List<UUID> documentFiles = new ArrayList<>();
    private final List<UUID> mailFiles = new ArrayList<>();

    @AfterEach
    void removeRows() {
        documentFiles.forEach(id -> jdbc.update("delete from billing_document_file where id = ?", id));
        mailFiles.forEach(id -> jdbc.update("delete from billing_mail_template_file where id = ?", id));
        configs.forEach(id -> jdbc.update("delete from billing_config where id = ?", id));
        files.forEach(id -> jdbc.update("delete from file_data where id = ?", id));
    }

    @Test
    void imagesOfOneCommunityGetItsTenantAndNothingElseChanges() throws IOException {
        UUID logo = file(null);
        UUID footer = file(null);
        UUID shared = file(null);           // referenced by two communities: ownership unclear
        UUID alsoAPdf = file(null);         // a document file as well
        UUID template = file(null);         // also the custom template of a config
        UUID alsoAMailImage = file(null);   // an image of the mail text as well
        UUID unreferenced = file(null);
        UUID alreadySet = file("RC999999");
        // one config per community (unique tenant_id)
        config(tenant, logo, footer, null);
        config(tenant.toLowerCase(), alreadySet, logo, null); // same community, other case: one owner
        config(tenant + "-B", shared, null, null);
        config(tenant + "-C", shared, alsoAPdf, null);
        config(tenant + "-D", template, null, template);
        config(tenant + "-E", alsoAMailImage, null, null);
        documentFile(alsoAPdf);
        mailFile(alsoAMailImage);

        migrate();
        migrate();

        assertThat(tenantOf(logo), equalToIgnoringCase(tenant));
        assertThat(tenantOf(footer), is(tenant));
        assertThat(tenantOf(shared), nullValue());
        assertThat(tenantOf(alsoAPdf), nullValue());
        assertThat(tenantOf(template), nullValue());
        assertThat(tenantOf(alsoAMailImage), nullValue());
        assertThat(tenantOf(unreferenced), nullValue());
        assertThat(tenantOf(alreadySet), is("RC999999"));
    }

    private void migrate() throws IOException {
        jdbc.execute(new ClassPathResource("db/migration/V1_20__migration.sql")
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private UUID file(String tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into file_data (id, date_created, last_updated, mime_type, name, tenant_id)"
                + " values (?, now(), now(), 'image/png', 'logo.png', ?)", id, tenantId);
        files.add(id);
        return id;
    }

    private void config(String tenantId, UUID header, UUID footer, UUID customTemplate) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into billing_config (id, tenant_id, header_image_file_data_id, footer_image_file_data_id,"
                + " custom_template_file_data_id, date_created, last_updated) values (?, ?, ?, ?, ?, now(), now())",
                id, tenantId, header, footer, customTemplate);
        configs.add(id);
    }

    private void documentFile(UUID fileDataId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into billing_document_file (id, date_created, last_updated, file_data_id, tenant_id)"
                + " values (?, now(), now(), ?, ?)", id, fileDataId, tenant);
        documentFiles.add(id);
    }

    private void mailFile(UUID fileDataId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into billing_mail_template_file (id, tenant_id, mail_key, file_data_id, kind, size,"
                + " date_created) values (?, ?, 'billing-document', ?, 'image', 1, now())", id, tenant, fileDataId);
        mailFiles.add(id);
    }

    private String tenantOf(UUID fileDataId) {
        return jdbc.queryForObject("select tenant_id from file_data where id = ?", String.class, fileDataId);
    }
}
