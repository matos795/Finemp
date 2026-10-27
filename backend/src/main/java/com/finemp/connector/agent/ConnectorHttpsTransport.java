package com.finemp.connector.agent;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import javax.net.ssl.SSLParameters;
import static com.finemp.connector.agent.ConnectorClientException.Kind.*;

/** Outbound-only transport. No Spring bean or implicit agent startup. */
public final class ConnectorHttpsTransport implements AutoCloseable {
    private static final int MAX_RESPONSE = 65536;
    private final URI origin;
    private final Supplier<String> credential;
    private final Duration deadline;
    private final HttpClient client;
    public ConnectorHttpsTransport(URI origin, Supplier<String> credential, Duration deadline) {
        this(origin, credential, deadline, newClient(deadline));
    }
    ConnectorHttpsTransport(URI origin, Supplier<String> credential, Duration deadline, HttpClient client) {
        if (origin == null || !"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null || origin.getPort() > 65535
                || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/")) || credential == null
                || client == null || client.followRedirects() != HttpClient.Redirect.NEVER
                || !"HTTPS".equals(client.sslParameters().getEndpointIdentificationAlgorithm())) throw error(CONFIGURATION);
        validateDeadline(deadline);
        this.origin = URI.create(origin.toASCIIString().replaceAll("/$", ""));
        this.credential = credential; this.deadline = deadline; this.client = client;
    }
    private static HttpClient newClient(Duration deadline) {
        validateDeadline(deadline); var ssl = new SSLParameters(); ssl.setEndpointIdentificationAlgorithm("HTTPS");
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).sslParameters(ssl)
                .connectTimeout(deadline.compareTo(Duration.ofSeconds(10)) > 0 ? Duration.ofSeconds(10) : deadline).build();
    }
    private static void validateDeadline(Duration deadline) {
        if (deadline == null || deadline.compareTo(Duration.ofMillis(1)) < 0 || deadline.compareTo(Duration.ofSeconds(120)) > 0)
            throw error(CONFIGURATION);
    }
    Response exchange(String method, String path, byte[] body) {
        boolean get = "GET".equals(method) && ("/session".equals(path) || path != null && path.matches("/work/[0-9a-f-]{36}/result"));
        boolean post = "POST".equals(method) && ("/work/claim".equals(path) || path != null && path.matches("/work/[0-9a-f-]{36}/(heartbeat|complete|fail)"));
        if (!get && !post || get && body != null) throw error(CONFIGURATION);
        int limit = path.endsWith("/complete") ? 8 * 1024 * 1024 : 2048;
        if (body != null && body.length > limit) throw error(RESOURCE_LIMIT);
        String secret;
        try { secret = credential.get(); } catch (RuntimeException unavailable) { throw error(CREDENTIAL); }
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) throw error(CREDENTIAL);
        var request = HttpRequest.newBuilder(URI.create(origin + "/api/connector/v1" + path)).timeout(deadline)
                .header("Authorization", "Bearer " + secret).header("Accept", "application/json")
                .header("Accept-Encoding", "identity");
        if (get) request.GET();
        else request.header("Content-Type", "application/json").POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        var cancelled = new AtomicBoolean(); var active = new AtomicReference<LimitedSubscriber>();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = client.sendAsync(request.build(), info -> {
                var subscriber = new LimitedSubscriber(MAX_RESPONSE); active.set(subscriber);
                if (cancelled.get()) subscriber.cancel(TIMEOUT);
                if (info.statusCode() >= 300 && info.statusCode() < 400) subscriber.cancel(PROTOCOL);
                var encodings = info.headers().allValues("Content-Encoding");
                if (encodings.size() > 1 || !encodings.isEmpty() && !"identity".equalsIgnoreCase(encodings.getFirst())) subscriber.cancel(PROTOCOL);
                var lengths = info.headers().allValues("Content-Length");
                if (lengths.size() > 1) subscriber.cancel(PROTOCOL);
                if (lengths.size() == 1) {
                    try { long size = Long.parseLong(lengths.getFirst()); if (size < 0 || size > MAX_RESPONSE) subscriber.cancel(RESOURCE_LIMIT); }
                    catch (NumberFormatException invalid) { subscriber.cancel(PROTOCOL); }
                }
                if (info.statusCode() == 200 || info.statusCode() == 202 || info.statusCode() == 422) {
                    var types = info.headers().allValues("Content-Type");
                    if (types.size() != 1 || !types.getFirst().split(";",2)[0].trim().equalsIgnoreCase("application/json")) subscriber.cancel(PROTOCOL);
                }
                return subscriber;
            });
            var response = pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            return new Response(response.statusCode(), response.body(), retryAfter(response.headers()));
        } catch (InterruptedException interrupted) {
            cancel(pending, active, cancelled); Thread.currentThread().interrupt(); throw error(INTERRUPTED);
        } catch (TimeoutException timeout) { cancel(pending, active, cancelled); throw error(TIMEOUT); }
        catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof ConnectorClientException safe) throw safe;
            if (cause instanceof HttpTimeoutException) throw error(TIMEOUT);
            throw error(TRANSPORT);
        } catch (ConnectorClientException safe) { throw safe; }
        catch (RuntimeException failure) { cancel(pending, active, cancelled); throw error(TRANSPORT); }
    }
    private void cancel(CompletableFuture<?> pending, AtomicReference<LimitedSubscriber> active, AtomicBoolean cancelled) {
        cancelled.set(true); var subscriber = active.get(); if (subscriber != null) subscriber.cancel(TIMEOUT);
        if (pending != null) pending.cancel(true);
    }
    private int retryAfter(HttpHeaders headers) {
        try {
            var values = headers.allValues("Retry-After"); if (values.size() != 1) return 0;
            return Math.clamp(Long.parseLong(values.getFirst()), 0, 300);
        } catch (NumberFormatException invalid) { return 0; }
    }
    private static ConnectorClientException error(ConnectorClientException.Kind kind) { return new ConnectorClientException(kind); }
    @Override public void close() { client.shutdownNow(); }
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedSubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || result.isDone()) { value.cancel(); return; }
            subscription = value; value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            long size = output.size(); for (var buffer : buffers) size += buffer.remaining();
            if (size > limit) { cancel(RESOURCE_LIMIT); return; }
            for (var buffer : buffers) { while (buffer.hasRemaining()) output.write(buffer.get()); }
            if (subscription != null) subscription.request(1);
        }
        @Override public synchronized void onError(Throwable failure) { cancel(TRANSPORT); }
        @Override public synchronized void onComplete() { if (!result.isDone()) result.complete(output.toByteArray()); }
        synchronized void cancel(ConnectorClientException.Kind kind) {
            if (!result.isDone()) result.completeExceptionally(error(kind));
            if (subscription != null) subscription.cancel();
        }
    }
    record Response(int status, byte[] body, int retryAfterSeconds) {
        @Override public String toString() { return "ConnectorResponse[status=" + status + ", redacted]"; }
    }
}
