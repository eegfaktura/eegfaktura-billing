package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.stereotype.Component;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;

@Component
public class BillingDocumentNumberGeneratorImpl implements BillingDocumentNumberGenerator {

    private final BillingDocumentNumberRepository billingDocumentNumberRepository;

    public BillingDocumentNumberGeneratorImpl(BillingDocumentNumberRepository billingDocumentNumberRepository) {
        this.billingDocumentNumberRepository = billingDocumentNumberRepository;
    }


    /**
     * Erzeugt eine neue Nummer für BillingDocuments basierend auf den Angaben (Parameters) wie folgt:
     * prefix + jahr + folgenummer.
     * <ul>
     *     <li>Prefix: ist optional</li>
     *     <li>Jahr wird als JJJJ angezeigt</li>
     *     <li>Folgenummer wird mit führenden nullen formattiert. Die Anzahl der führenden Nullen
     *     wird durch den Parameter sequenceNumberLength bestimmt</li>
     * </ul>
     * Hinweis: sequenceNumberLength muss ein Wert zwischen 1 und 10 sein. Sonst wird dieser auf 5 (=default) gestellt
     */
    //
    //
    public BillingDocumentNumber getNext(String tenantId, int year, String prefix, Long start, int sequenceNumberLength) {
        if (sequenceNumberLength<=0 || sequenceNumberLength>10) {
            sequenceNumberLength = 5; //default
        }
        prefix = prefix != null ? prefix : "";
        start = start != null ? start : 0L;
        BillingDocumentNumber next = new BillingDocumentNumber();
        next.setTenantId(tenantId);
        next.setYear(year);
        next.setPrefix(prefix);
        Long nextSequenceNumber = billingDocumentNumberRepository.getMaxSequenceNumber(tenantId, year, prefix);
        nextSequenceNumber = nextSequenceNumber==null? start : nextSequenceNumber+1L;
        next.setSequenceNumber(nextSequenceNumber);
        next.setDocumentNumber(prefix.trim() + year + String.format("%0"+ sequenceNumberLength + "d",
                nextSequenceNumber));
        return billingDocumentNumberRepository.saveAndFlush(next);
    }
}
