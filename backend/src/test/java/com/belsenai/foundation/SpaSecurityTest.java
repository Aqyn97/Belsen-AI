package com.belsenai.foundation;

import com.belsenai.web.SpaController;
import com.belsenai.config.SecurityConfig;
import com.belsenai.common.error.ProblemResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(controllers=SpaController.class,excludeAutoConfiguration=UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class,ProblemResponses.class})
class SpaSecurityTest {
    @Autowired MockMvc mvc;
    @Test void explicitDocumentsArePublicButNotCached() throws Exception {
        for(String route:new String[]{"/","/auth","/auth/callback","/app","/app/dashboard","/app/goals","/app/tasks","/app/habits","/app/analytics","/app/coach", "/app/settings"})
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void headDocumentsWorkWithoutOpeningOtherMethods() throws Exception {
        mvc.perform(head("/app/tasks")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/app/tasks").with(csrf()).with(user("owner"))).andExpect(status().isForbidden());
    }
    @Test void hashedAssetsHaveExplicitMimeAndImmutableCache() throws Exception {
        mvc.perform(get("/assets/fixture-12345678.js")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"))
            .andExpect(header().string("Cache-Control","public, max-age=31536000, immutable"));
    }
    @Test void missingAssetsAndSourceMapsAreJsonErrorsNotHtml() throws Exception {
        for(String path:new String[]{"/assets/missing-12345678.js","/assets/fixture.js","/assets/fixture-12345678.js.map","/assets/application.yml"})
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void privateAndUnknownNamespacesNeverReceiveSpaHtml() throws Exception {
        for(String path:new String[]{"/api/v1/me","/api/v1/unknown","/actuator/health","/.env","/application.yml","/app/admin","/assets/private/nested.js"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void publicShellDoesNotAuthorizeApiOrCsrfBypass() throws Exception {
        mvc.perform(get("/api/v1/unknown").with(user("owner"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/register")).andExpect(status().isForbidden());
    }
    @Test void shellRetainsSecurityHeaders() throws Exception {
        mvc.perform(get("/").secure(true)).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("X-Frame-Options","DENY")).andExpect(header().string("Strict-Transport-Security",containsString("max-age=")))
            .andExpect(header().string("Content-Security-Policy",containsString("script-src 'self'")))
            .andExpect(header().string("Permissions-Policy",containsString("camera=()")));
    }
}
