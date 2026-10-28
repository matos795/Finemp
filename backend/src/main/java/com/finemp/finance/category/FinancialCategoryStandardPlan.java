package com.finemp.finance.category;

import com.finemp.company.Company;
import java.util.List;
import static com.finemp.finance.category.FinancialDirection.*;
import static com.finemp.finance.category.CashActivity.*;
import static com.finemp.finance.category.OperationalDreLine.*;
import static com.finemp.finance.category.CostBehavior.*;

/** Product metadata transcribed from the homologated planoPadrao, without operational data. */
public final class FinancialCategoryStandardPlan {
    public static final String VERSION="FINEMP_WEB_1";
    private FinancialCategoryStandardPlan() { }
    private static final List<Group> GROUPS=List.of(
            g(INCOME,"Receitas operacionais",OPERATING,REC,VARIABLE,
                    s("Vendas de produtos"),s("Prestação de serviços"),s("Recebimentos de cartão"),s("Outras receitas operacionais",OUT)),
            g(INCOME,"Receitas financeiras",OPERATING,RFIN,VARIABLE,
                    s("Rendimentos de aplicações"),s("Juros recebidos"),s("Descontos obtidos")),
            g(INCOME,"Outras entradas",FINANCING,NAO,VARIABLE,
                    s("Empréstimos e financiamentos recebidos"),s("Aporte dos sócios"),s("Venda de bens do ativo",NAO,INVESTING,null),s("Reembolsos",OUT,OPERATING,null)),
            g(EXPENSE,"Custos variáveis",OPERATING,CUS,VARIABLE,
                    s("Compras de mercadorias / matéria-prima"),s("Fretes"),s("Comissões",DCOM),s("Taxas de cartão e maquininha",DCOM),s("Embalagens")),
            g(EXPENSE,"Pessoal",OPERATING,DPES,FIXED,
                    s("Salários"),s("Férias"),s("13º salário"),s("FGTS"),s("INSS"),s("Vale-transporte"),s("Vale-refeição / alimentação"),s("Plano de saúde"),s("Rescisões"),s("Pró-labore")),
            g(EXPENSE,"Despesas administrativas",OPERATING,DADM,FIXED,
                    s("Aluguel"),s("Condomínio"),s("Energia elétrica"),s("Água"),s("Telefone e internet"),s("Contabilidade"),s("Sistemas e software"),
                    s("Material de escritório e limpeza"),s("Manutenção"),s("Seguros"),s("Honorários e consultorias")),
            g(EXPENSE,"Despesas comerciais",OPERATING,DCOM,FIXED,
                    s("Marketing e publicidade"),s("Viagens e deslocamentos"),s("Brindes e eventos")),
            g(EXPENSE,"Impostos e taxas",OPERATING,DTRI,FIXED,
                    s("Simples Nacional (DAS)",DED,null,VARIABLE),s("ICMS",DED,null,VARIABLE),s("ISS",DED,null,VARIABLE),s("PIS / COFINS",DED,null,VARIABLE),
                    s("IRPJ / CSLL",IR,null,VARIABLE),s("IPTU"),s("IPVA"),s("Taxas e licenças"),s("Parcelamento de impostos",NAO)),
            g(EXPENSE,"Despesas financeiras",OPERATING,DFIN,FIXED,
                    s("Tarifas bancárias"),s("Juros pagos"),s("IOF",null,null,VARIABLE),s("Multas",null,null,VARIABLE)),
            g(EXPENSE,"Investimentos",INVESTING,NAO,VARIABLE,
                    s("Máquinas e equipamentos"),s("Veículos"),s("Móveis e utensílios"),s("Reformas e obras")),
            g(EXPENSE,"Financiamentos e sócios",FINANCING,NAO,FIXED,
                    s("Parcelas de empréstimos (principal)"),s("Distribuição de lucros"),s("Retiradas dos sócios")));
    public static List<Group> groups() {return GROUPS;}
    private static Group g(FinancialDirection direction,String name,CashActivity activity,OperationalDreLine dre,CostBehavior cost,Subcategory...subs) {
        return new Group(direction,name,activity,dre,cost,List.of(subs));
    }
    private static Subcategory s(String name) {return s(name,null,null,null);}
    private static Subcategory s(String name,OperationalDreLine dre) {return s(name,dre,null,null);}
    private static Subcategory s(String name,OperationalDreLine dre,CashActivity activity,CostBehavior cost) {return new Subcategory(name,dre,activity,cost);}
    public record Group(FinancialDirection direction,String name,CashActivity activity,OperationalDreLine dre,CostBehavior cost,List<Subcategory> subs) {
        public Group {subs=List.copyOf(subs);}
        public FinancialCategory create(Company company,long sequence) {
            var row=FinancialCategory.group(company,direction,sequence,name);row.updateGroup(name,activity,dre,cost);return row;
        }
    }
    public record Subcategory(String name,OperationalDreLine dre,CashActivity activity,CostBehavior cost) {
        public FinancialCategory create(FinancialCategory parent,long sequence) {
            var row=FinancialCategory.subcategory(parent,sequence,name);
            row.updateSubcategory(parent,name,activity,dre,cost==parent.getCostBehavior()?null:cost);return row;
        }
    }
}
