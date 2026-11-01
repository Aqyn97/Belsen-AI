package com.belsenai.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import com.belsenai.common.error.ApiException;
import org.springframework.stereotype.Component;

/** Single-instance, bounded fixed-window protection. Fail closed when capacity is exhausted. */
@Component
public class LoginThrottle {
    private static final int MAX_KEYS = 10000;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;

    public LoginThrottle() { this(Clock.systemUTC()); }
    public LoginThrottle(Clock clock) { this.clock = clock; }

    public synchronized void check(String account, String address) {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> !entry.getValue().expires().isAfter(now));
        String addressKey = "address:" + address;
        Bucket addressBucket = buckets.get(addressKey);
        if (addressBucket != null && addressBucket.count() >= 50) { throw limited(); }

        String accountKey = "account:" + account;
        Bucket accountBucket = buckets.get(accountKey);
        if (accountBucket != null && accountBucket.count() >= 10) { throw limited(); }

        // Reserve room for both keys before mutating either bucket. Rejections allocate nothing.
        int newKeys = (addressBucket == null ? 1 : 0) + (accountBucket == null ? 1 : 0);
        if (newKeys > MAX_KEYS - buckets.size()) { throw limited(); }

        buckets.put(addressKey, increment(addressBucket, now));
        buckets.put(accountKey, increment(accountBucket, now));
    }

    private Bucket increment(Bucket bucket, Instant now) {
        return bucket == null ? new Bucket(1, now.plus(WINDOW))
                : new Bucket(bucket.count() + 1, bucket.expires());
    }

    private ApiException limited() { return new ApiException(429, "Too many authentication attempts. Try again later."); }
    private record Bucket(int count, Instant expires) { }
}
