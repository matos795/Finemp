package com.finemp.finance.reconciliation;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.regex.Pattern;

final class StatementTextDecoder {
    static final int MAX_BYTES=16*1024*1024;
    private static final Charset WINDOWS=Charset.forName("windows-1252");
    private StatementTextDecoder() { }
    static String decodeCsv(byte[] bytes) {return decode(bytes,false);}
    static String decodeOfx(byte[] bytes) {return decode(bytes,true);}
    private static String decode(byte[] bytes,boolean ofx) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)throw new IllegalArgumentException("Arquivo de extrato inválido ou excede o limite permitido.");
        String header=new String(bytes,0,Math.min(bytes.length,600),StandardCharsets.ISO_8859_1);
        if(header.startsWith("\u00ef\u00bb\u00bf"))header=header.substring(3);
        boolean explicitUtf8=false;
        if(ofx) {
            var declaration=Pattern.compile("(?is)^\\s*<\\?xml\\s+([^?]{0,512})\\?>").matcher(header);
            if(declaration.find()) {
                var encoding=Pattern.compile("(?i)\\bencoding\\s*=\\s*(['\"])([^'\"]+)\\1").matcher(declaration.group(1));
                if(encoding.find()) {
                    String label=encoding.group(2).toUpperCase(Locale.ROOT);
                    if(label.equals("UTF-8") || label.equals("UTF8"))explicitUtf8=true;
                    else if(label.equals("WINDOWS-1252") || label.equals("ISO-8859-1") || label.equals("US-ASCII"))return new String(bytes,WINDOWS);
                    else throw new IllegalArgumentException("Codificação de extrato não suportada.");
                }else explicitUtf8=true;
            }else {
                int markup=header.indexOf('<');String declarations=markup<0?header:header.substring(0,markup);
                if(Pattern.compile("(?im)^\\s*(?:CHARSET|ENCODING)\\s*:\\s*(?:1252|USASCII|ISO-8859-1|WINDOWS-1252)\\s*$").matcher(declarations).find())return new String(bytes,WINDOWS);
                explicitUtf8=Pattern.compile("(?im)^\\s*(?:CHARSET|ENCODING)\\s*:\\s*(?:65001|UTF-8|UTF8)\\s*$").matcher(declarations).find();
            }
        }
        try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();return text.startsWith("\uFEFF")?text.substring(1):text;}
        catch(CharacterCodingException invalid){if(explicitUtf8)throw new IllegalArgumentException("Codificação UTF-8 de extrato inválida.");return new String(bytes,WINDOWS);}
    }
}
