package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class DoBillingResults {

    private String abstractText;
    private UUID billingRunId;
    private List<ParticipantAmount> participantAmounts = new ArrayList<>();

}
