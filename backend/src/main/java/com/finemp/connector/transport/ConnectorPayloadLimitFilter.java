package com.finemp.connector.transport;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded buffering only after machine authentication; never persist or log a request body. */
public class ConnectorPayloadLimitFilter extends OncePerRequestFilter {
    private static final Pattern COMPLETION = Pattern.compile("/api/connector/v1/work/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/complete");
    private final int maxBatchBytes, maxMetadataBytes;
    private final Semaphore capacity;
    public ConnectorPayloadLimitFilter(int maxBatchBytes, int maxMetadataBytes, int concurrency) {
        if (maxBatchBytes < 1024 || maxBatchBytes > 16 * 1024 * 1024 || maxMetadataBytes < 64
                || maxMetadataBytes > 65536 || maxMetadataBytes > maxBatchBytes || concurrency < 1 || concurrency > 8)
            throw new IllegalArgumentException("Invalid connector request resource limits.");
        this.maxBatchBytes = maxBatchBytes; this.maxMetadataBytes = maxMetadataBytes; capacity = new Semaphore(concurrency);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var encodings = Collections.list(request.getHeaders("Content-Encoding"));
        if (encodings.size() > 1 || encodings.stream().anyMatch(encoding -> !encoding.isBlank() && !encoding.equalsIgnoreCase("identity"))) {
            reject(response, 415, "CONNECTOR_UNSUPPORTED_ENCODING"); return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        int limit = "POST".equals(request.getMethod()) && COMPLETION.matcher(path).matches() ? maxBatchBytes : maxMetadataBytes;
        if (request.getContentLengthLong() > limit) { reject(response, 413, "CONNECTOR_PAYLOAD_TOO_LARGE"); return; }
        if (!capacity.tryAcquire()) { reject(response, 429, "CONNECTOR_BUSY"); return; }
        try {
            byte[] body;
            try { body = request.getInputStream().readNBytes(limit + 1); }
            catch (IOException invalid) { reject(response, 400, "CONNECTOR_INVALID_REQUEST"); return; }
            if (body.length > limit) { reject(response, 413, "CONNECTOR_PAYLOAD_TOO_LARGE"); return; }
            chain.doFilter(new BufferedRequest(request, body), response);
        } finally { capacity.release(); }
    }
    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        if (status == 429) response.setHeader("Retry-After", "1");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("Connector request uses blocking bounded parsing."); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
