package com.finemp.finance.entry;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntrySeriesCalendarTest {
    private EntrySeriesCalendar.Input input(SeriesMode mode,int quantity) {
        return new EntrySeriesCalendar.Input(mode,quantity,LocalDate.of(2026,1,31),LocalDate.of(2026,2,5),
                YearMonth.of(2026,1),LocalDate.of(2026,1,31),LocalDate.of(2026,2,7));
    }
    @Test void installmentsMustClampDueDatesFromTheirOriginalDayWithoutMovingCompetenceOrDocument() {
        var rows=EntrySeriesCalendar.generate(input(SeriesMode.INSTALLMENTS,3));
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::dueDate).containsExactly(
                LocalDate.of(2026,1,31),LocalDate.of(2026,2,28),LocalDate.of(2026,3,31));
        assertThat(rows).allSatisfy(row->{
            assertThat(row.documentDate()).isEqualTo(LocalDate.of(2026,1,31));
            assertThat(row.competence()).isEqualTo(YearMonth.of(2026,1));
            assertThat(row.entryDate()).isEqualTo(LocalDate.of(2026,2,5));
        });
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::number).containsExactly(1,2,3);
    }
    @Test void monthlyRecurrenceMustMoveDocumentAndCompetenceButPreserveEntryDateAndOnlyFirstPayment() {
        var rows=EntrySeriesCalendar.generate(input(SeriesMode.MONTHLY,3));
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::documentDate).containsExactly(
                LocalDate.of(2026,1,31),LocalDate.of(2026,2,28),LocalDate.of(2026,3,31));
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::competence).containsExactly(YearMonth.of(2026,1),YearMonth.of(2026,2),YearMonth.of(2026,3));
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::paymentDate).containsExactly(LocalDate.of(2026,2,7),null,null);
        assertThat(rows).allSatisfy(row->assertThat(row.entryDate()).isEqualTo(LocalDate.of(2026,2,5)));
    }
    @Test void leapYearAndDecemberRolloverMustRemainDateOnly() {
        var rows=EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.MONTHLY,4,LocalDate.of(2023,12,31),LocalDate.of(2023,12,1),
                YearMonth.of(2023,12),LocalDate.of(2023,12,31),null));
        assertThat(rows).extracting(EntrySeriesCalendar.Occurrence::dueDate).containsExactly(
                LocalDate.of(2023,12,31),LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),LocalDate.of(2024,3,31));
        assertThat(rows.get(2).competence()).isEqualTo(YearMonth.of(2024,2));
        assertThat(rows).allSatisfy(row->assertThat(row.paymentDate()).isNull());
    }
    @Test void singleEntryMustPreserveEveryExplicitDateWithoutInventingChronologicalRestrictions() {
        var row=EntrySeriesCalendar.generate(input(SeriesMode.SINGLE,1)).getFirst();
        assertThat(row.documentDate()).isEqualTo(LocalDate.of(2026,1,31));
        assertThat(row.entryDate()).isEqualTo(LocalDate.of(2026,2,5));
        assertThat(row.dueDate()).isEqualTo(LocalDate.of(2026,1,31));
        assertThat(row.paymentDate()).isEqualTo(LocalDate.of(2026,2,7));
    }
    @Test void resultMustBeImmutableAndBoundedAt120Occurrences() {
        var rows=EntrySeriesCalendar.generate(input(SeriesMode.MONTHLY,120));
        assertThat(rows).hasSize(120);
        assertThat(rows.getLast().number()).isEqualTo(120);assertThat(rows.getLast().quantity()).isEqualTo(120);
        assertThatThrownBy(rows::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void invalidModesQuantitiesOrRequiredDatesMustFailWithoutPartialResults() {
        assertThatThrownBy(()->EntrySeriesCalendar.generate(null)).isInstanceOf(IllegalArgumentException.class);
        for(var mode:SeriesMode.values()) {
            for(int quantity:new int[]{-1,0,121,Integer.MAX_VALUE})assertThatThrownBy(()->EntrySeriesCalendar.generate(input(mode,quantity))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->EntrySeriesCalendar.generate(input(SeriesMode.SINGLE,2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EntrySeriesCalendar.generate(input(SeriesMode.INSTALLMENTS,1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(null,1,LocalDate.of(2026,1,1),LocalDate.of(2026,1,1),YearMonth.of(2026,1),LocalDate.of(2026,1,1),null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.SINGLE,1,null,LocalDate.of(2026,1,1),YearMonth.of(2026,1),LocalDate.of(2026,1,1),null))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void technicalYearOverflowMustRejectTheWholeSchedule() {
        assertThatThrownBy(()->EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.MONTHLY,2,LocalDate.of(9999,12,31),LocalDate.of(9999,12,1),YearMonth.of(9999,12),LocalDate.of(9999,12,31),null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.SINGLE,1,LocalDate.of(0,1,1),LocalDate.of(2026,1,1),YearMonth.of(2026,1),LocalDate.of(2026,1,1),null))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void eachMonthlyDateFieldMustUseItsOwnIndependentOrigin() {
        var rows=EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.MONTHLY,2,LocalDate.of(2026,1,31),LocalDate.of(2026,7,1),
                YearMonth.of(2025,11),LocalDate.of(2026,4,30),null));
        var second=rows.get(1);
        assertThat(second.documentDate()).isEqualTo(LocalDate.of(2026,2,28));
        assertThat(second.competence()).isEqualTo(YearMonth.of(2025,12));
        assertThat(second.dueDate()).isEqualTo(LocalDate.of(2026,5,30));
        assertThat(second.entryDate()).isEqualTo(LocalDate.of(2026,7,1));
    }
}
