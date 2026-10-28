package com.finemp.finance.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Shared bounded body mechanism, selected by a server-only normalized-route authorization marker. */
public class FinancialCommandPayloadLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final Semaphore capacity;
    private final String marker,codePrefix;
    protected FinancialCommandPayloadLimitFilter(int maxBytes,int concurrency,String marker,String codePrefix) {
        if(maxBytes<1024 || maxBytes>65536 || concurrency<1 || concurrency>4 || marker==null || marker.isBlank()
                || codePrefix==null || !codePrefix.matches("[A-Z_]+"))throw new IllegalArgumentException("Limites financeiros inválidos.");
        this.maxBytes=maxBytes;capacity=new Semaphore(concurrency);this.marker=marker;this.codePrefix=codePrefix;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // Reuse the route decision, not a second URI matcher: encoded paths and binding aliases cannot skip the gate.
        return !Boolean.TRUE.equals(request.getAttribute(marker));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var encodings=Collections.list(request.getHeaders("Content-Encoding"));
        if(encodings.size()>1 || encodings.stream().anyMatch(value->!value.isBlank() && !value.equalsIgnoreCase("identity")) || !json(request.getContentType())) {
            reject(response,415,codePrefix+"_UNSUPPORTED_ENCODING");return;
        }
        if(request.getContentLengthLong()>maxBytes) {reject(response,413,codePrefix+"_PAYLOAD_TOO_LARGE");return;}
        if(!capacity.tryAcquire()) {reject(response,429,codePrefix+"_BUSY");return;}
        try {
            byte[] body;try {body=request.getInputStream().readNBytes(maxBytes+1);}catch(IOException invalid) {reject(response,400,"INVALID_"+codePrefix+"_COMMAND");return;}
            if(body.length>maxBytes) {reject(response,413,codePrefix+"_PAYLOAD_TOO_LARGE");return;}
            chain.doFilter(new BufferedRequest(request,body),response);
        }finally {capacity.release();}
    }
    private boolean json(String value) {
        try {var type=MediaType.parseMediaType(value==null?"":value);return type.getType().equalsIgnoreCase("application") && type.getSubtype().equalsIgnoreCase("json");}
        catch(IllegalArgumentException invalid) {return false;}
    }
    private void reject(HttpServletResponse response,int status,String code)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");if(status==429)response.setHeader("Retry-After","1");
        response.getWriter().write("{\"code\":\""+code+"\"}");
    }
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request,byte[] body) {super(request);this.body=body;}
        @Override public int getContentLength() {return body.length;}
        @Override public long getContentLengthLong() {return body.length;}
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(body);return new ServletInputStream() {
                @Override public int read() {return input.read();}
                @Override public int read(byte[] bytes,int offset,int length) {return input.read(bytes,offset,length);}
                @Override public boolean isFinished() {return input.available()==0;}
                @Override public boolean isReady() {return true;}
                @Override public void setReadListener(ReadListener listener) {throw new IllegalStateException("Escrita financeira usa parsing bloqueante limitado.");}
            };
        }
        @Override public BufferedReader getReader() {return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
