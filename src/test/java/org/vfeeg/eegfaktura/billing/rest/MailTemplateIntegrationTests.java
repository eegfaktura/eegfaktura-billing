package org.vfeeg.eegfaktura.billing.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/mailtemplates end to end: contract of section H for stored texts and their files (default,
 * save, conflict, reset, validation, uploads and limits, drafts).
 */
class MailTemplateIntegrationTests extends MailTemplateIntegrationBase {

    @Test
    @DisplayName("Without a stored text: list and GET show the default (customized=false, version 0)")
    void defaultTemplate() throws Exception {
        cleanup(TENANT_B);
        mvc.perform(as(get("/api/mailtemplates"), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value(KEY))
                .andExpect(jsonPath("$[0].customized").value(false))
                .andExpect(jsonPath("$[0].defaultChanged").value(false));
        mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.subject").value("{document.type} {document.period} - {eeg.name}"))
                .andExpect(jsonPath("$.bodyHtml").value(containsString("data-ph=\"recipient.name\"")))
                .andExpect(jsonPath("$.bodyHtml").value(containsString("src=\"mailfile:eegfaktura-logo\"")))
                .andExpect(jsonPath("$.files.length()").value(0))
                .andExpect(jsonPath("$.limits.maxImages").value(3))
                .andExpect(jsonPath("$.limits.maxAttachments").value(0))
                .andExpect(jsonPath("$.placeholders[?(@.key=='eeg.contactBlock')].block").value(true));
    }

    @Test
    @DisplayName("PUT saves (sanitized), increments the version; a stale version is 409; DELETE resets")
    void saveConflictReset() throws Exception {
        cleanup(TENANT_B);
        putTemplate(TENANT_B, "Beleg {document.type}", "<p onclick=\"x()\">Hallo <span data-ph=\"recipient.name\">N</span></p><script>alert(1)</script>", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedBy").value("admin"))
                .andExpect(jsonPath("$.bodyHtml").value("<p>Hallo <span data-ph=\"recipient.name\">Empfängername</span></p>"));

        putTemplate(TENANT_B, "Neu", "<p>neu</p>", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.message").exists());
        putTemplate(TENANT_B, "Neu", "<p>neu</p>", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(as(get("/api/mailtemplates"), "admin", TENANT_B))
                .andExpect(jsonPath("$[0].customized").value(true))
                .andExpect(jsonPath("$[0].updatedBy").value("admin"));

        cleanup(TENANT_B);
        mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("Validation: unknown placeholder, empty text, long subject, missing version are 400 with field errors")
    void validation() throws Exception {
        putTemplate(TENANT_B, "x", "<p><span data-ph=\"member.iban\">IBAN</span></p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bodyHtml"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("member.iban")));
        putTemplate(TENANT_B, "x {eeg.iban}", "<p>a</p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        putTemplate(TENANT_B, "x", "<script>alert(1)</script><p> </p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bodyHtml"));
        // a block only on the top level
        putTemplate(TENANT_B, "x", "<ul><li>Kontakt: <div data-ph=\"eeg.contactBlock\">K</div></li></ul>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("eeg.contactBlock")));
        putTemplate(TENANT_B, "x", "<p><span data-ph=\"eeg.contactBlock\">K</span></p>", 0)
                .andExpect(status().isBadRequest());
        putTemplate(TENANT_B, "x".repeat(201), "<p>a</p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get(BASE + "consent-request"), "admin", TENANT_B)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Files: PNG 201 and served with the checked type; disguised file, attachment, 4th image, size are 400")
    void files() throws Exception {
        cleanup(TENANT_B);
        String id = uploadImage(TENANT_B, PNG);
        mvc.perform(as(get(BASE + KEY + "/files/" + id), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        // a text file named .png with image/png
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        // no extra PDF for the billing mail
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("kind", "attachment"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("kind"));
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("kind", "video"), "admin", TENANT_B))
                .andExpect(status().isBadRequest());

        // together <= 500 KB
        byte[] big = Arrays.copyOf(PNG, 400 * 1024);
        uploadImage(TENANT_B, big);
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "c.png", "image/png", Arrays.copyOf(PNG, 120 * 1024)))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("500 KB")));
        // at most 3 images
        uploadImage(TENANT_B, PNG);
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "d.png", "image/png", PNG))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("3 Bilder")));

