package org.vfeeg.eegfaktura.billing.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the catch-all handler writes to the log: no input values from exceptions that quote them. */
class RestExceptionHandlerLogTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previous;

    @BeforeEach
    void attach() {
        previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }

    private ILoggingEvent onlyLine() {
        assertEquals(1, appender.list.size(), "one line at info: " + appender.list);
        return appender.list.get(0);
    }

    @Test
    void databaseRowValuesAreNotLogged() {
        new RestExceptionHandler().handleThrowable(new DataIntegrityViolationException("insert failed",
                new SQLException("duplicate key: Key (email)=(anna@example.at) already exists")));

        ILoggingEvent line = onlyLine();
        assertNull(line.getThrowableProxy(), "no stack trace, it repeats the message");
        assertFalse(line.getFormattedMessage().contains("anna@example.at"));
        assertEquals(DataIntegrityViolationException.class.getName(), line.getKeyValuePairs().get(0).value);
    }

    @Test
    void unreadableBodyIsNotQuoted() {
        new RestExceptionHandler().handleThrowable(new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value \"AT611904300234573201\"",
                new MockHttpInputMessage(new byte[0])));

        assertFalse(onlyLine().getFormattedMessage().contains("AT611904300234573201"));
    }

    @Test
    void otherExceptionsKeepTheStackTrace() {
        new RestExceptionHandler().handleThrowable(new IllegalStateException("broken"));

        ILoggingEvent line = onlyLine();
        assertNotNull(line.getThrowableProxy());
        assertEquals(IllegalStateException.class.getName(), line.getThrowableProxy().getClassName());
    }

    @Test
    void causeChainIsChecked() {
        assertTrue(RestExceptionHandler.quotesInput(new RuntimeException(new SQLException("x"))));
        assertFalse(RestExceptionHandler.quotesInput(new RuntimeException(new IllegalStateException())));
    }
}
