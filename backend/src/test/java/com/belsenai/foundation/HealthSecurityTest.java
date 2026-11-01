package com.belsenai.foundation;

import com.belsenai.common.health.HealthController;
import com.belsenai.config.SecurityConfig;
import com.belsenai.common.error.ProblemResponses;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, ProblemResponses.class})
class HealthSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndContainsOnlyStatus() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anonymousPrivateRequestIsUnauthorizedWithoutLoginRedirect() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser
    void authenticatedRequestsAreDeniedOutsideApprovedRoutes() throws Exception {
        mvc.perform(get("/api/v1/internal"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfDoesNotEnableUnimplementedRoutes() throws Exception {
        mvc.perform(post("/api/v1/internal").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void defaultLogoutRouteRemainsDisabled() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthPostIsNotPublic() throws Exception {
        mvc.perform(post("/api/v1/health").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
