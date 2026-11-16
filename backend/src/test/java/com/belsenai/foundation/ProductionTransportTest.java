package com.belsenai.foundation;
import com.belsenai.common.health.HealthController;
import com.belsenai.common.error.ProblemResponses;
import com.belsenai.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=HealthController.class,excludeAutoConfiguration=UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class,ProblemResponses.class})
@ActiveProfiles("production")
@TestPropertySource(properties="belsen.public-base-url=https://belsen.example")
class ProductionTransportTest {
    @Autowired MockMvc mvc;
    @Test void plainHttpCannotSpoofHttpsWithAnUntrustedForwardedHeader() throws Exception {
        mvc.perform(get("/api/v1/health").header("X-Forwarded-Proto","https"))
            .andExpect(status().is3xxRedirection());
    }
    @Test void httpsHealthWorksWithHsts() throws Exception {
        mvc.perform(get("/api/v1/health").secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isOk())
            .andExpect(header().exists("Strict-Transport-Security"));
    }
}
