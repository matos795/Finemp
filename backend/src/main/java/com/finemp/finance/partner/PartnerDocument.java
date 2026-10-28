package com.finemp.finance.partner;

import java.util.Locale;

/** Matches the homologated manual-registration checksum algorithm; no external lookup. */
public final class PartnerDocument {
    private PartnerDocument() { }
    public static String normalize(String value) {
        if(value==null || value.length()>64)throw invalid();
        return value.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]","");
    }
    public static String requireValid(String value,PartnerPersonType type) {
        String document=normalize(value);
        if(type==null || document.isEmpty() || document.chars().distinct().count()==1)throw invalid();
        if(type==PartnerPersonType.INDIVIDUAL) {
            if(!document.matches("[0-9]{11}") || cpfDigit(document,9)!=document.charAt(9)-'0'
                    || cpfDigit(document,10)!=document.charAt(10)-'0')throw invalid();
        } else {
            if(!document.matches("[0-9A-Z]{12}[0-9]{2}") || cnpjDigit(document,12)!=document.charAt(12)-'0'
                    || cnpjDigit(document,13)!=document.charAt(13)-'0')throw invalid();
        }
        return document;
    }
    private static int cpfDigit(String document,int length) {
        int sum=0;
        for(int i=0;i<length;i++)sum+=(document.charAt(i)-'0')*(length+1-i);
        return ((sum*10)%11)%10;
    }
    private static int cnpjDigit(String document,int length) {
        int sum=0,weight=2;
        for(int i=length-1;i>=0;i--) {
            sum+=(document.charAt(i)-48)*weight;
            weight=weight==9?2:weight+1;
        }
        int remainder=sum%11;
        return remainder<2?0:11-remainder;
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Documento do parceiro inválido.");
    }
}
