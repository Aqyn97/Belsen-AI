package com.belsenai.coach;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import com.belsenai.common.error.ProblemResponses;

/** Installed after authentication/CSRF, before MVC; chunked bodies are bounded too. */
public class CoachBodyLimit extends OncePerRequestFilter {
    private final ProblemResponses problems;
    public CoachBodyLimit(ProblemResponses problems) { this.problems=problems; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getMethod().equals("POST") || !java.util.Set.of("/api/v1/coach/evidence","/api/v1/coach/analyses").contains(request.getServletPath())) { chain.doFilter(request,response);return; }
        byte[] body=request.getInputStream().readNBytes(4097);
        if(body.length>4096) { problems.write(request,response,413,"Coach request exceeds 4096 bytes.");return; }
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
