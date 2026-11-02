package org.vfeeg.eegfaktura.billing.config;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.logging.LogText;
import org.vfeeg.eegfaktura.billing.service.BillingRunActionLock;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.model.ErrorResponse;
import org.vfeeg.eegfaktura.billing.model.FieldError;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class RestExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(final NotFoundException exception) {
        final ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setHttpStatus(HttpStatus.NOT_FOUND.value());
        errorResponse.setException(exception.getClass().getSimpleName());
        errorResponse.setMessage(exception.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception) {
        final BindingResult bindingResult = exception.getBindingResult();
        final List<FieldError> fieldErrors = bindingResult.getFieldErrors()
                .stream()
                .map(error -> {
                    final FieldError fieldError = new FieldError();
                    fieldError.setErrorCode(error.getCode());
                    fieldError.setField(error.getField());
                    return fieldError;
                })
                .toList();
        final ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setHttpStatus(HttpStatus.BAD_REQUEST.value());
        errorResponse.setException(exception.getClass().getSimpleName());
        errorResponse.setFieldErrors(fieldErrors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            final ResponseStatusException exception) {
        final ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setHttpStatus(exception.getStatusCode().value());
        errorResponse.setException(exception.getClass().getSimpleName());
        errorResponse.setMessage(exception.getMessage());
        if (exception instanceof BillingRunActionLock.BusyException
                || exception instanceof BillingRunService.RunNotDeletableException) {
            errorResponse.setError(exception.getReason()); // shown by the web as it is
        }
        return new ResponseEntity<>(errorResponse, exception.getStatusCode());
    }

    @ExceptionHandler(Throwable.class)
    @ApiResponse(responseCode = "4xx/5xx", description = "Error")
    public ResponseEntity<ErrorResponse> handleThrowable(final Throwable exception) {
        // through the logging module (JSON, request.id/tenant.id), not stderr (#28)
        if (LogText.quotesInput(exception)) {
            // the message (and the stack trace, which repeats it) may carry request or row values: type only
            log.atError().addKeyValue("error.type", exception.getClass().getName())
                    .log("Unhandled exception (message not logged, it may quote input values)");
            // stack frames at debug, still without the messages
            log.debug("Unhandled exception", LogText.withoutMessages(exception));
        } else {
            log.error("Unhandled exception", exception);
        }
        final ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.setException(exception.getClass().getSimpleName());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
