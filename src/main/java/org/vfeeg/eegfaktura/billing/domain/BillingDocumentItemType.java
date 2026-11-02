package org.vfeeg.eegfaktura.billing.domain;

/**
 * Kind of a document item (column {@code billing_document_item.item_type}, stored as text since V1_18).
 * Items written before have none; {@code JoiningFeeHistory} recognises their energy items by the legacy rule.
 */
public enum BillingDocumentItemType {
    ENERGY, PARTICIPANT_FEE, METERING_POINT_FEE, JOINING_FEE
}
