package com.finemp.finance.reconciliation;

import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Bounded text parser. It never resolves XML entities, performs I/O or persists statements. */
public class OfxPreviewParser {
    private static final int MAX_BYTES=16*1024*1024,MAX_ROWS=50000;
    private static final Charset WINDOWS=Charset.forName("windows-1252");
    private static final Pattern OPEN=Pattern.compile("<STMTTRN>",Pattern.CASE_INSENSITIVE);
    private static final Pattern END=Pattern.compile("</STMTTRN>|<STMTTRN>|</BANKTRANLIST>",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEDGER=Pattern.compile("<LEDGERBAL>",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEDGER_END=Pattern.compile("</LEDGERBAL>",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Pattern> TAGS=java.util.stream.Stream.of("BANKID","ACCTID","CURDEF","BALAMT","DTASOF","DTPOSTED","TRNAMT","NAME","MEMO","TRNTYPE","FITID","CHECKNUM","REFNUM")
            .collect(java.util.stream.Collectors.toUnmodifiableMap(name->name,name->Pattern.compile("<"+name+">([^<\\r\\n]*)",Pattern.CASE_INSENSITIVE)));
    public Preview parse(byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)throw invalid();
        String source=decode(bytes);var rows=new ArrayList<Row>();var identities=new HashMap<String,List<Integer>>();
        var open=OPEN.matcher(source);int position=0;
        while(open.find(position)) {
            if(rows.size()>=MAX_ROWS)throw invalid();int begin=open.end();var end=END.matcher(source);
            if(!end.find(begin)){rows.add(new Row(rows.size()+1,"","","",null,Error.UNTERMINATED,List.of()));break;}
            var block=source.substring(begin,end.start());int index=rows.size();var row=parseRow(index+1,block);rows.add(row);
            if(row.values()!=null)identities.computeIfAbsent(row.values().identityKey(),ignored->new ArrayList<>()).add(index);
            position=source.regionMatches(true,end.start(),"<STMTTRN>",0,9)?end.start():end.end();
        }
        if(rows.isEmpty())throw invalid();
        for(var duplicates:identities.values())if(duplicates.size()>1)for(var index:duplicates){var row=rows.get(index);var warnings=new ArrayList<>(row.warnings());warnings.add(Warning.DUPLICATE_IN_FILE);rows.set(index,new Row(row.number(),row.rawDate(),row.rawAmount(),row.rawMemo(),row.values(),row.error(),warnings));}
        var warnings=new ArrayList<Warning>();var bank=metadata(tag(source,"BANKID"),warnings);var account=metadata(tag(source,"ACCTID"),warnings);var currency=metadata(tag(source,"CURDEF"),warnings);Balance balance=null;
        if(hasMultiple(source,"BANKID"))warnings.add(Warning.MULTIPLE_BANKS);
        if(hasMultiple(source,"ACCTID"))warnings.add(Warning.MULTIPLE_ACCOUNTS);
        if(hasMultiple(source,"CURDEF"))warnings.add(Warning.MULTIPLE_CURRENCIES);
        if(!"BRL".equalsIgnoreCase(currency))warnings.add(Warning.CURRENCY_UNCONFIRMED);
        var ledger=LEDGER.matcher(source);
        if(ledger.find()) {
            var ledgerEnd=LEDGER_END.matcher(source);int end=ledgerEnd.find(ledger.end())?ledgerEnd.start():source.length();String block=source.substring(ledger.end(),end);
            try {balance=new Balance(date(tag(block,"DTASOF")),amount(tag(block,"BALAMT"),true));}catch(Problem invalid) {warnings.add(Warning.INVALID_BALANCE);}
        }
        return new Preview(bank,account,currency,balance,rows,warnings);
    }
    private Row parseRow(int number,String block) {
        String rawDate=tag(block,"DTPOSTED"),rawAmount=tag(block,"TRNAMT");String name=tag(block,"NAME"),memo=tag(block,"MEMO");
        String text=name.isEmpty()?memo:memo.isEmpty() || name.equals(memo)?name:name+" – "+memo;if(text.isEmpty())text=tag(block,"TRNTYPE");
        String id=tag(block,"FITID"),document=tag(block,"CHECKNUM");if(document.isEmpty())document=tag(block,"REFNUM");
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
    private static String tag(String block,String name) {
        var matcher=TAGS.get(name).matcher(block);return matcher.find()?trim(matcher.group(1)):"";
    }
    private static boolean hasMultiple(String source,String name) {
        var matcher=TAGS.get(name).matcher(source);String first=null;
        while(matcher.find()){String value=trim(matcher.group(1));if(value.isEmpty())continue;if(first==null)first=value;else if(!first.equals(value))return true;}return false;
    }
    private static String decode(byte[] bytes) {
        String header=new String(bytes,0,Math.min(bytes.length,600),StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        if(header.contains("CHARSET:1252") || header.contains("ENCODING:USASCII") || header.contains("ISO-8859-1") || header.contains("WINDOWS-1252"))return new String(bytes,WINDOWS);
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException invalid){return new String(bytes,WINDOWS);}
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
