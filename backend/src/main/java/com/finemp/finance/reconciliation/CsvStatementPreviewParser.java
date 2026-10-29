package com.finemp.finance.reconciliation;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Bounded CSV preview with explicit separator; never evaluates formulas or persists data. */
public class CsvStatementPreviewParser {
    private static final int MAX_ROWS=50040,MAX_FIELDS=200,MAX_FIELD=4000;
    private static final Pattern WORD_D=Pattern.compile("\\bD\\b",Pattern.CASE_INSENSITIVE),WORD_C=Pattern.compile("\\bC\\b",Pattern.CASE_INSENSITIVE);
    public Preview parse(byte[] bytes,char separator) {
        if(separator!=',' && separator!=';' && separator!='\t' && separator!='|')throw invalid();
        var collector=new Collector();tokenize(StatementTextDecoder.decodeCsv(bytes),separator,collector::accept);
        if(collector.header==null)throw invalid();return collector.preview();
    }
    private static class Collector {
        Header header;int number;final List<Row> rows=new ArrayList<>();final Map<String,List<Integer>> identities=new HashMap<>();
        void accept(List<String> fields) {
            number++;
            if(header==null){if(number>40)throw invalid();header=Header.find(fields);return;}
            int index=rows.size();var row=parseRow(number,fields,header);rows.add(row);
            if(row.values()!=null)identities.computeIfAbsent(row.values().identityKey(),ignored->new ArrayList<>()).add(index);
        }
        Preview preview() {
            for(var duplicate:identities.values())if(duplicate.size()>1)for(var index:duplicate){var row=rows.get(index);var warnings=new ArrayList<>(row.warnings());warnings.add(Warning.DUPLICATE_IN_FILE);rows.set(index,new Row(row.number(),row.rawDate(),row.rawAmount(),row.rawMemo(),row.values(),row.error(),warnings));}
            return new Preview(rows,List.of(Warning.CURRENCY_UNCONFIRMED));
        }
    }
    private static Row parseRow(int number,List<String> fields,Header h) {
        String rawDate=cell(fields,h.date()),memo=cell(fields,h.memo()).trim();if(memo.isEmpty())memo="(sem histórico)";
        String rawAmount=h.amount()>=0?cell(fields,h.amount()):cell(fields,h.credit())+" | "+cell(fields,h.debit());
        if(fields.size()!=h.columns())return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(memo,4000),null,Error.BAD_COLUMN_COUNT,List.of());
        String normalized=StatementValues.normalize(memo);
        if(normalized.startsWith("saldo") || memo.matches("(?i)^s\\s*a\\s*l\\s*d\\s*o.*"))return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(memo,4000),null,null,List.of(Warning.BALANCE_ROW));
        try {
            LocalDate date=date(rawDate);BigDecimal value;
            if(h.amount()>=0) {
                String raw=cell(fields,h.amount());boolean negative=(raw.contains("-") || raw.contains("(") || WORD_D.matcher(raw).find()) && !WORD_C.matcher(raw).find();if(raw.contains("-"))negative=true;
                String leading=raw.stripLeading();
                if(leading.startsWith("=") || leading.startsWith("@") || leading.matches("^[+-]\\s*[A-Za-z_][A-Za-z0-9_.]*\\s*\\(.*")
                        || raw.contains("+") && !leading.matches("^\\+\\s*(?:R\\$\\s*)?[0-9.,]+(?:\\s*[DCdc])?\\s*$"))throw new Problem(Error.BAD_AMOUNT);
                String cleaned=raw.replaceAll("[^0-9,.-]","");value=numeric(cleaned,false).abs();
                if(negative || cell(fields,h.direction()).stripLeading().toLowerCase(Locale.ROOT).startsWith("d"))value=value.negate();
            }else {
                BigDecimal credit=numeric(cleanNumber(cell(fields,h.credit())),true).abs(),debit=numeric(cleanNumber(cell(fields,h.debit())),true).abs();value=exact(credit.subtract(debit),false);
            }
            if(value.signum()==0)throw new Problem(Error.ZERO_AMOUNT);
            StatementValues values;try{values=new StatementValues(date,value,memo,"",cell(fields,h.document()));}catch(IllegalArgumentException invalid){throw new Problem(Error.INVALID_TEXT);}
            return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(memo,4000),values,null,List.of(Warning.FALLBACK_IDENTITY));
        }catch(Problem problem){return new Row(number,safe(rawDate,64),safe(rawAmount,64),safe(memo,4000),null,problem.error,List.of());}
    }
    private static BigDecimal numeric(String input,boolean emptyAsZero) {
        if(input.isEmpty()){if(emptyAsZero)return BigDecimal.ZERO.setScale(2);throw new Problem(Error.BAD_AMOUNT);}if(input.length()>64)throw new Problem(Error.BAD_AMOUNT);
        String normalized=input;
        if(input.contains(","))normalized=input.replace(".","").replaceFirst(",",".");
        else if(input.matches("-?[0-9]{1,3}\\.[0-9]{3}"))throw new Problem(Error.AMBIGUOUS_AMOUNT);
        else if(input.matches("-?[0-9]{1,3}(\\.[0-9]{3}){2,}"))normalized=input.replace(".","");
        try{return exact(new BigDecimal(normalized),true);}catch(NumberFormatException invalid){throw new Problem(Error.BAD_AMOUNT);}
    }
    private static BigDecimal exact(BigDecimal number,boolean zeroAllowed) {
        if(!zeroAllowed && number.signum()==0)throw new Problem(Error.ZERO_AMOUNT);
        try{var compact=number.signum()==0?BigDecimal.ZERO:number.stripTrailingZeros();if(compact.scale()>2)throw new Problem(Error.SUBCENT_AMOUNT);if((long)compact.precision()-compact.scale()>17)throw new Problem(Error.OUT_OF_RANGE);return compact.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException invalid){throw new Problem(Error.BAD_AMOUNT);}
    }
    private static String cleanNumber(String value){return value.replaceFirst("R\\$\\s?","").replaceAll("\\s","");}
    private static LocalDate date(String raw) {
        String value=raw.trim();try {
            if(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.*")){int year=Integer.parseInt(value.substring(0,4));if(year<1)throw new Problem(Error.BAD_DATE);return LocalDate.of(year,Integer.parseInt(value.substring(5,7)),Integer.parseInt(value.substring(8,10)));}
            var matcher=Pattern.compile("^([0-9]{1,2})/([0-9]{1,2})/([0-9]{2}|[0-9]{4})$").matcher(value);
            if(matcher.matches()){int year=Integer.parseInt(matcher.group(3));if(matcher.group(3).length()==2)year+=2000;if(year<1)throw new Problem(Error.BAD_DATE);return LocalDate.of(year,Integer.parseInt(matcher.group(2)),Integer.parseInt(matcher.group(1)));}
        }catch(DateTimeException invalid){throw new Problem(Error.BAD_DATE);}throw new Problem(Error.BAD_DATE);
    }
    private static void tokenize(String source,char separator,Consumer<List<String>> consumer) {
        var row=new ArrayList<String>();var field=new StringBuilder();boolean quoted=false,afterQuote=false,atRowStart=true;int rows=0;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i);atRowStart=false;
            if(quoted){if(c=='"'){if(i+1<source.length() && source.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;afterQuote=true;}}else field.append(c);}
            else if(c==separator){add(row,field);afterQuote=false;}
            else if(c=='\n' || c=='\r'){add(row,field);if(++rows>MAX_ROWS)throw invalid();consumer.accept(List.copyOf(row));row.clear();afterQuote=false;atRowStart=true;if(c=='\r' && i+1<source.length() && source.charAt(i+1)=='\n')i++;}
            else if(afterQuote)throw invalid();
            else if(c=='"'){if(field.length()!=0)throw invalid();quoted=true;}
            else field.append(c);
            if(field.length()>MAX_FIELD)throw invalid();
        }
        if(quoted)throw invalid();if(!atRowStart || !row.isEmpty() || field.length()>0 || afterQuote){add(row,field);if(++rows>MAX_ROWS)throw invalid();consumer.accept(List.copyOf(row));}
    }
    private static void add(List<String> row,StringBuilder field){if(row.size()>=MAX_FIELDS)throw invalid();row.add(field.toString());field.setLength(0);}
    private static String cell(List<String> row,int index){return index<0 || index>=row.size()?"":row.get(index);}
    private static String safe(String value,int max){return value.length()<=max?value:"";}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Arquivo CSV inválido ou excede o limite permitido.");}
    private static class Problem extends RuntimeException {final Error error;Problem(Error error){this.error=error;}}
    private record Header(int date,int amount,int credit,int debit,int memo,int document,int direction,int columns) {
        static Header find(List<String> fields) {
            var names=fields.stream().map(StatementValues::normalize).toList();int date=find(names,"data"),amount=find(names,"valor","montante","quantia"),credit=find(names,"credito","entrada"),debit=find(names,"debito","saida");
            if(date<0 || amount<0 && credit<0 && debit<0)return null;
            int direction=-1;for(int i=0;i<names.size();i++)if(Set.of("d/c","c/d","tipo").contains(names.get(i))){direction=i;break;}
            return new Header(date,amount,credit,debit,find(names,"historico","descricao","lancamento","memo","detalhe"),find(names,"documento","doc","n doc","numero"),direction,fields.size());
        }
        private static int find(List<String> names,String...aliases){for(int i=0;i<names.size();i++)for(var alias:aliases)if(names.get(i).equals(alias) || names.get(i).startsWith(alias))return i;return -1;}
    }
    public enum Error { BAD_DATE,BAD_AMOUNT,BAD_COLUMN_COUNT,SUBCENT_AMOUNT,AMBIGUOUS_AMOUNT,OUT_OF_RANGE,ZERO_AMOUNT,INVALID_TEXT }
    public enum Warning { FALLBACK_IDENTITY,DUPLICATE_IN_FILE,BALANCE_ROW,CURRENCY_UNCONFIRMED }
    public record Row(int number,String rawDate,String rawAmount,String rawMemo,StatementValues values,Error error,List<Warning> warnings){public Row{warnings=List.copyOf(warnings);}@Override public String toString(){return "CsvRow[number="+number+",redacted]";}}
    public record Preview(List<Row> rows,List<Warning> warnings){public Preview{rows=List.copyOf(rows);warnings=List.copyOf(warnings);}@Override public String toString(){return "CsvPreview[rows="+rows.size()+",redacted]";}}
}
