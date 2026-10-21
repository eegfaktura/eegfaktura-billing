package org.vfeeg.eegfaktura.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.vfeeg.eegfaktura.billing.config.AsyncConfig;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;

import java.util.UUID;

/**
 * Startet den Rechen-Teil eines Abrechnungslaufs asynchron. Eigene Klasse,
 * damit der Spring-Proxy greift (@Async wirkt nicht bei Self-Invocation).
 * Der fruehere catch-all aus BillingService.doBilling lebt jetzt hier:
 * jede Exception aus der Berechnung fuehrt zu Status FAILED mit persistierter
 * fachlicher Fehlerzusammenfassung (keine Stacktraces im errorSummary).
 */
@Component
@Slf4j
public class BillingRunLauncher {

    private final BillingService billingService;

    public BillingRunLauncher(final BillingService billingService) {
        this.billingService = billingService;
    }

    @Async(AsyncConfig.BILLING_RUN_EXECUTOR)
    public void launch(UUID billingRunId, DoBillingParams doBillingParams) {
        try {
            billingService.executeBillingRun(billingRunId, doBillingParams);
        } catch (Exception e) {
            log.error("Abrechnung fehlgeschlagen (billingRunId={})", billingRunId, e);
            billingService.markBillingRunFailed(billingRunId, toErrorSummary(e));
        }
    }

    /**
     * errorSummary geht via BillingRunDTO an den Client: fachliche Meldungen
     * (eigene RuntimeExceptions) durchreichen, technische Exceptions (DB/
     * Persistenz - koennen SQL-/Schema-Details enthalten) nur generisch
     * zusammenfassen; Details stehen im Server-Log (Security-Review-Finding).
     */
    private static String toErrorSummary(Exception e) {
        boolean technical = e instanceof org.springframework.dao.DataAccessException
                || e instanceof jakarta.persistence.PersistenceException
                || e instanceof java.sql.SQLException
                || e.getMessage() == null;
        return technical
                ? "Abrechnung fehlgeschlagen: technischer Fehler - Details im Server-Log."
                : "Abrechnung fehlgeschlagen: " + e.getMessage();
    }
}
