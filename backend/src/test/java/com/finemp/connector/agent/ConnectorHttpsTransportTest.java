package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorHttpsTransportTest {
    private final String token = "s".repeat(43);
    private final URI origin = URI.create("https://connector.invalid");
    @Test void shouldRefuseUnsafeDestinationsAndRedirectingClientBeforeSendingCredentials() {
        for (var url : List.of("http://connector.invalid", "https://user:private@connector.invalid", "https://connector.invalid/?key=private",
                "https://connector.invalid/#fragment", "https://connector.invalid/api", "https:///missing-host"))
            assertThatThrownBy(() -> new ConnectorHttpsTransport(URI.create(url), () -> token, Duration.ofSeconds(5)))
                    .isInstanceOf(ConnectorClientException.class).hasMessage("Connector client: CONFIGURATION").hasNoCause();
        var client = client(); when(client.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);
        assertThatThrownBy(() -> transport(client, Duration.ofSeconds(1))).isInstanceOf(ConnectorClientException.class);
        verify(client, never()).sendAsync(any(), any());
    }
    @Test void shouldSendOnlyOneBearerToAFixedHttpsEndpointAndRedactAllDiagnostics() {
        var client = client(); respond(client, 200, Map.of("content-type", List.of("application/json")), "{}".getBytes());
        var result = transport(client, Duration.ofSeconds(2)).exchange("GET", "/session", null);
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(client).sendAsync(request.capture(), any());
        assertThat(request.getValue().uri()).isEqualTo(URI.create("https://connector.invalid/api/connector/v1/session"));
        assertThat(request.getValue().headers().allValues("Authorization")).containsExactly("Bearer " + token);
        assertThat(result.status()).isEqualTo(200); assertThat(result.toString()).doesNotContain(token, "{}");
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(2));
    }
    @Test void shouldCancelAnExchangeWhoseBodyNeverFinishesBeforeTheTotalDeadline() {
        var client = client(); var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        assertThatThrownBy(() -> transport(client, Duration.ofMillis(25)).exchange("GET", "/session", null))
                .isInstanceOfSatisfying(ConnectorClientException.class, ex -> assertThat(ex.kind()).isEqualTo(ConnectorClientException.Kind.TIMEOUT));
        assertThat(pending.isCancelled()).isTrue();
    }
    @Test void shouldCancelBeforeCopyingAnOversizedUnknownLengthResponse() {
        var client = client(); var subscription = respond(client, 200, Map.of("content-type", List.of("application/json")), new byte[65537]);
        assertThatThrownBy(() -> transport(client, Duration.ofSeconds(2)).exchange("GET", "/session", null))
                .isInstanceOfSatisfying(ConnectorClientException.class, ex -> assertThat(ex.kind()).isEqualTo(ConnectorClientException.Kind.RESOURCE_LIMIT));
        verify(subscription).cancel();
    }
    @Test void shouldRejectCompressedOrRedirectedResponsesWithoutFollowingTheLocation() {
        for (var status : List.of(200, 307)) {
            var client = client(); respond(client, status, Map.of("content-encoding", List.of("gzip"), "location", List.of("https://elsewhere.invalid")), new byte[0]);
            assertThatThrownBy(() -> transport(client, Duration.ofSeconds(2)).exchange("GET", "/session", null))
                    .isInstanceOf(ConnectorClientException.class).hasNoCause();
            verify(client).sendAsync(any(), any());
        }
    }
    @Test void shouldNeverExposeSupplierNetworkOrBodyExceptions() {
        var client = client();
        var transport = new ConnectorHttpsTransport(origin, () -> { throw new IllegalStateException("synthetic private secret"); }, Duration.ofSeconds(2), client);
        assertThatThrownBy(() -> transport.exchange("GET", "/session", null)).hasMessage("Connector client: CREDENTIAL").hasNoCause();
        verify(client, never()).sendAsync(any(), any());
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("private connection")));
        assertThatThrownBy(() -> transport(client, Duration.ofSeconds(2)).exchange("GET", "/session", null))
                .hasMessage("Connector client: TRANSPORT").hasNoCause();
    }
    @Test void shouldBoundOutboundBodyAndEndpointBeforeReadingTheCredential() {
        var client = client(); var supplier = mock(java.util.function.Supplier.class);
        var transport = new ConnectorHttpsTransport(origin, supplier, Duration.ofSeconds(2), client);
        assertThatThrownBy(() -> transport.exchange("POST", "/work/claim", new byte[8*1024*1024+1])).isInstanceOf(ConnectorClientException.class);
        assertThatThrownBy(() -> transport.exchange("GET", "//elsewhere.invalid", null)).isInstanceOf(ConnectorClientException.class);
        verifyNoInteractions(supplier); verify(client, never()).sendAsync(any(), any());
    }
    private ConnectorHttpsTransport transport(HttpClient client, Duration deadline) { return new ConnectorHttpsTransport(origin, () -> token, deadline, client); }
    private HttpClient client() {
        var client = mock(HttpClient.class); when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        var ssl = new SSLParameters(); ssl.setEndpointIdentificationAlgorithm("HTTPS"); when(client.sslParameters()).thenReturn(ssl);
        return client;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flow.Subscription respond(HttpClient client, int status, Map<String,List<String>> headers, byte[] body) {
        var subscription = mock(Flow.Subscription.class);
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            var info = mock(HttpResponse.ResponseInfo.class); when(info.statusCode()).thenReturn(status);
            var httpHeaders = HttpHeaders.of(headers, (a,b) -> true); when(info.headers()).thenReturn(httpHeaders);
            var subscriber = handler.apply(info); subscriber.onSubscribe(subscription);
            subscriber.onNext(List.of(ByteBuffer.wrap(body))); subscriber.onComplete();
            return subscriber.getBody().thenApply(bytes -> {
                var response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(status); when(response.headers()).thenReturn(httpHeaders);
                when(response.body()).thenReturn(bytes); return response;
            }).toCompletableFuture();
        });
        return subscription;
    }
}
