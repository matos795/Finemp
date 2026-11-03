import type { CompanyPermission } from "../../features/auth/types/auth.types"
import type { MenuIconName } from "./menu-icons"

export interface NavigationItem {
    id: string
    label: string
    /** Rota React. Sem rota = tela ainda não ligada à API (inativa, D5). */
    path?: string
    /** Qualquer uma das permissões libera o item (só UX; o backend autoriza). */
    permissions?: CompanyPermission[]
}

export interface NavigationModule {
    id: string
    label: string
    icon: MenuIconName
    items: NavigationItem[]
}

/**
 * Menu do protótipo homologado (MENU), na mesma ordem e com os mesmos nomes.
 * Telas ainda não ligadas à API não têm rota e aparecem inativas (D5).
 */
export const navigationModules: NavigationModule[] = [
    {
        id: "fluxo",
        label: "Fluxo de caixa",
        icon: "fluxo",
        items: [
            {
                id: "painel",
                label: "Painel",
                path: "/dashboard",
                permissions: ["DASHBOARD_VIEW"],
            },
            { id: "lanc", label: "Lançamentos" },
            { id: "agenda", label: "Pagar e receber" },
            { id: "ext", label: "Extratos e conciliação" },
            { id: "mensal", label: "Fluxo mensal" },
            { id: "diario", label: "Fluxo diário" },
            { id: "bancos", label: "Contas e saldos" },
        ],
    },
    {
        id: "rel",
        label: "Relatórios",
        icon: "rel",
        items: [
            { id: "relRec", label: "Contas a receber em aberto" },
            { id: "relPag", label: "Contas a pagar em aberto" },
            { id: "dre", label: "DRE (competência)" },
            { id: "conc", label: "Conciliação caixa × DRE" },
            { id: "ciclo", label: "Ciclo financeiro e prazos" },
            { id: "ncg", label: "Capital de giro (NCG)" },
            { id: "bal", label: "Balanço patrimonial" },
            { id: "indServ", label: "Indicadores de serviços" },
            { id: "consist", label: "Verificação de consistência" },
            { id: "relApres", label: "Apresentação de resultados" },
        ],
    },
    {
        id: "grp",
        label: "Grupo de empresas",
        icon: "grp",
        items: [
            { id: "grpCons", label: "Consolidado (fluxo e DRE)" },
            { id: "grpSoc", label: "Estrutura societária" },
        ],
    },
    {
        id: "div",
        label: "Endividamento",
        icon: "div",
        items: [
            { id: "divPainel", label: "Visão geral" },
            { id: "divBanco", label: "Bancos" },
            { id: "divForn", label: "Fornecedores" },
            { id: "divImp", label: "Impostos" },
            { id: "divTerc", label: "Terceiros" },
            { id: "divCron", label: "Cronograma de pagamentos" },
        ],
    },
    {
        id: "crm",
        label: "Comercial",
        icon: "crm",
        items: [
            { id: "crmFunil", label: "Funil de vendas" },
            { id: "crmAtiv", label: "Agenda de follow-ups" },
            { id: "crmCli", label: "Carteira e ficha do cliente" },
            { id: "crmInd", label: "Indicadores comerciais" },
        ],
    },
    {
        id: "pes",
        label: "Gestão de pessoas",
        icon: "pes",
        items: [
            { id: "func", label: "Funcionários" },
            { id: "pesCusto", label: "Custo por funcionário" },
            { id: "pesCargo", label: "Cargos e descrições (DRC)" },
            { id: "pesAval", label: "Avaliação de desempenho" },
            { id: "pesPdi", label: "PDI e feedbacks" },
            { id: "pes9box", label: "Matriz 9-box e sucessão" },
        ],
    },
    {
        id: "ctr",
        label: "Clientes",
        icon: "ctr",
        items: [
            { id: "ctr", label: "Contratos e reajustes" },
            { id: "ctrInad", label: "Inadimplência e cobrança" },
            { id: "ctrFormas", label: "Recebimentos por forma" },
            { id: "fatMes", label: "Faturamento do mês" },
            { id: "nf", label: "Notas fiscais" },
        ],
    },
    {
        id: "est",
        label: "Estoque",
        icon: "est",
        items: [
            { id: "estProd", label: "Produtos e saldos" },
            { id: "estMov", label: "Movimentações (kardex)" },
            { id: "estNfe", label: "Notas fiscais (XML)" },
            { id: "estPreco", label: "Formação de preço" },
        ],
    },
    {
        id: "pat",
        label: "Bens",
        icon: "pat",
        items: [
            { id: "bens", label: "Controle de bens" },
        ],
    },
    {
        id: "pln",
        label: "Planejamento",
        icon: "pln",
        items: [
            { id: "plnCron", label: "Cronograma de encontros" },
            { id: "plnMetas", label: "Metas e indicadores" },
            { id: "plnAcoes", label: "Plano de ação" },
        ],
    },
    {
        id: "orc",
        label: "Orçamentos",
        icon: "orc",
        items: [
            { id: "orc", label: "Painel do orçamento" },
            { id: "orcRxP", label: "Real × previsto e versões" },
            { id: "orcPlano", label: "Plano orçamentário" },
            { id: "orcPes", label: "Planejamento de pessoal" },
            { id: "orcFix", label: "Gastos fixos" },
            { id: "orcVar", label: "Gastos variáveis" },
            { id: "orcDre", label: "DRE orçada × realizada" },
            { id: "orcFat", label: "Faturamento" },
            { id: "orcRank", label: "Ranking de gastos" },
            { id: "orcPe", label: "Ponto de equilíbrio" },
            { id: "orcCen", label: "Cenários" },
        ],
    },
    {
        id: "cad",
        label: "Cadastros",
        icon: "cad",
        items: [
            { id: "empresas", label: "Empresas" },
            { id: "cadEmp", label: "Dados da empresa" },
            { id: "parc", label: "Clientes e fornecedores" },
            {
                id: "cadContas",
                label: "Contas bancárias",
                path: "/bank-accounts",
                permissions: ["BANK_ACCOUNT_VIEW", "BANK_ACCOUNT_MANAGE"],
            },
            { id: "cadPlano", label: "Plano de contas" },
            { id: "cadCtb", label: "Plano de contas da contabilidade" },
            { id: "cr", label: "Centros de resultado" },
        ],
    },
    {
        id: "ajuda",
        label: "Ajuda",
        icon: "ajuda",
        items: [
            { id: "faq", label: "Perguntas frequentes" },
            { id: "tutor", label: "Tutoriais guiados" },
        ],
    },
    {
        id: "dem",
        label: "Demais",
        icon: "dem",
        items: [
            { id: "ctb", label: "Contabilidade" },
            { id: "perFech", label: "Fechamento de períodos" },
            { id: "audit", label: "Trilha de auditoria" },
            { id: "impexp", label: "Importação e exportação" },
            { id: "backup", label: "Backup" },
            {
                // PA7: a DRE contábil (Questor) já existente não tem lugar no
                // menu homologado; fica em "Demais" até decisão de produto.
                id: "dreContabil",
                label: "DRE contábil",
                path: "/dre",
                permissions: ["DRE_VIEW"],
            },
        ],
    },
]

/** Item visível: sem permissão exigida ou com qualquer uma delas. */
export function isItemVisible(
    item: NavigationItem,
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return (
        !item.permissions ||
        item.permissions.some(hasPermission)
    )
}

export function findNavigationByPath(
    modules: NavigationModule[],
    pathname: string,
) {
    for (const module of modules) {
        const item = module.items.find(
            (candidate) =>
                candidate.path !== undefined &&
                (pathname === candidate.path ||
                    pathname.startsWith(`${candidate.path}/`)),
        )

        if (item) {
            return { module, item }
        }
    }

    return null
}
