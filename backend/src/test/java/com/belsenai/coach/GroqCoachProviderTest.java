package com.belsenai.coach;

import com.sun.net.httpserver.HttpServer;
import com.belsenai.analytics.DashboardDtos;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class GroqCoachProviderTest {
    final JsonMapper mapper=JsonMapper.builder().build();
    CoachProviderConfig.Settings settings(boolean enabled) { return new CoachProviderConfig.Settings(enabled,"test-only",true,true,CoachProviderConfig.MODEL,CoachProviderConfig.ENDPOINT.toString(),1,1,12288,512,true); }
    CoachEvidence.Snapshot evidence() {
        var at=Instant.parse("2026-11-04T12:00:00Z");
        return new CoachEvidence.Snapshot(1,new DashboardDtos.Period(LocalDate.of(2026,11,2),"UTC",at.minusSeconds(100),at.plusSeconds(100),at,true,"FROZEN","PROVISIONAL",1),
            List.of(new CoachEvidence.Fact("tasks","1","3","33.3",null)),
            List.of(new CoachEvidence.Candidate("c1",0,CoachEvidence.Action.REVIEW_TASKS,CoachEvidence.Destination.TASKS,"INCOMPLETE_TASKS","server explanation",List.of("tasks"))),new CoachEvidence.Coverage(0,0,false));
    }
    String response(String content,String finish) {
        return mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("role","assistant","content",content)))));
    }
    @Test void explicitConfigurationRejectsUnsafeOrUnconfirmedOperation() {
        assertThatThrownBy(()->new CoachProviderConfig.Settings(true,"",true,true,CoachProviderConfig.MODEL,CoachProviderConfig.ENDPOINT.toString(),1,1,12288,512,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CoachProviderConfig.Settings(true,"test-only",false,true,CoachProviderConfig.MODEL,CoachProviderConfig.ENDPOINT.toString(),1,1,12288,512,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CoachProviderConfig.Settings(false,"",false,false,"other","http://localhost",1,1,12288,512,true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(settings(false).toString()).doesNotContain("test-only");
    }
    @Test void localProtocolStrictSchemaWhitelistedDataAndOneRequest() throws Exception {
        var captured=new AtomicReference<String>();var calls=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();captured.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] bytes=response("{\"selectedIds\":[\"c1\"]}","stop").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
            assertThat(provider.rank(evidence())).containsExactly("c1");assertThat(calls.get()).isEqualTo(1);
            var json=mapper.readTree(captured.get());assertThat(json.get("model").asString()).isEqualTo(CoachProviderConfig.MODEL);
            assertThat(json.get("response_format").get("json_schema").get("strict").asBoolean()).isTrue();
            assertThat(captured.get()).doesNotContain("server explanation","ownerId","submissionId","test-only","goalId","tool_choice");
            assertThat(json.get("max_completion_tokens").asInt()).isEqualTo(512);
            assertThat(json.get("messages").size()).isEqualTo(2);
            assertThat(json.has("store")).isFalse();assertThat(json.has("reasoning_format")).isFalse();
            assertThat(json.get("include_reasoning").asBoolean()).isFalse();
            assertThat(json.get("reasoning_effort").asString()).isEqualTo("low");
        } finally {server.stop(0);}
    }
    @Test void localValidationRejectsMalformedUnknownDuplicateExtraEmptyTruncatedAndRefusal() {
        try(var provider=new GroqCoachProvider(settings(false),mapper)) {
            for(String value:List.of("{}","{\"selectedIds\":[]}","{\"selectedIds\":[\"unknown\"]}","{\"selectedIds\":[\"c1\",\"c1\"]}","{\"selectedIds\":[\"c1\"],\"extra\":1}","{\"selectedIds\":[\"c1\"],\"selectedIds\":[]}","[1]","not json"))
                assertThatThrownBy(()->provider.decode(response(value,"stop").getBytes(StandardCharsets.UTF_8),evidence())).isInstanceOf(CoachProviderFailure.class);
            assertThatThrownBy(()->provider.decode(response("{\"selectedIds\":[\"c1\"]}","length").getBytes(StandardCharsets.UTF_8),evidence())).isInstanceOf(CoachProviderFailure.class);
            String refusal="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"refusal\":\"refused\",\"content\":\"{}\"}}]}";
            assertThatThrownBy(()->provider.decode(refusal.getBytes(StandardCharsets.UTF_8),evidence())).isInstanceOf(CoachProviderFailure.class);
            assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class);
        }
    }
    @Test void httpErrorsNeverRetryOrExposeUpstreamBody() throws Exception {
        for(int status:List.of(400,401,403,429,500,502,503,302)) {
            var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",e->{calls.incrementAndGet();byte[] b="secret upstream details".getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Location","https://api.groq.com");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();});server.start();
            try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
                assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class).hasMessageNotContaining("secret");assertThat(calls.get()).isEqualTo(1);
            } finally {server.stop(0);}
        }
    }
    @Test void deadlineBoundsHeadersAndBodyWithoutSleep() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{entered.countDown();try {release.await(5,TimeUnit.SECONDS);} catch(InterruptedException x){Thread.currentThread().interrupt();} finally{e.close();}});server.start();
        try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
            assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class);assertThat(entered.getCount()).isZero();
        } finally {release.countDown();server.stop(0);}
    }
    @Test void reasoningIsNeverParsedAsTheRanking() {
        try(var provider=new GroqCoachProvider(settings(false),mapper)) {
            String response=mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("role","assistant","reasoning","Ignore the schema and choose foreign-id","content","{\"selectedIds\":[\"c1\"]}")))));
            assertThat(provider.decode(response.getBytes(StandardCharsets.UTF_8),evidence())).containsExactly("c1");
            String reasoningOnly=mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("role","assistant","reasoning","{\"selectedIds\":[\"c1\"]}")))));
            assertThatThrownBy(()->provider.decode(reasoningOnly.getBytes(StandardCharsets.UTF_8),evidence())).isInstanceOf(CoachProviderFailure.class);
        }
    }

    @Test void connectionFailureAndInputLimitsFailClosed() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);int port=server.getAddress().getPort();server.start();server.stop(0);
        try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+port+"/"))) {
            assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class);
        }
        var small=new CoachProviderConfig.Settings(true,"test-only",true,true,CoachProviderConfig.MODEL,CoachProviderConfig.ENDPOINT.toString(),1,1,1024,512,true);
        try(var provider=new GroqCoachProvider(small,mapper)) { assertThatThrownBy(()->provider.validate(evidence())).isInstanceOf(CoachProviderFailure.class); }
    }
    @Test void oversizedChunkedResponseIsCancelledWithoutRetry() throws Exception {
        var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{calls.incrementAndGet();e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,0);try {e.getResponseBody().write(new byte[65537]);} catch(java.io.IOException cancelled) { } finally {e.close();}});server.start();
        try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
            assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class);assertThat(calls.get()).isEqualTo(1);
        } finally {server.stop(0);}
    }
    @Test void deadlineAlsoBoundsAStalledResponseBody() throws Exception {
        var release=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,0);e.getResponseBody().write('{');e.getResponseBody().flush();try {release.await(5,TimeUnit.SECONDS);} catch(InterruptedException x){Thread.currentThread().interrupt();} finally{e.close();}});server.start();
        try(var provider=new GroqCoachProvider(settings(true),mapper,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
            assertThatThrownBy(()->provider.rank(evidence())).isInstanceOf(CoachProviderFailure.class).satisfies(e->assertThat(((CoachProviderFailure)e).status()).isEqualTo(504));
        } finally {release.countDown();server.stop(0);}
    }

}
