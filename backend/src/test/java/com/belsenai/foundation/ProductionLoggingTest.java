package com.belsenai.foundation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ProductionLoggingTest {
    @Configuration(proxyBeanMethods=false) static class Probe { }
    @Test void productionSuppressesSqlValuesButKeepsSafeApplicationErrors(CapturedOutput output) {
        try(var context=new SpringApplicationBuilder(Probe.class).web(WebApplicationType.NONE)
                .profiles("production").properties("spring.main.banner-mode=off").run()) {
            LoggerFactory.getLogger("org.hibernate.orm.jdbc.error").warn("SQL diagnostic private-fixture@example.test");
            LoggerFactory.getLogger("org.hibernate.orm.jdbc.bind").trace("private-bind-fixture");
            LoggerFactory.getLogger("com.belsenai.common.error.ApiExceptionHandler").error("Safe fixture request identifier");
            assertThat(output.getAll()).doesNotContain("private-fixture@example.test","private-bind-fixture")
                .contains("Safe fixture request identifier");
        }
    }
}
