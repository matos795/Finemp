import type { CompanyPermission } from "../../auth/types/auth.types.ts"

/*
 * Sugestões editáveis do onboarding. São só atalhos de UX: preenchem a
 * lista de permissões, que o administrador pode ajustar antes de enviar.
 * O papel escolhido (ACCOUNTANT, CLIENT…) é descritivo e não concede
 * nenhuma permissão por si; vale apenas a lista explícita enviada.
 */

/** Contador: leitura financeira e classificação contábil. */
export const ACCOUNTANT_SUGGESTION: CompanyPermission[] = [
    "DASHBOARD_VIEW",
    "DRE_VIEW",
    "BALANCE_SHEET_VIEW",
    "CASH_FLOW_VIEW",
    "INDICATORS_VIEW",
    "ACCOUNTING_MOVEMENT_VIEW",
    "ACCOUNT_CLASSIFICATION_VIEW",
    "ACCOUNT_CLASSIFICATION_MANAGE",
    "MANUAL_ENTRY_MANAGE",
    "BANK_ACCOUNT_VIEW",
    "BUSINESS_PARTNER_VIEW",
    "RESULT_CENTER_VIEW",
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_PERIOD_VIEW",
]

/**
 * Cliente financeiro: o suficiente para operar o financeiro da Company
 * desde o primeiro lançamento: cadastrar conta, aplicar o plano padrão de
 * categorias e manter os clientes/fornecedores usados nos lançamentos
 * (#329). Centros de resultado continuam só com visualização.
 */
export const CLIENT_FINANCE_SUGGESTION: CompanyPermission[] = [
    "DASHBOARD_VIEW",
    "CASH_FLOW_VIEW",
    "INDICATORS_VIEW",
    "BANK_ACCOUNT_VIEW",
    "BANK_ACCOUNT_MANAGE",
    "BUSINESS_PARTNER_VIEW",
    "BUSINESS_PARTNER_MANAGE",
    "RESULT_CENTER_VIEW",
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_CATEGORY_MANAGE",
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
    "FINANCIAL_PERIOD_VIEW",
]
