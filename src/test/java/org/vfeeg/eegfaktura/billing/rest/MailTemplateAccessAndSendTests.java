package org.vfeeg.eegfaktura.billing.rest;

import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService;

import java.util.List;
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/mailtemplates end to end: Tenant header, tenant isolation, role, test mail and the text a
 * send run uses.
 */
class MailTemplateAccessAndSendTests extends MailTemplateIntegrationBase {

    @Test
    void withoutTenantHeaderNothingIsAnswered() throws Exception {
        // the mail texts take the community only from the Tenant header; without it every handler refuses
        mvc.perform(get("/api/mailtemplates").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + KEY).header("Authorization", "Bearer admin").header("Tenant", " "))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tenant isolation: files and texts of another tenant are 403 (load, then check), wrong header 403")
    void tenantIsolation() throws Exception {
        cleanup(TENANT_A);
        String fileOfA = uploadImage(TENANT_A, PNG);

        // tenant B is in the token, the file belongs to A: loaded, then refused
        mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin", TENANT_B)).andExpect(status().isForbidden());
        mvc.perform(as(delete(BASE + KEY + "/files/" + fileOfA), "admin", TENANT_B)).andExpect(status().isForbidden());
        // the user of A only: B is not in the token
        // (JwtRequestFilter refuses before any controller; it throws out of the filter chain)
        assertThrows(AccessDeniedException.class, () ->
                mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin-no-mail", TENANT_B)));
        mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin-no-mail", TENANT_A)).andExpect(status().isOk());
        cleanup(TENANT_A);
    }

    @Test
    @DisplayName("Without EEG_ADMIN or without token: 403 / 401")
    void roleRequired() throws Exception {
        int noToken = mvc.perform(get("/api/mailtemplates").header("Tenant", TENANT_A)).andReturn().getResponse().getStatus();
        assertTrue(noToken == 401 || noToken == 403, "status " + noToken);
        mvc.perform(as(get("/api/mailtemplates"), "user", TENANT_A)).andExpect(status().isForbidden());
        mvc.perform(as(put(BASE + KEY), "user", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                .content(body("subject", "x", "bodyHtml", "<p>a</p>", "version", 0))).andExpect(status().isForbidden());
        assertTrue(templateRepository.findByTenantIdAndMailKey(TENANT_A, KEY).isEmpty());
    }

    @Test
    @DisplayName("Test mail: me = e-mail claim, eeg = master data address, [Test] prefix, no cc, real EEG data")
    void testMail() throws Exception {
        cleanup(TENANT_A);
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "{document.type} - {eeg.name}",
                                "bodyHtml", "<p>Hallo <span data-ph=\"recipient.name\">x</span>, <span data-ph=\"eeg.email\">x</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("admin@example.com"));
        MimeMessage message = sentMessage();
        assertEquals("[Test] Rechnung - Energiegemeinschaft Holy Grail", message.getSubject());
        assertEquals("admin@example.com", message.getAllRecipients()[0].toString());
        assertEquals(1, message.getAllRecipients().length);
        assertNull(message.getRecipients(MimeMessage.RecipientType.CC));
        String raw = raw(message);
        assertTrue(raw.contains("Max Mustermann"), raw);
        assertTrue(raw.contains("eeg-holy-grail") , raw);

        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("eeg-holy-grail@gmx.at"));
        Address[] to = sentMessage().getAllRecipients();
        assertEquals("eeg-holy-grail@gmx.at", to[0].toString());

        // the mail server refuses: 502 with a message
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("down"))
                .when(javaMailSender).send(any(MimeMessage.class));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").exists());

        reset(javaMailSender);
        // no e-mail claim; tenant B has no master data; free recipients are not possible
        mvc.perform(as(post(BASE + KEY + "/test"), "admin-no-mail", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipient"));
        // tenant B has no participants, so no row in base.billing_masterdata
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Für die Gemeinschaft ist keine E-Mail-Adresse bekannt."));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "attacker@example.com", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest());
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Master data and billing config are found when their stored tenant case differs from the header")
    void tenantCaseInsensitiveLookups() throws Exception {
        String tenant = "TE400400";
        if (billingConfigRepository.findFirstByTenantIdIgnoreCase(tenant).isEmpty()) {
            jdbcTemplate.update("insert into base.billing_masterdata (tenant_id, eec_name, eec_email) values (?, ?, ?)",
                    "te400400", "EEG Kleinschreibung", "klein@example.com; kein-gueltiger ;obmann@example.com");
            BillingConfig config = new BillingConfig();
            config.setTenantId("te400400");
            config.setFooterText("Fuss <b>fett</b> &amp; mehr");
            billingConfigRepository.save(config);
        }
        when(jwtTokenService.validateTokenAndGetAuthentication("admin-c")).thenReturn(new JwtAuthentication("admin-c",
                List.of(new Authority(tenant), new Authority("ROLE_EEG_ADMIN")), false, "c@example.com"));

        mvc.perform(as(post(BASE + KEY + "/preview"), "admin-c", tenant).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "{eeg.name}", "bodyHtml",
                                "<p><span data-ph=\"billing.footerText\">F</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("EEG Kleinschreibung"))
                .andExpect(jsonPath("$.html").value(containsString("Fuss <b>fett</b> &amp; mehr")));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin-c", tenant).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("klein@example.com; obmann@example.com"));
        assertEquals(2, sentMessage().getAllRecipients().length);
    }

    @Test
    @DisplayName("Send run: a stored text that cannot be rendered falls back to the default")
    void prepareForSendFallsBack() {
        String tenant = "TE300300";
        templateRepository.findByTenantIdAndMailKey(tenant, KEY).ifPresent(templateRepository::delete);
        BillingMailTemplate broken = new BillingMailTemplate();
        broken.setTenantId(tenant);
        broken.setMailKey(KEY);
        broken.setSubject("x");
        broken.setBodyHtml("<p><span data-ph=\"member.removed\">x</span></p>");
        broken.setVersion(1);
        broken.setDefaultVersion(1);
        templateRepository.save(broken);

        MailTemplateService.Prepared prepared = mailTemplateService.prepareForSend(tenant.toLowerCase());
        assertFalse(prepared.customized());
        assertTrue(prepared.failure() != null);
        assertEquals(prepared.entry().defaultBodyHtml(), prepared.bodyHtml());

        templateRepository.delete(templateRepository.findByTenantIdAndMailKey(tenant, KEY).orElseThrow());
        assertTrue(mailTemplateService.prepareForSend(tenant).failure() == null);
    }
}
