package com.finemp.finance.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Accept only the URL query, never a merged servlet parameter map or a form body. */
public final class ExpectedVersionQuery {
    private ExpectedVersionQuery() { }
    public static long required(String query) {
        if(query==null || query.isEmpty() || query.length()>8192)throw invalid();
        String version=null;
        try {
            for(var parameter:query.split("&")) {
                var parts=parameter.split("=",2);var name=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);
                if(!name.equals("expectedVersion"))continue;
                if(version!=null || parts.length!=2)throw invalid();version=URLDecoder.decode(parts[1],StandardCharsets.UTF_8);
            }
            if(version==null || !version.matches("[0-9]{1,19}"))throw invalid();return Long.parseLong(version);
        }catch(IllegalArgumentException invalid) {throw invalid();}
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Versão esperada na query inválida.");}
}
