package com.belsenai.web;

import com.belsenai.common.error.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Explicit public documents only; never forward API, OAuth, actuator or arbitrary paths to HTML. */
@RestController
public class SpaController {
    @GetMapping(value={"/", "/auth", "/auth/callback", "/app", "/app/dashboard", "/app/goals",
            "/app/tasks", "/app/habits", "/app/analytics", "/app/settings"}, produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        Resource index=new ClassPathResource("static/index.html");
        if(!index.exists()) throw new ApiException(503,"The web application has not been packaged.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(index);
    }

    @GetMapping("/assets/{name}")
    public ResponseEntity<Resource> asset(@PathVariable String name) {
        // A flat Vite content-hash filename and explicit MIME type. No source maps or arbitrary files.
        if(!name.matches("[A-Za-z0-9_-]+-[A-Za-z0-9_-]{8,}\\.(js|css|woff2|svg|png|ico)"))
            throw new ApiException(404,"Asset is unavailable.");
        Resource asset=new ClassPathResource("static/assets/"+name);
        if(!asset.exists()) throw new ApiException(404,"Asset is unavailable.");
        String extension=name.substring(name.lastIndexOf('.')+1);
        MediaType type=MediaType.parseMediaType(switch(extension) {
            case "js" -> "text/javascript"; case "css" -> "text/css"; case "woff2" -> "font/woff2";
            case "svg" -> "image/svg+xml"; case "png" -> "image/png"; default -> "image/x-icon";
        });
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public, max-age=31536000, immutable")
                .contentType(type).body(asset);
    }
}
