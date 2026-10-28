package com.finemp.finance.bank;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import com.finemp.company.Company;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class BankAccountTest {
    private final Company company=new Company("Synthetic Financial Company","59595959000159");
    private final LocalDate date=LocalDate.of(2026,7,1);
    @Test void shouldPreserveNegativeOpeningBalanceAsExactCentsWithoutHiddenRounding(){
        var account=new BankAccount(company,"  Synthetic account  ",BankAccountType.CURRENT,new BigDecimal("-1.2300"),date,true);
        assertThat(account.getName()).isEqualTo("Synthetic account");assertThat(account.getOpeningBalance()).isEqualTo(new BigDecimal("-1.23"));assertThat(account.isActive()).isTrue();
    }
    @Test void shouldRejectSubCentValuesAndLargeExponentsBeforeAllocatingExpandedMoney(){
        assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
            for(var amount:new String[]{"0.001","1.005","1E+999999999","1E-999999999","100000000000000000.00"})
                assertThatThrownBy(()->new BankAccount(company,"Synthetic",BankAccountType.CASH,new BigDecimal(amount),date,true)).isInstanceOf(IllegalArgumentException.class);
        });
    }
    @Test void shouldNormalizeZeroWithoutExpandingAnArbitraryExponentAndAllowInactiveAccounts(){
        var account=new BankAccount(company,"Synthetic",BankAccountType.OTHER,new BigDecimal("0E+999999999"),date,false);
        assertThat(account.getOpeningBalance()).isEqualTo(new BigDecimal("0.00"));assertThat(account.isActive()).isFalse();
    }
    @Test void shouldRequireOwnerNameTypeAndDateWithoutCreatingUnspecifiedBankFields(){
        assertThatThrownBy(()->new BankAccount(null,"Synthetic",BankAccountType.CASH,BigDecimal.ZERO,date,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankAccount(company,"  ",BankAccountType.CASH,BigDecimal.ZERO,date,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankAccount(company,"Synthetic",null,BigDecimal.ZERO,date,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankAccount(company,"Synthetic",BankAccountType.CASH,BigDecimal.ZERO,null,true)).isInstanceOf(IllegalArgumentException.class);
    }
}
