package com.belsenai.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/** Refuse an incomplete production package or accidentally weakened session/proxy settings. */
@Configuration(proxyBeanMethods=false)
@Profile("production")
public class ProductionConfig {
    public ProductionConfig(Environment env) {
        if(Arrays.asList(env.getActiveProfiles()).contains("local")) fail();
        try {
            URI origin=URI.create(env.getRequiredProperty("belsen.public-base-url"));
            if(!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                    || origin.getPort()==0 || origin.getPort()>65535
                    || origin.getQuery()!=null || origin.getFragment()!=null || (origin.getPath()!=null && !origin.getPath().isEmpty())) fail();
        } catch(IllegalArgumentException invalid) { fail(); }
        if(!env.getProperty("server.servlet.session.cookie.secure",Boolean.class,true)
                || !env.getProperty("server.servlet.session.cookie.http-only",Boolean.class,true)
                || !"lax".equalsIgnoreCase(env.getProperty("server.servlet.session.cookie.same-site","lax"))) fail();
        if(env.getProperty("belsen.google.enabled",Boolean.class,false)) {
            String expected=env.getRequiredProperty("belsen.public-base-url")+"/login/oauth2/code/google";
            if(!expected.equals(env.getProperty("belsen.google.redirect-uri",expected))) fail();
        }
        String forwarded=env.getProperty("server.forward-headers-strategy","none");
        if(!forwarded.equalsIgnoreCase("none") && !forwarded.equalsIgnoreCase("native")) fail();
        boolean tls=env.getProperty("server.ssl.enabled",Boolean.class,true);
        if(!tls && !forwarded.equalsIgnoreCase("native")) fail();
        if(forwarded.equalsIgnoreCase("native")) {
            String proxies=env.getProperty("server.tomcat.remoteip.internal-proxies","127[.]0[.]0[.]1|::1");
            if(proxies.isBlank() || Arrays.asList(".*",".+","^.*$","^.+$").contains(proxies)) fail();
        }
        if(!new ClassPathResource("static/index.html").exists())
            throw new IllegalStateException("Production requires the packaged SPA. Use scripts/build-production.sh.");
    }
    private static void fail() { throw new IllegalStateException("Invalid production origin, profile or session/proxy configuration. See deployment documentation."); }
}
