package com.finemp.finance.reconciliation;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

@Embeddable
public record StatementValues(
        @Column(name="posted_date",nullable=false,updatable=false) LocalDate postedDate,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Column(name="signed_amount",nullable=false,precision=19,scale=2,updatable=false) BigDecimal signedAmount,
        @Column(nullable=false,length=4000,updatable=false) String memo,
        @Column(name="external_id",nullable=false,length=255,updatable=false) String externalId,
        @Column(name="document_reference",nullable=false,length=255,updatable=false) String documentReference) {
    public StatementValues {
        if(postedDate==null || postedDate.getYear()<1 || postedDate.getYear()>9999 || signedAmount==null || signedAmount.signum()==0)throw invalid();
        try {var compact=signedAmount.stripTrailingZeros();if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();signedAmount=compact.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException invalid){throw invalid();}
        memo=trim(text(memo,4000));externalId=trim(text(externalId,255));documentReference=trim(text(documentReference,255));
    }
    public String identityKey() {
        String input=externalId.isEmpty()?"FALLBACK\0"+postedDate+"\0"+signedAmount.toPlainString()+"\0"+normalize(memo):"FITID\0"+externalId;
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static String normalize(String value) {
        var input=Normalizer.normalize(value,Normalizer.Form.NFD);var result=new StringBuilder(input.length());
        for(int i=0;i<input.length();i++){char c=input.charAt(i);if(c<0x0300 || c>0x036f)result.append(c);}
        return trim(result.toString().toLowerCase(Locale.ROOT));
    }
    private static String trim(String value) {int a=0,b=value.length();while(a<b && whitespace(value.charAt(a)))a++;while(b>a && whitespace(value.charAt(b-1)))b--;return value.substring(a,b);}
    private static boolean whitespace(char c) {return c>=0x0009 && c<=0x000d || c==0x0020 || c==0x00a0 || c==0x1680 || c>=0x2000 && c<=0x200a || c==0x2028 || c==0x2029 || c==0x202f || c==0x205f || c==0x3000 || c==0xfeff;}
    private static String text(String value,int max) {
        if(value==null)return "";if(value.length()>max)throw invalid();
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c==0 || Character.isLowSurrogate(c))throw invalid();if(Character.isHighSurrogate(c) && (++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))))throw invalid();}return value;
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Movimento de extrato inválido.");}
    @Override public String toString() {return "StatementValues[redacted]";}
}
