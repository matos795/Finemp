package com.finemp.finance.reconciliation;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Bounded text parser. It never resolves XML entities, performs I/O or persists statements. */
public class OfxPreviewParser {
    private static final int MAX_ROWS=50000;
    private static final Pattern OPEN=Pattern.compile("<STMTTRN>",Pattern.CASE_INSENSITIVE);
    private static final Pattern END=Pattern.compile("</STMTTRN>|<STMTTRN>|</BANKTRANLIST>",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEDGER=Pattern.compile("<LEDGERBAL>",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEDGER_END=Pattern.compile("</LEDGERBAL>",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Pattern> TAGS=java.util.stream.Stream.of("BANKID","ACCTID","CURDEF","BALAMT","DTASOF","DTPOSTED","TRNAMT","NAME","MEMO","TRNTYPE","FITID","CHECKNUM","REFNUM")
            .collect(java.util.stream.Collectors.toUnmodifiableMap(name->name,name->Pattern.compile("<"+name+">([^<\\r\\n]*)",Pattern.CASE_INSENSITIVE)));
    public Preview parse(byte[] bytes) {
        String source=StatementTextDecoder.decodeOfx(bytes);boolean xml=isXml(source);var rows=new ArrayList<Row>();var identities=new HashMap<String,List<Integer>>();
        var open=OPEN.matcher(source);int position=0;
        while(open.find(position)) {
            if(rows.size()>=MAX_ROWS)throw invalid();int begin=open.end();var end=END.matcher(source);
            if(!end.find(begin)){rows.add(new Row(rows.size()+1,"","","",null,Error.UNTERMINATED,List.of()));break;}
            var block=source.substring(begin,end.start());int index=rows.size();Row row;String fit="";
            try {
                fit=tag(block,"FITID",xml);
                row=xml && !source.regionMatches(true,end.start(),"</STMTTRN>",0,10)?new Row(index+1,"","","",null,Error.UNTERMINATED,List.of()):parseRow(index+1,block,xml,fit);
            }catch(Problem invalid){row=new Row(index+1,"","","",null,invalid.error,List.of());}
            rows.add(row);String identity=null;
            if(!fit.isEmpty()) {try{identity=new StatementValues(LocalDate.of(2000,1,1),BigDecimal.ONE,"",fit,"").identityKey();}catch(IllegalArgumentException ignored){ }}
            else if(row.values()!=null)identity=row.values().identityKey();
            if(identity!=null)identities.computeIfAbsent(identity,ignored->new ArrayList<>()).add(index);
            position=source.regionMatches(true,end.start(),"<STMTTRN>",0,9)?end.start():end.end();
        }
        if(rows.isEmpty())throw invalid();
        for(var duplicates:identities.values())if(duplicates.size()>1)for(var index:duplicates){var row=rows.get(index);var warnings=new ArrayList<>(row.warnings());warnings.add(Warning.DUPLICATE_IN_FILE);rows.set(index,new Row(row.number(),row.rawDate(),row.rawAmount(),row.rawMemo(),row.values(),row.error(),warnings));}
        var warnings=new ArrayList<Warning>();var bank=metadataTag(source,"BANKID",xml,warnings);var account=metadataTag(source,"ACCTID",xml,warnings);var currency=metadataTag(source,"CURDEF",xml,warnings);Balance balance=null;
        if(hasMultiple(source,"BANKID",xml,warnings))warnings.add(Warning.MULTIPLE_BANKS);
        if(hasMultiple(source,"ACCTID",xml,warnings))warnings.add(Warning.MULTIPLE_ACCOUNTS);
        if(hasMultiple(source,"CURDEF",xml,warnings))warnings.add(Warning.MULTIPLE_CURRENCIES);
        if(!"BRL".equalsIgnoreCase(currency))warnings.add(Warning.CURRENCY_UNCONFIRMED);
        var ledger=LEDGER.matcher(source);
        if(ledger.find()) {
            var ledgerEnd=LEDGER_END.matcher(source);int end=ledgerEnd.find(ledger.end())?ledgerEnd.start():source.length();String block=source.substring(ledger.end(),end);
            try {balance=new Balance(date(tag(block,"DTASOF",xml)),amount(tag(block,"BALAMT",xml),true));}catch(Problem invalid) {warnings.add(Warning.INVALID_BALANCE);}
        }
        return new Preview(bank,account,currency,balance,rows,warnings);
    }
    private Row parseRow(int number,String block,boolean xml,String id) {
        String rawDate=tag(block,"DTPOSTED",xml),rawAmount=tag(block,"TRNAMT",xml);String name=tag(block,"NAME",xml),memo=tag(block,"MEMO",xml);
        String text=name.isEmpty()?memo:memo.isEmpty() || name.equals(memo)?name:name+" – "+memo;if(text.isEmpty())text=tag(block,"TRNTYPE",xml);
        String document=tag(block,"CHECKNUM",xml);if(document.isEmpty())document=tag(block,"REFNUM",xml);
        try {
            LocalDate date=date(rawDate);BigDecimal amount=amount(rawAmount,false);
            StatementValues values;try {values=new StatementValues(date,amount,text,id,document);}catch(IllegalArgumentException invalid){throw new Problem(Error.INVALID_TEXT);}
            return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(text,4000),values,null,id.isEmpty()?List.of(Warning.FALLBACK_IDENTITY):List.of());
        }catch(Problem invalid){return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(text,4000),null,invalid.error,List.of());}
    }
    private static LocalDate date(String value) {
        if(value.length()<8 || !value.substring(0,8).matches("[0-9]{8}"))throw new Problem(Error.BAD_DATE);
        try {int year=Integer.parseInt(value.substring(0,4));if(year<1)throw new Problem(Error.BAD_DATE);return LocalDate.of(year,Integer.parseInt(value.substring(4,6)),Integer.parseInt(value.substring(6,8)));}catch(DateTimeException invalid){throw new Problem(Error.BAD_DATE);}
    }
    private static BigDecimal amount(String value,boolean zeroAllowed) {
        if(value.isEmpty() || value.length()>64)throw new Problem(Error.BAD_AMOUNT);
        try {
            var number=new BigDecimal(value.replace(',','.'));if(!zeroAllowed && number.signum()==0)throw new Problem(Error.ZERO_AMOUNT);
            var compact=number.signum()==0?BigDecimal.ZERO:number.stripTrailingZeros();if(compact.scale()>2)throw new Problem(Error.SUBCENT_AMOUNT);if((long)compact.precision()-compact.scale()>17)throw new Problem(Error.OUT_OF_RANGE);
            return compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(NumberFormatException | ArithmeticException invalid){throw new Problem(Error.BAD_AMOUNT);}
    }
    private static String tag(String block,String name,boolean xml) {
        var matcher=TAGS.get(name).matcher(block);return matcher.find()?trim(xml?xmlText(matcher.group(1)):matcher.group(1)):"";
    }
    private static boolean hasMultiple(String source,String name,boolean xml,List<Warning> warnings) {
        var matcher=TAGS.get(name).matcher(source);String first=null;
        while(matcher.find()){String value;try{value=trim(xml?xmlText(matcher.group(1)):matcher.group(1));}catch(Problem invalid){if(!warnings.contains(Warning.INVALID_BANK_INFORMATION))warnings.add(Warning.INVALID_BANK_INFORMATION);continue;}if(value.isEmpty())continue;if(first==null)first=value;else if(!first.equals(value))return true;}return false;
    }
    private static String metadataTag(String source,String name,boolean xml,List<Warning> warnings) {try{return metadata(tag(source,name,xml),warnings);}catch(Problem invalid){if(!warnings.contains(Warning.INVALID_BANK_INFORMATION))warnings.add(Warning.INVALID_BANK_INFORMATION);return "";}}
    private static boolean isXml(String source) {
        if(source.stripLeading().startsWith("<?xml"))return true;
        int first=source.indexOf('<');String header=first<0?source:source.substring(0,first);
        if(Pattern.compile("(?im)^\\s*(?:OFXHEADER|DATA)\\s*:\\s*(?:100|OFXSGML)\\s*$").matcher(header).find())return false;
        return Pattern.compile("</(?:TRNAMT|DTPOSTED)>",Pattern.CASE_INSENSITIVE).matcher(source).find();
    }
    private static String xmlText(String raw) {
        var result=new StringBuilder(raw.length());int position=0;
        while(position<raw.length()) {
            int amp=raw.indexOf('&',position);if(amp<0){result.append(raw,position,raw.length());break;}result.append(raw,position,amp);
            int end=raw.indexOf(';',amp+1);if(end<0 || end-amp>12)throw new Problem(Error.INVALID_TEXT);String token=raw.substring(amp+1,end);int code;
            switch(token){case "amp"->code='&';case "lt"->code='<';case "gt"->code='>';case "quot"->code='"';case "apos"->code='\'';default->{
                try{if(token.startsWith("#x"))code=Integer.parseInt(token.substring(2),16);else if(token.startsWith("#"))code=Integer.parseInt(token.substring(1));else throw new Problem(Error.INVALID_TEXT);}catch(NumberFormatException invalid){throw new Problem(Error.INVALID_TEXT);}
            }}
            if(!(code==9 || code==10 || code==13 || code>=0x20 && code<=0xd7ff || code>=0xe000 && code<=0xfffd || code>=0x10000 && code<=0x10ffff))throw new Problem(Error.INVALID_TEXT);
            result.appendCodePoint(code);position=end+1;
        }
        return result.toString();
    }
    private static String metadata(String value,List<Warning> warnings) {if(value.length()<=255)return value;warnings.add(Warning.INVALID_BANK_INFORMATION);return "";}
    private static String safe(String value,int max) {return value.length()<=max?value:"";}
    private static String trim(String value) {int a=0,b=value.length();while(a<b && whitespace(value.charAt(a)))a++;while(b>a && whitespace(value.charAt(b-1)))b--;return value.substring(a,b);}
    private static boolean whitespace(char c) {return c>=0x0009 && c<=0x000d || c==0x0020 || c==0x00a0 || c==0x1680 || c>=0x2000 && c<=0x200a || c==0x2028 || c==0x2029 || c==0x202f || c==0x205f || c==0x3000 || c==0xfeff;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Arquivo de extrato inválido ou excede o limite permitido.");}
    private static class Problem extends RuntimeException {final Error error;Problem(Error error){this.error=error;}}
    public enum Error { BAD_DATE, BAD_AMOUNT, SUBCENT_AMOUNT, OUT_OF_RANGE, ZERO_AMOUNT, INVALID_TEXT, UNTERMINATED }
    public enum Warning { FALLBACK_IDENTITY, DUPLICATE_IN_FILE, INVALID_BALANCE, INVALID_BANK_INFORMATION, MULTIPLE_BANKS, MULTIPLE_ACCOUNTS, CURRENCY_UNCONFIRMED, MULTIPLE_CURRENCIES }
    public record Row(int number,String rawDate,String rawAmount,String rawMemo,StatementValues values,Error error,List<Warning> warnings) {
        public Row {warnings=List.copyOf(warnings);}
        @Override public String toString(){return "OfxRow[number="+number+",redacted]";}
    }
    public record Balance(LocalDate date,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount) {@Override public String toString(){return "OfxBalance[redacted]";}}
    public record Preview(String bankCode,String accountReference,String currency,Balance balance,List<Row> rows,List<Warning> warnings) {
        public Preview {rows=List.copyOf(rows);warnings=List.copyOf(warnings);}
        @Override public String toString(){return "OfxPreview[rows="+rows.size()+",redacted]";}
    }
}
