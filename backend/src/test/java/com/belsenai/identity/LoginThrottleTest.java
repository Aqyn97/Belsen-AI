package com.belsenai.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

import com.belsenai.common.error.ApiException;
import com.belsenai.identity.security.LoginThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void blockedAddressDoesNotAllocateOrUpdateAccountBuckets() {
        blockAddress();
        Map<?, ?> before = Map.copyOf(buckets());
        assertLimited("new-account", "blocked-address");
        assertLimited("account-0", "blocked-address");
        assertThat(buckets()).isEqualTo(before);
    }

    @Test
    void originalExhaustionAttackLeavesFreshClientEligible() {
        blockAddress();
        for (int i = 0; i < 10005; i++) {
            assertLimited("attack-" + i + "@example.test", "blocked-address");
        }
        assertThat(buckets()).hasSize(51);
        assertThatCode(() -> throttle.check("fresh-account@example.test", "fresh-address"))
                .doesNotThrowAnyException();
        assertThat(buckets()).hasSize(53);
    }

    @Test
    void insufficientCapacityRejectsBothKeysWithoutPartialMutation() {
        fillPairs(4999);
        throttle.check("account-0", "one-more-address");
        assertThat(buckets()).hasSize(9999);
        Map<?, ?> before = Map.copyOf(buckets());
        assertLimited("new-account", "new-address");
        assertThat(buckets()).isEqualTo(before);
        throttle.check("last-account", "address-0");
        assertThat(buckets()).hasSize(10000);
        assertLimited("overflow-account", "overflow-address");
    }

    @Test
    void concurrentRequestsCannotOverbookRemainingCapacity() throws Exception {
        fillPairs(4999);
        int accepted = concurrent(i -> attempt("concurrent-account-" + i, "concurrent-address-" + i));
        assertThat(accepted).isEqualTo(1);
        assertThat(buckets()).hasSize(10000);
    }

    @Test
    void concurrentRequestsCannotExceedAccountLimit() throws Exception {
        int accepted = concurrent(i -> attempt("shared-account", "address-" + i));
        assertThat(accepted).isEqualTo(10);
        assertThat(buckets()).hasSize(11);
        assertLimited("shared-account", "another-address");
    }

    @Test
    void expirationAtWindowBoundaryReclaimsFullStore() {
        fillPairs(5000);
        assertThat(buckets()).hasSize(10000);
        assertLimited("new-account", "new-address");
        now.set(now.get().plusSeconds(900));
        throttle.check("new-account", "new-address");
        assertThat(buckets()).hasSize(2);
        assertThat(buckets().keySet()).isEqualTo(java.util.Set.of("account:new-account", "address:new-address"));
    }

    private void blockAddress() {
        for (int i = 0; i < 50; i++) { throttle.check("account-" + i, "blocked-address"); }
    }

    private void fillPairs(int pairs) {
        for (int i = 0; i < pairs; i++) { throttle.check("account-" + i, "address-" + i); }
    }

    private boolean attempt(String account, String address) {
        try {
            throttle.check(account, address);
            return true;
        } catch (ApiException exception) {
            assertThat(exception.status()).isEqualTo(429);
            return false;
        }
    }

    private void assertLimited(String account, String address) {
        assertThatThrownBy(() -> throttle.check(account, address))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.status()).isEqualTo(429));
    }

    private int concurrent(IntPredicate request) throws Exception {
        int workers = 16;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < workers; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Start gate timed out"); }
                    return request.test(index);
                }));
            }
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { start.countDown(); }
            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) { accepted++; }
            }
            return accepted;
        }
    }

    // Inspect storage after workers finish without exposing mutable state in the production API.
    private Map<?, ?> buckets() {
        return (Map<?, ?>) ReflectionTestUtils.getField(throttle, "buckets");
    }
}
