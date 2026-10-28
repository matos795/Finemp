package com.finemp.finance.entry;

import java.time.*;
import java.util.List;
import java.util.ArrayList;

/** Date-only schedule from the homologated salvarLanc/addMeses, independent of monetary policy. */
public final class EntrySeriesCalendar {
    private EntrySeriesCalendar() { }
    public record Input(SeriesMode mode,int quantity,LocalDate documentDate,LocalDate entryDate,YearMonth competence,
                        LocalDate dueDate,LocalDate firstPaymentDate) { }
    public record Occurrence(int number,int quantity,LocalDate documentDate,LocalDate entryDate,YearMonth competence,
                             LocalDate dueDate,LocalDate paymentDate) { }
    public static List<Occurrence> generate(Input input) {
        if(input==null || input.mode()==null || input.competence()==null)throw invalid();
        if(input.mode()==SeriesMode.SINGLE ? input.quantity()!=1 : input.quantity()<2 || input.quantity()>120)throw invalid();
        date(input.documentDate());date(input.entryDate());date(input.dueDate());year(input.competence().getYear());
        if(input.firstPaymentDate()!=null)date(input.firstPaymentDate());
        var rows=new ArrayList<Occurrence>(input.quantity());
        try {
            for(int i=0;i<input.quantity();i++) {
                LocalDate due=input.dueDate().plusMonths(i);
                LocalDate document=input.mode()==SeriesMode.MONTHLY?input.documentDate().plusMonths(i):input.documentDate();
                YearMonth competence=input.mode()==SeriesMode.MONTHLY?input.competence().plusMonths(i):input.competence();
                date(due);date(document);year(competence.getYear());
                rows.add(new Occurrence(i+1,input.quantity(),document,input.entryDate(),competence,due,i==0?input.firstPaymentDate():null));
            }
        }catch(DateTimeException overflow) {throw invalid();}
        return List.copyOf(rows);
    }
    private static void date(LocalDate date) {if(date==null)throw invalid();year(date.getYear());}
    private static void year(int year) {if(year<1 || year>9999)throw invalid();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Calendário de lançamentos inválido.");}
}
