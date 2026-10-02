package org.vfeeg.eegfaktura.billing.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * One GreenMail SMTP server for the whole test JVM, on a free port chosen at start.
 *
 * <p>Same reason as {@link PostgresContainerHolder}: GreenMail's JUnit extension stops the server after
 * each class and starts it on a new port, while the cached Spring context keeps the mail sender of the
 * old one. Started in the static initializer, never stopped by a test (the JVM ends it). The test
 * properties demand SMTP auth ({@code mail.smtp.auth=true}), so the server gets a user and the mail
 * sender its test-only login.
 */
public final class GreenMailHolder {

    public static final String LOGIN = "billing-test";
    /** Test-only password for the local GreenMail server, not a secret. */
    public static final String PASSWORD = "test-only-not-a-secret";

    public static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        GREEN_MAIL.start();
        GREEN_MAIL.setUser("billing-test@localhost", LOGIN, PASSWORD);
    }

    private GreenMailHolder() {
    }

    public static int port() {
        return GREEN_MAIL.getSmtp().getPort();
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> GREEN_MAIL.getSmtp().getBindTo());
        registry.add("spring.mail.port", GreenMailHolder::port);
        registry.add("spring.mail.username", () -> LOGIN);
        registry.add("spring.mail.password", () -> PASSWORD);
    }
}
