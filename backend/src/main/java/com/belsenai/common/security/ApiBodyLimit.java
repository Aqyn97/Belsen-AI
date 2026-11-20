package com.belsenai.common.security;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import com.belsenai.common.error.ProblemResponses;

/** Installed after authentication/CSRF, before MVC; chunked bodies are bounded too. */
public class ApiBodyLimit extends OncePerRequestFilter {
    private final ProblemResponses problems;
    public ApiBodyLimit(ProblemResponses problems) { this.problems=problems; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod()) || !request.getServletPath().startsWith("/api/v1/")) { chain.doFilter(request,response);return; }
        int limit = java.util.Set.of("/api/v1/coach/evidence", "/api/v1/coach/analyses").contains(request.getServletPath()) ? 4096 : 16384;
        if (request.getContentLengthLong() > limit) { problems.write(request,response,413,"Request body is too large.");return; }
        byte[] body=request.getInputStream().readNBytes(limit + 1);
        if(body.length>limit) { problems.write(request,response,413,"Request body is too large.");return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
        },response);
    }
}
