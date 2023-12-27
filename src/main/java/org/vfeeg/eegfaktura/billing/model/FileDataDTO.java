package org.vfeeg.eegfaktura.billing.model;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class FileDataDTO {

    private UUID id;
    private String tenantId;
    private String name;
    private String mimeType;
    private byte[] data;

}
