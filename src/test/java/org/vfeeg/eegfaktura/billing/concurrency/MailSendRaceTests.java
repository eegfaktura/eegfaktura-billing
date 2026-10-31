package org.vfeeg.eegfaktura.billing.concurrency;

import org.junit.jupiter.api.Test;
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
 * the per-run lock would refuse the second and hide the race). The check "status empty" and the claim
 * of the mail run are separate statements; the claim is the one with a single winner.
 */
class MailSendRaceTests extends ConcurrencyAndMailBase {

    private static final String MEMBER_1 = "member1@example.org";
    private static final String MEMBER_2 = "member2@example.org";

    /**
     * Both calls are held where they claim the mail run until the other one arrived there too, so both have
     * passed the check (bounded: a call that was refused earlier lets the first go on after
     * {@link Gate#NOT_EXPECTED}). Correct: exactly one call sends, the other is refused, every member gets
     * one mail.
     */
    @Test
    void onlyOneOfTwoSimultaneousSendsSends() {
        UUID runId = finalRun(members(MEMBER_1, MEMBER_2));
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Gate.meet(bothChecked, Gate.NOT_EXPECTED);
            return proceed(billingRunRepositorySpy, invocation);
        }).when(billingRunRepositorySpy).claimMailRun(any(), any());

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
