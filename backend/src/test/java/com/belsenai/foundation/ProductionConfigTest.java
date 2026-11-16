package com.belsenai.foundation;
import com.belsenai.config.ProductionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class ProductionConfigTest {
    MockEnvironment valid() { var env=new MockEnvironment().withProperty("belsen.public-base-url","https://belsen.example");env.setActiveProfiles("production");return env; }
    @Test void acceptsOneHttpsOrigin() { assertThatCode(()->new ProductionConfig(valid())).doesNotThrowAnyException(); }
    @Test void rejectsMissingOrigin() { assertThatThrownBy(()->new ProductionConfig(new MockEnvironment())).isInstanceOf(IllegalStateException.class); }
    @Test void rejectsHttpAndAmbiguousOrigins() {
        for(String origin:new String[]{"http://belsen.example","https://user@belsen.example","https://belsen.example/path","https://belsen.example/","https://belsen.example?query=x","https://belsen.example#part","not-an-origin","https://belsen.example:0","https://belsen.example:65536"})
            assertThatThrownBy(()->new ProductionConfig(valid().withProperty("belsen.public-base-url",origin))).isInstanceOf(IllegalStateException.class);
    }
    @Test void googleCallbackMustMatchProductionOrigin() {
        var env=valid().withProperty("belsen.google.enabled","true");
        assertThatCode(()->new ProductionConfig(env)).doesNotThrowAnyException();
        env.withProperty("belsen.google.redirect-uri","https://untrusted.example/callback");
        assertThatThrownBy(()->new ProductionConfig(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsAccidentalLocalProfile() { var env=valid();env.setActiveProfiles("production","local");assertThatThrownBy(()->new ProductionConfig(env)).isInstanceOf(IllegalStateException.class); }
    @Test void rejectsWeakenedCookiesAndUnrestrictedForwarding() {
        for(String key:new String[]{"server.servlet.session.cookie.secure","server.servlet.session.cookie.http-only","server.servlet.session.cookie.same-site","server.forward-headers-strategy"})
            assertThatThrownBy(()->new ProductionConfig(valid().withProperty(key,"false"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void disablingDirectTlsRequiresExplicitRestrictedProxyMode() {
        var env=valid().withProperty("server.ssl.enabled","false");
        assertThatThrownBy(()->new ProductionConfig(env)).isInstanceOf(IllegalStateException.class);
        env.withProperty("server.forward-headers-strategy","native");
        assertThatCode(()->new ProductionConfig(env)).doesNotThrowAnyException();
        for(String proxies:new String[]{"",".*","^.*$"})
            assertThatThrownBy(()->new ProductionConfig(env.withProperty("server.tomcat.remoteip.internal-proxies",proxies))).isInstanceOf(IllegalStateException.class);
    }
}
