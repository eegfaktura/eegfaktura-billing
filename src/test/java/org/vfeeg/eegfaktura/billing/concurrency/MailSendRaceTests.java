package org.vfeeg.eegfaktura.billing.concurrency;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.support.ConcurrencyAndMailBase;
import org.vfeeg.eegfaktura.billing.support.Gate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * F7 (#18), status race: two simultaneous sends of one run, called on the service (through the resource
 * the per-tenant lock would serialise them and hide the race). The check "status empty" and the write
 * "IN PROGRESS" are separate statements without a lock.
 */
class MailSendRaceTests extends ConcurrencyAndMailBase {

    private static final String MEMBER_1 = "member1@example.org";
    private static final String MEMBER_2 = "member2@example.org";

    /**
     * Both calls are held where they write "IN PROGRESS" until the other one arrived there too (bounded:
     * with a fix the second call does not get that far, and the first goes on after
     * {@link Gate#NOT_EXPECTED}). Correct: exactly one call sends, the other is refused, every member gets
     * one mail. Today both pass the check and every member gets two mails.
     */
    @Test
    @Disabled("known-errors #18")
    void onlyOneOfTwoSimultaneousSendsSends() {
        UUID runId = finalRun(members(MEMBER_1, MEMBER_2));
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        doAnswer(invocation -> {
            BillingRun run = invocation.getArgument(0);
            if ("IN PROGRESS".equals(run.getMailStatus())) {
                Gate.meet(bothChecked, Gate.NOT_EXPECTED);
            }
            return proceed(billingRunRepositorySpy, invocation);
        }).when(billingRunRepositorySpy).saveAndFlush(any(BillingRun.class));

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            List<Future<String>> sends = List.of(
                    pool.submit(() -> mailService.sendAllBillingDocuments(runId)),
                    pool.submit(() -> mailService.sendAllBillingDocuments(runId)));

            List<String> protocols = new ArrayList<>();
            List<Throwable> refusals = new ArrayList<>();
            for (Future<String> send : sends) {
                try {
                    protocols.add(send.get(Gate.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                } catch (ExecutionException e) {
                    refusals.add(e.getCause());
                }
            }

            assertThat("calls that sent", protocols, hasSize(1));
            assertThat("calls that were refused", refusals, hasSize(1));
            assertThat(refusals.get(0).getMessage(), containsString("nicht moeglich"));
            assertThat(receivedBy(MEMBER_1), arrayWithSize(1));
            assertThat(receivedBy(MEMBER_2), arrayWithSize(1));
            assertThat(run(runId).getMailStatus(), is("SENT"));
        });
    }
}
