package com.belsenai.coach;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class CoachProviderConfig {
    public static final String MODEL="openai/gpt-oss-20b";
    public static final URI ENDPOINT=URI.create("https://api.groq.com/openai/v1/chat/completions");
    public static final class Settings {
        public final boolean enabled;
        final String key;
        final int connectSeconds,deadlineSeconds,maxInputBytes,maxOutputTokens;
        public Settings(boolean enabled,String key,boolean freeConfirmed,boolean privacyConfirmed,String model,String endpoint,
                int connectSeconds,int deadlineSeconds,int maxInputBytes,int maxOutputTokens,boolean strict) {
            this.enabled=enabled;this.key=key;this.connectSeconds=connectSeconds;this.deadlineSeconds=deadlineSeconds;
            this.maxInputBytes=maxInputBytes;this.maxOutputTokens=maxOutputTokens;
            if(!MODEL.equals(model) || !ENDPOINT.toString().equals(endpoint) || !strict
                    || connectSeconds<1 || connectSeconds>3 || deadlineSeconds<1 || deadlineSeconds>15
                    || connectSeconds>deadlineSeconds || maxInputBytes<1024 || maxInputBytes>12288 || maxOutputTokens<64 || maxOutputTokens>512)
                throw new IllegalArgumentException("Invalid Coach provider policy.");
            if(enabled && (key==null || key.isBlank() || key.length()>512 || key.chars().anyMatch(c->c<33 || c>126) || !freeConfirmed || !privacyConfirmed))
                throw new IllegalArgumentException("Enabled Coach requires a backend credential and reviewed free-plan/privacy confirmations.");
        }
    }
    @Bean Settings coachSettings(@Value("${belsen.coach.enabled:false}") boolean enabled,
            @Value("${belsen.coach.api-key:}") String key,@Value("${belsen.coach.free-plan-confirmed:false}") boolean free,
            @Value("${belsen.coach.privacy-reviewed:false}") boolean privacy,
            @Value("${belsen.coach.model:openai/gpt-oss-20b}") String model,
            @Value("${belsen.coach.endpoint:https://api.groq.com/openai/v1/chat/completions}") String endpoint,
            @Value("${belsen.coach.connect-seconds:3}") int connect,@Value("${belsen.coach.deadline-seconds:15}") int deadline,
            @Value("${belsen.coach.max-input-bytes:12288}") int input,@Value("${belsen.coach.max-output-tokens:512}") int output,
            @Value("${belsen.coach.strict-output:true}") boolean strict) {
        return new Settings(enabled,key,free,privacy,model,endpoint,connect,deadline,input,output,strict);
    }
    @Bean CoachProvider coachProvider(Settings settings,ObjectMapper mapper) { return new GroqCoachProvider(settings,mapper); }
}
