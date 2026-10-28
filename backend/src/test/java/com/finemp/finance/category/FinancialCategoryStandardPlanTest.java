package com.finemp.finance.category;

import com.finemp.company.Company;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialCategoryStandardPlanTest {
    @Test void everyRawMetadataFieldMustMatchTheIndependentlyExtractedHomologatedFixture()throws Exception {
        var company=mock(Company.class);when(company.getId()).thenReturn(UUID.randomUUID());
        var lines=new ArrayList<String>();lines.add("code\tparent\tname\tactivity\tdre\tcost");
        var counters=new EnumMap<FinancialDirection,Long>(FinancialDirection.class);
        for(var definition:FinancialCategoryStandardPlan.groups()) {
            long sequence=counters.merge(definition.direction(),1L,Long::sum);
            var group=definition.create(company,sequence);ReflectionTestUtils.setField(group,"id",UUID.randomUUID());lines.add(line(group,"-"));
            for(int i=0;i<definition.subs().size();i++)lines.add(line(definition.subs().get(i).create(group,i+1),group.getCode()));
        }
        try(var stream=getClass().getResourceAsStream("/reference/financial-category-standard-plan.tsv")) {
            assertThat(stream).isNotNull();
            assertThat(lines).containsExactlyElementsOf(new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines().toList());
        }
    }
    @Test void standardSubcategoriesMustStartWithExplicitDreInsteadOfTheManualAddSubFallback() {
        var definition=FinancialCategoryStandardPlan.groups().getFirst();
        var company=mock(Company.class);when(company.getId()).thenReturn(UUID.randomUUID());
        var group=definition.create(company,1);ReflectionTestUtils.setField(group,"id",UUID.randomUUID());
        var sub=definition.subs().getFirst().create(group,1);
        assertThat(sub.getDreLine()).isEqualTo(OperationalDreLine.REC);assertThat(sub.getCashActivity()).isNull();assertThat(sub.getCostBehavior()).isNull();
        group.updateGroup("Changed",CashActivity.FINANCING,OperationalDreLine.NAO,CostBehavior.FIXED);
        assertThat(sub.effective(group)).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.FINANCING,OperationalDreLine.REC,CostBehavior.FIXED));
    }
    @Test void productMetadataMustBeImmutableAcrossRequests() {
        assertThatThrownBy(()->FinancialCategoryStandardPlan.groups().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->FinancialCategoryStandardPlan.groups().getFirst().subs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    private String line(FinancialCategory row,String parent) {
        String activity=row.getCashActivity()==null?"-":switch(row.getCashActivity()){case OPERATING->"op";case INVESTING->"inv";case FINANCING->"fin";};
        String cost=row.getCostBehavior()==null?"-":row.getCostBehavior()==CostBehavior.FIXED?"F":"V";
        return String.join("\t",row.getCode(),parent,row.getName(),activity,row.getDreLine()==null?"-":row.getDreLine().name(),cost);
    }
}
