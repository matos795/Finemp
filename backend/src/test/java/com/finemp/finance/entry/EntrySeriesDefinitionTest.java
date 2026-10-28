package com.finemp.finance.entry;

import com.finemp.company.Company;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntrySeriesDefinitionTest {
    @Test void onlyActualSeriesModesAndOriginalQuantitiesAreRepresentable() {
        assertThat(new EntrySeriesDefinition(SeriesMode.MONTHLY,2).quantity()).isEqualTo(2);
        assertThat(new EntrySeriesDefinition(SeriesMode.INSTALLMENTS,120).quantity()).isEqualTo(120);
        for(var mode:new SeriesMode[]{null,SeriesMode.SINGLE})assertThatThrownBy(()->new EntrySeriesDefinition(mode,2)).isInstanceOf(IllegalArgumentException.class);
        for(var quantity:new int[]{0,1,121})assertThatThrownBy(()->new EntrySeriesDefinition(SeriesMode.MONTHLY,quantity)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void membershipMustPreserveOriginalPositionAndQuantityWithoutMoneyAllocation() {
        var company=company(UUID.randomUUID());var series=series(company,3);var entry=entry(company);
        var member=new EntrySeriesMembership(company,series,entry,2);
        assertThat(member.getNumber()).isEqualTo(2);assertThat(member.getQuantity()).isEqualTo(3);assertThat(member.getSeriesId()).isEqualTo(series.getId());
        for(var number:new int[]{0,4})assertThatThrownBy(()->new EntrySeriesMembership(company,series,entry,number)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothReferencesMustBelongToTheSamePersistedCompanyAndHaveIdentities() {
        var company=company(UUID.randomUUID());var other=company(UUID.randomUUID());var series=series(company,2);var entry=entry(company);
        assertThatThrownBy(()->new EntrySeriesMembership(other,series,entry,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EntrySeriesMembership(company,series(other,2),entry,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EntrySeriesMembership(company,series,entry(other),1)).isInstanceOf(IllegalArgumentException.class);
        when(entry.getId()).thenReturn(null);assertThatThrownBy(()->new EntrySeriesMembership(company,series,entry,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nullContextAndUnpersistedSeriesMustNotProduceAnUnscopedMembership() {
        var company=company(UUID.randomUUID());var series=series(company,2);var entry=entry(company);
        assertThatThrownBy(()->new FinancialEntrySeries(company,new EntrySeriesDefinition(SeriesMode.MONTHLY,2),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EntrySeriesMembership(null,series,entry,1)).isInstanceOf(IllegalArgumentException.class);
        when(series.getId()).thenReturn(null);assertThatThrownBy(()->new EntrySeriesMembership(company,series,entry,1)).isInstanceOf(IllegalArgumentException.class);
    }
    Company company(UUID id) {var value=mock(Company.class);when(value.getId()).thenReturn(id);return value;}
    FinancialEntrySeries series(Company company,int quantity) {var value=mock(FinancialEntrySeries.class);when(value.getId()).thenReturn(UUID.randomUUID());when(value.getCompany()).thenReturn(company);when(value.getDefinition()).thenReturn(new EntrySeriesDefinition(SeriesMode.MONTHLY,quantity));return value;}
    FinancialEntry entry(Company company) {var value=mock(FinancialEntry.class);when(value.getId()).thenReturn(UUID.randomUUID());when(value.getCompany()).thenReturn(company);return value;}
}