        mvc.perform(as(delete(BASE + KEY + "/files/" + id), "admin", TENANT_B)).andExpect(status().isNoContent());
        mvc.perform(as(get(BASE + KEY + "/files/" + id), "admin", TENANT_B)).andExpect(status().isNotFound());
        mvc.perform(as(get(BASE + KEY + "/files/not-a-uuid"), "admin", TENANT_B)).andExpect(status().isBadRequest());
        // built-in eegfaktura logo: served, not in files, not deletable
        mvc.perform(as(get(BASE + KEY + "/files/eegfaktura-logo"), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(as(delete(BASE + KEY + "/files/eegfaktura-logo"), "admin", TENANT_B)).andExpect(status().isNotFound());
        cleanup(TENANT_B);
        assertEquals(0, read(mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))).get("files").size());
    }

    @Test
    @DisplayName("Parallel uploads are serialized: the 500 KB limit holds")
    void parallelUploadsKeepTheLimit() throws Exception {
        String tenant = "TE500500";
        when(jwtTokenService.validateTokenAndGetAuthentication("admin-p")).thenReturn(new JwtAuthentication("admin-p",
                List.of(new Authority(tenant), new Authority("ROLE_EEG_ADMIN"))));
        mvc.perform(as(delete(BASE + KEY), "admin-p", tenant)).andExpect(status().isNoContent());
        byte[] image = Arrays.copyOf(PNG, 200 * 1024);
        int parallel = 6;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(parallel);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(as(multipart(BASE + KEY + "/files")
                                .file(new MockMultipartFile("file", "p.png", "image/png", image))
                                .param("kind", "image"), "admin-p", tenant))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (var result : results) {
            created += result.get() == 201 ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(2, created, "2 x 200 KB fit into 500 KB, a third does not");
        assertEquals(2, read(mvc.perform(as(get(BASE + KEY), "admin-p", tenant))).get("files").size());
        mvc.perform(as(delete(BASE + KEY), "admin-p", tenant)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Drafts: PUT commits referenced images (also older drafts), refuses missing ones, keeps fresh drafts")
    void imagesInText() throws Exception {
        cleanup(TENANT_A);
        cleanup(TENANT_B);
        String own = uploadImage(TENANT_B, PNG);
        String unused = uploadImage(TENANT_B, PNG);
        String foreign = uploadImage(TENANT_A, PNG);

        JsonNode saved = read(putTemplate(TENANT_B, "x",
                "<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" />"
                        + "<img src=\"https://tracker.example/x.png\" />Text</p>", 0)
                .andExpect(status().isOk()));
        assertEquals("<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" />Text</p>", saved.get("bodyHtml").asText());
        assertEquals(2, saved.get("files").size());
        assertEquals(own, saved.get("files").get(0).get("id").asText());
        assertTrue(saved.get("files").get(0).get("committed").asBoolean());
        // a fresh draft (e.g. of another editor) stays a draft
        assertEquals(unused, saved.get("files").get(1).get("id").asText());
        assertFalse(saved.get("files").get(1).get("committed").asBoolean());
        assertTrue(saved.get("attachmentFileId").isNull());
        assertFalse(saved.get("legacyTemplateActive").asBoolean());

        // the saved version uses "own": DELETE is 409; a draft may be deleted
        mvc.perform(as(delete(BASE + KEY + "/files/" + own), "admin", TENANT_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        String draft = uploadImage(TENANT_B, PNG);
        mvc.perform(as(delete(BASE + KEY + "/files/" + draft), "admin", TENANT_B)).andExpect(status().isNoContent());

        // a draft older than 24 h is no longer listed and is removed by the next save
        jdbcTemplate.update("update billing_mail_template_file set date_created = now() - interval '48 hours' where id = ?",
                UUID.fromString(unused));
        assertEquals(1, read(mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))).get("files").size());
        // an image in the text whose file is gone: 400, never dropped silently
        putTemplate(TENANT_B, "x", "<p>a<img src=\"mailfile:" + UUID.randomUUID() + "\" /></p>", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Ein Bild im Text gibt es nicht (mehr); bitte erneut einfügen."));
        // an image of another tenant is not a file of this mail either
        putTemplate(TENANT_B, "x", "<p>a<img src=\"mailfile:" + foreign + "\" /></p>", 1)
                .andExpect(status().isBadRequest());
        // a referenced draft older than 24 h is still there: the save commits it
        String old = uploadImage(TENANT_B, PNG);
        jdbcTemplate.update("update billing_mail_template_file set date_created = now() - interval '48 hours' where id = ?",
                UUID.fromString(old));
        JsonNode withOld = read(putTemplate(TENANT_B, "x",
                "<p><img src=\"mailfile:" + own + "\" /><img src=\"mailfile:" + old + "\" /></p>", 1)
                .andExpect(status().isOk()));
        assertTrue(withOld.get("bodyHtml").asText().contains("mailfile:" + old));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "select committed from billing_mail_template_file where id = ?", Boolean.class, UUID.fromString(old)));
        // the next save without "own": the committed file goes, the stale draft too
        JsonNode second = read(putTemplate(TENANT_B, "x", "<p>ohne Bild</p>", 2).andExpect(status().isOk()));
        assertEquals(0, second.get("files").size());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from billing_mail_template_file where tenant_id = ?", Integer.class, TENANT_B));
        // no extra PDF for the billing mail
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>", "version", 3,
                                "attachmentFileId", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attachmentFileId"));
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>", "attachmentFileId", null)))
                .andExpect(status().isOk());
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>",
                                "attachmentFileId", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attachmentFileId"));

        // preview: own image as data: URI, sample member
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "{document.type} für {recipient.name}",
                                "bodyHtml", "<p><img src=\"mailfile:" + uploadImage(TENANT_B, PNG) + "\" /></p>"
                                        + "<p><span data-ph=\"recipient.name\">x</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Rechnung für Max Mustermann"))
                .andExpect(jsonPath("$.html").value(containsString("src=\"data:image/png;base64,")))
                .andExpect(jsonPath("$.html").value(containsString("Max Mustermann")))
                .andExpect(jsonPath("$.html").value(not(containsString("mailfile:"))));
        cleanup(TENANT_A);
        cleanup(TENANT_B);
    }
}
