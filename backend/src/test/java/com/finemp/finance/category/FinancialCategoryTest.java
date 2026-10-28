package com.finemp.finance.category;

import com.finemp.company.Company;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialCategoryTest {
    Company company=mock(Company.class);
    @BeforeEach void prepare() {when(company.getId()).thenReturn(UUID.randomUUID());}
    FinancialCategory group(FinancialDirection direction) {
        var row=FinancialCategory.group(company,direction,1,"Synthetic Group");ReflectionTestUtils.setField(row,"id",UUID.randomUUID());return row;
    }
    @Test void groupDefaultsMustFollowComputationEvenWhenTheOldSelectorDisplayedFixed() {
        var income=group(FinancialDirection.INCOME);var expense=group(FinancialDirection.EXPENSE);
        assertThat(income.getCostBehavior()).isNull();
        assertThat(income.effective(null)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.OPERATING,OperationalDreLine.REC,CostBehavior.VARIABLE));
        assertThat(expense.effective(null)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.OPERATING,OperationalDreLine.DADM,CostBehavior.FIXED));
    }
    @Test void newlyCreatedSubcategoryMustInheritAbsentFieldsDynamically() {
        var parent=group(FinancialDirection.INCOME);var sub=FinancialCategory.subcategory(parent,1,"Synthetic Subcategory");
        assertThat(sub.getDreLine()).isNull();assertThat(sub.getCashActivity()).isNull();assertThat(sub.getCostBehavior()).isNull();
        parent.updateGroup("Changed",CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.FIXED);
        assertThat(sub.effective(parent)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.FIXED));
    }
    @Test void choosingGroupDreDuringEditMustSnapshotTheCurrentValueButKeepOtherNullsInherited() {
        var parent=group(FinancialDirection.INCOME);var sub=FinancialCategory.subcategory(parent,1,"Synthetic Subcategory");
        sub.updateSubcategory(parent,"Updated",null,null,null);
        assertThat(sub.getDreLine()).isEqualTo(OperationalDreLine.REC);
        parent.updateGroup("Changed",CashActivity.FINANCING,OperationalDreLine.NAO,CostBehavior.FIXED);
        assertThat(sub.effective(parent)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.FINANCING,OperationalDreLine.REC,CostBehavior.FIXED));
    }
    @Test void explicitOverridesMustRemainIndependentFromSubsequentGroupChanges() {
        var parent=group(FinancialDirection.EXPENSE);var sub=FinancialCategory.subcategory(parent,2,"Explicit");
        sub.updateSubcategory(parent,"Explicit",CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.VARIABLE);
        parent.updateGroup("Changed",CashActivity.FINANCING,OperationalDreLine.IR,CostBehavior.FIXED);
        assertThat(sub.effective(parent)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.VARIABLE));
    }
    @Test void classificationMustRejectAnUnrelatedParentOrAnotherTenant() {
        var parent=group(FinancialDirection.INCOME);var sub=FinancialCategory.subcategory(parent,1,"Synthetic Subcategory");
        assertThatThrownBy(()->sub.effective(group(FinancialDirection.INCOME))).isInstanceOf(IllegalArgumentException.class);
        var foreignCompany=mock(Company.class);when(foreignCompany.getId()).thenReturn(UUID.randomUUID());
        var foreign=FinancialCategory.group(foreignCompany,FinancialDirection.INCOME,1,"Foreign");
        ReflectionTestUtils.setField(foreign,"id",parent.getId());
        assertThatThrownBy(()->sub.effective(foreign)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->FinancialCategory.subcategory(sub,1,"Grandchild")).isInstanceOf(IllegalArgumentException.class);
    }
}
