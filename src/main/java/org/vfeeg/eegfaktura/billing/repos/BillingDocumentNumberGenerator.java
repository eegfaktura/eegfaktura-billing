package org.vfeeg.eegfaktura.billing.repos;

import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;

public interface BillingDocumentNumberGenerator {
    BillingDocumentNumber getNext(String tenantId, int year, String prefix, Long start, int sequenceNumberLength);
}
