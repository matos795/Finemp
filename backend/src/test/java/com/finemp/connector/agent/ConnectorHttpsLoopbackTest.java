package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.HttpClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;

/** Ephemeral synthetic certificate. No operational key, listener or trust-store installation. */
class ConnectorHttpsLoopbackTest {
    @Test void shouldEnforceTlsTrustRefuseRedirectAndTimeOutAfterHeadersOnARealLoopbackConnection() throws Exception {
        String password="ephemeral-test-only";
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        var keys=KeyStore.getInstance("PKCS12");keys.load(null,password.toCharArray());
        keys.setKeyEntry("synthetic",pair.getPrivate(),password.toCharArray(),new java.security.cert.Certificate[]{certificate(pair)});
        var keyFactory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keyFactory.init(keys,password.toCharArray());
        var trustFactory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trustFactory.init(keys);
        var ssl=SSLContext.getInstance("TLS");ssl.init(keyFactory.getKeyManagers(),trustFactory.getTrustManagers(),null);
        var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        var executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        var redirected=new AtomicInteger();var release=new CountDownLatch(1);var mode=new AtomicInteger();
        server.createContext("/api/connector/v1/session",exchange->{
            try(exchange) {
                if(mode.get()==1) {exchange.getResponseHeaders().set("Location","/should-never-follow");exchange.sendResponseHeaders(307,-1);return;}
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,0);
                if(mode.get()==2) {
                    exchange.getResponseBody().write('{');exchange.getResponseBody().flush();
                    try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                } else exchange.getResponseBody().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        server.createContext("/should-never-follow",exchange->{redirected.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.start();var origin=URI.create("https://127.0.0.1:"+server.getAddress().getPort());
        var parameters=new SSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");
        try(var trusted=HttpClient.newBuilder().sslContext(ssl).sslParameters(parameters).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(2)).build()) {
            try(var untrusted=new ConnectorHttpsTransport(origin,()->"s".repeat(43),Duration.ofSeconds(10))) {
                assertThatThrownBy(()->untrusted.exchange("GET","/session",null))
                        .isInstanceOfSatisfying(ConnectorClientException.class,ex->assertThat(ex.kind()).isEqualTo(ConnectorClientException.Kind.TRANSPORT));
            }
            var transport=new ConnectorHttpsTransport(origin,()->"s".repeat(43),Duration.ofSeconds(10),trusted);
            assertThat(transport.exchange("GET","/session",null).status()).isEqualTo(200);
            mode.set(1);assertThatThrownBy(()->transport.exchange("GET","/session",null)).hasMessage("Connector client: PROTOCOL");
            assertThat(redirected).hasValue(0);
            mode.set(2);var shortDeadline=new ConnectorHttpsTransport(origin,()->"s".repeat(43),Duration.ofMillis(300),trusted);
            assertThatThrownBy(()->shortDeadline.exchange("GET","/session",null))
                    .isInstanceOfSatisfying(ConnectorClientException.class,ex->assertThat(ex.kind()).isEqualTo(ConnectorClientException.Kind.TIMEOUT));
        } finally {release.countDown();server.stop(0);executor.shutdownNow();}
    }
    // Minimal DER for an ephemeral test certificate, using only public JCA APIs. No stored private key or global trust changes.
    private X509Certificate certificate(KeyPair pair) throws Exception {
        byte[] algorithm=der(0x30,der(6,new byte[]{0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,11}),der(5,new byte[0]));
        byte[] name=der(0x30,der(0x31,der(0x30,der(6,new byte[]{0x55,4,3}),der(0x0c,"localhost".getBytes(StandardCharsets.UTF_8)))));
        var format=DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'",Locale.ROOT).withZone(ZoneOffset.UTC);
        byte[] validity=der(0x30,der(0x17,format.format(Instant.now().minusSeconds(86400)).getBytes(StandardCharsets.US_ASCII)),
                der(0x17,format.format(Instant.now().plusSeconds(86400)).getBytes(StandardCharsets.US_ASCII)));
        byte[] san=der(0x30,der(6,new byte[]{0x55,0x1d,0x11}),der(4,der(0x30,
                der(0x82,"localhost".getBytes(StandardCharsets.US_ASCII)),der(0x87,new byte[]{127,0,0,1}))));
        byte[] tbs=der(0x30,der(0xa0,der(2,new byte[]{2})),der(2,new byte[]{1}),algorithm,name,validity,name,
                pair.getPublic().getEncoded(),der(0xa3,der(0x30,san)));
        var signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(tbs);
        byte[] signature=signer.sign();var bits=new byte[signature.length+1];System.arraycopy(signature,0,bits,1,signature.length);
        return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der(0x30,tbs,algorithm,der(3,bits))));
    }
    private byte[] der(int tag,byte[]...parts) {
        var value=new ByteArrayOutputStream();for(var part:parts)value.writeBytes(part);
        var output=new ByteArrayOutputStream();output.write(tag);int length=value.size();
        if(length<128)output.write(length);
        else if(length<256){output.write(0x81);output.write(length);}
        else{output.write(0x82);output.write(length>>>8);output.write(length&255);}
        output.writeBytes(value.toByteArray());return output.toByteArray();
    }
}
