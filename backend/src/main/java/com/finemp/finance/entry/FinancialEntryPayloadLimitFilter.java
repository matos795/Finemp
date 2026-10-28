package com.finemp.finance.entry;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed after authorization, only for the two opt-in financial writer routes. */
public class FinancialEntryPayloadLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final Semaphore capacity;
    public FinancialEntryPayloadLimitFilter(int maxBytes,int concurrency) {
        if(maxBytes<1024 || maxBytes>65536 || concurrency<1 || concurrency>4)throw new IllegalArgumentException("Limites financeiros inválidos.");
        this.maxBytes=maxBytes;capacity=new Semaphore(concurrency);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // Reuse the route decision, not a second URI matcher: encoded paths and binding aliases cannot skip the gate.
        return !Boolean.TRUE.equals(request.getAttribute(EntryCommandAuthorizationManager.AUTHORIZED_WRITER_ATTRIBUTE));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var encodings=Collections.list(request.getHeaders("Content-Encoding"));
        if(encodings.size()>1 || encodings.stream().anyMatch(value->!value.isBlank() && !value.equalsIgnoreCase("identity")) || !json(request.getContentType())) {
            reject(response,415,"FINANCIAL_ENTRY_UNSUPPORTED_ENCODING");return;
        }
        if(request.getContentLengthLong()>maxBytes) {reject(response,413,"FINANCIAL_ENTRY_PAYLOAD_TOO_LARGE");return;}
        if(!capacity.tryAcquire()) {reject(response,429,"FINANCIAL_ENTRY_BUSY");return;}
        try {
            byte[] body;try {body=request.getInputStream().readNBytes(maxBytes+1);}catch(IOException invalid) {reject(response,400,"INVALID_FINANCIAL_ENTRY_COMMAND");return;}
            if(body.length>maxBytes) {reject(response,413,"FINANCIAL_ENTRY_PAYLOAD_TOO_LARGE");return;}
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
