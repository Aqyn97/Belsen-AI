package com.belsenai.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Experimental Render-only contract, not approved for production. See docs/RENDER_EXPERIMENT.md. */
@Configuration(proxyBeanMethods = false)
@Profile("render")
public class RenderConfig {
    public RenderConfig(Environment env) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("production")) fail();
        String origin = env.getRequiredProperty("belsen.public-base-url");
        if (!origin.matches("https://[a-z0-9][a-z0-9-]*[.]onrender[.]com")
                || !origin.equals(env.getRequiredProperty("RENDER_EXTERNAL_URL"))) fail();
        if (env.getProperty("server.ssl.enabled", Boolean.class, true)
                || !"native".equals(env.getProperty("server.forward-headers-strategy"))) fail();
        // Only exact IPv4 peers, separated by |. No entire private network or wildcard trust.
        String peers = env.getRequiredProperty("server.tomcat.remoteip.internal-proxies");
        for (String peer : peers.split("\\|", -1)) {
            if (!peer.matches("[0-9]{1,3}(\\[\\.\\][0-9]{1,3}){3}")) fail();
            for (String octet : peer.split("\\[\\.\\]")) {
                if (Integer.parseInt(octet) > 255) fail();
            }
            if (peer.startsWith("0[.]")) fail();
        }
        String url = env.getRequiredProperty("spring.datasource.url");
        // One direct endpoint serves both Hikari and Flyway. A fixed query prevents SSL
        // factory/hostname verifier overrides and duplicate/encoded downgrade parameters.
        if (!url.matches("jdbc:postgresql://ep-[a-z0-9-]+[.][a-z0-9.-]+[.]neon[.]tech:5432/[A-Za-z0-9_-]+"
                + "\\?sslmode=verify-full&sslfactory=org[.]postgresql[.]ssl[.]DefaultJavaSSLFactory"
                + "&connectTimeout=15&socketTimeout=30&tcpKeepAlive=true")
                || url.split("/", 4)[2].contains("-pooler.")) fail();
        for (String flag : new String[]{"belsen.registration.enabled", "belsen.google.enabled", "belsen.coach.enabled"}) {
            if (env.getProperty(flag, Boolean.class, false)) fail();
        }
        if (env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 0) != 3
                || env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, -1) != 0
                || env.getProperty("spring.datasource.hikari.keepalive-time", Long.class, -1L) != 0L) fail();
    }

    private static void fail() {
        throw new IllegalStateException("Invalid free-hosting configuration. See docs/FREE_HOSTING.md; no configuration values are logged.");
    }
}
