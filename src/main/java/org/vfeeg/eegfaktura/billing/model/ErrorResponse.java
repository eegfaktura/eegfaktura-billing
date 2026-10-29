package org.vfeeg.eegfaktura.billing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ErrorResponse {

    private Integer httpStatus;
    private String exception;
    private String message;
    private List<FieldError> fieldErrors;
    /** Text for the user, read by eegfaktura-web's handleErrors; only set where a message is meant for the user. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

}
