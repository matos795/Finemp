/**
 * Telas da Área da Contabilidade, na mesma ordem, com os mesmos nomes e os
 * mesmos identificadores do protótipo contábil (menu "Contabilidade (equipe
 * Finemp)") e do docs/reference/HANDOFF_CONTABIL.md.
 *
 * `id` é o código da tela no protótipo/handoff (ex.: `cfcock`) e serve de
 * rastreabilidade com o backend. Painel sem contrato oficial FINEMP fica
 * "Em breve" e não exibe dado algum (docs/reference/accounting-area-map.md §5).
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */

/** Disponibilidade da tela. Só muda para "available" com contrato confirmado. */
export type AccountingScreenStatus = "soon" | "available"

export interface AccountingScreen {
    /** Código do protótipo / HANDOFF_CONTABIL (ex.: "cfcock"). */
    id: string
    /** Nome exato do item de menu no protótipo. */
    label: string
    /** Rota React (proposta do incremento; ver docs/reference/accounting-area-map.md). */
    path: string
    /** Selo do bloco de cabeçalho da tela (`.ind-tag`); null = tela sem bloco de cabeçalho no protótipo. */
    tag: string | null
    /** Título do bloco de cabeçalho da tela no protótipo. */
    title: string
    /** Barra "Competência em fechamento" (cfBar) no bloco de cabeçalho. */
    competenceBar: boolean
    /** Títulos dos painéis da tela no protótipo, na ordem ("" = painel sem título no protótipo). */
    panels: string[]
    /** Objetivo da tela (HANDOFF_CONTABIL, campo "Objetivo"). */
    objective: string
    /** Seção do HANDOFF_CONTABIL que especifica a tela. */
    handoffSection: string
    status: AccountingScreenStatus
}

export interface AccountingSubgroup {
    label: string
    screens: AccountingScreen[]
}

export interface AccountingModule {
    id: string
    label: string
    icon: "fech" | "ata"
    subgroups: AccountingSubgroup[]
}

export const ACCOUNTING_AREA_BASE_PATH = "/contabilidade"

/** Separador do menu do protótipo. */
export const ACCOUNTING_AREA_SECTION_LABEL = "Contabilidade (equipe Finemp)"

const CF = `${ACCOUNTING_AREA_BASE_PATH}/central-de-fechamento`
const PC = `${ACCOUNTING_AREA_BASE_PATH}/pacote-de-consolidacao`
const SC = `${ACCOUNTING_AREA_BASE_PATH}/sucesso-do-cliente`

const PACOTE_OBJ =
    "Folha do modelo de pacote de consolidação, com estrutura, códigos e fórmulas do modelo e os números da empresa."

/** Telas da Central com a barra de competência no protótipo (containers `#{id}Bar`). */
const COMPETENCE_BAR = new Set([
    "cfcock", "cfrot", "cfdocs", "cfbase", "cfrec", "cfantecip", "cfadiant", "cfpre", "cflanc",
    "cflalur", "cfpres", "cfimp", "cffolha", "cfvol",
])

/** Telas que, no protótipo, começam direto pelos painéis (sem `.ind-bloco`). */
const WITHOUT_HEADER_BLOCK = new Set(["checklist", "diag", "depara"])

function screen(
    id: string,
    label: string,
    path: string,
    tag: string,
    title: string,
    panels: string[],
    objective: string,
    handoffSection: string,
): AccountingScreen {
    return {
        id,
        label,
        path,
        tag: WITHOUT_HEADER_BLOCK.has(id) ? null : tag,
        title,
        competenceBar: COMPETENCE_BAR.has(id),
        panels,
        objective,
        handoffSection,
        status: "soon",
    }
}

export const accountingModules: AccountingModule[] = [
    {
        id: "fechamento",
        label: "Central de Fechamento",
        icon: "fech",
        subgroups: [
            {
                label: "Rotina do mês",
                screens: [
                    screen("cfcock", "Cockpit de Fechamento", `${CF}/cockpit`, "Central de Fechamento", "Cockpit de Fechamento",
                        ["Passos do fechamento"],
                        "Num lugar só, a situação do fechamento da competência para a empresa, com os passos e o que falta.", "4.1"),
                    screen("cfrot", "Roteiro do Analista", `${CF}/roteiro`, "Central de Fechamento", "Roteiro do Analista",
                        [""],
                        "Passo a passo do fechamento por competência, com responsável, prazo, situação, revisor e observação.", "4.2"),
                    screen("checklist", "Checklist de Fechamento", `${CF}/checklist`, "Central de Fechamento", "Checklist de Fechamento",
                        ["Análise de fechamento contábil", "Suportes dos saldos"],
                        "Checklist mensal de fechamento com validação automática por item e anexação dos suportes que comprovam cada saldo.", "4.3"),
                    screen("cfdocs", "Documentos e Classificação", `${CF}/documentos`, "Central de Fechamento", "Documentos e Classificação",
                        ["Enviar documentos", "Documentos"],
                        "Receber documentos do mês, extrair os dados, sugerir a contabilização e gerar lançamentos preparados após a conferência do analista.", "4.4"),
                    screen("cfbase", "Base de Classificação", `${CF}/base-de-classificacao`, "Central de Fechamento", "Base de Classificação",
                        ["Histórico por fornecedor (razão dos últimos 12 meses)", "Regras aprendidas", "Lançamentos classificados", "Históricos padrão"],
                        "Mostrar a base usada para classificar: histórico do razão, regras aprendidas e lançamentos preparados.", "4.5"),
                    screen("cfrec", "Lançamentos Recorrentes e Planilhas", `${CF}/recorrentes`, "Central de Fechamento", "Lançamentos Recorrentes e Planilhas",
                        ["Modelos recorrentes", "Planilhas de importação"],
                        "Cadastrar modelos de lançamentos que se repetem e gerar o lote da competência sem duplicar.", "4.6"),
                    screen("cfantecip", "Despesas Antecipadas", `${CF}/despesas-antecipadas`, "Central de Fechamento", "Despesas Antecipadas",
                        ["Itens e apropriação do mês", "Conciliação com o balanço"],
                        "Controlar despesas pagas antes do consumo, gerar a apropriação mensal e conciliar com o saldo do balanço.", "4.7"),
                    screen("cfadiant", "Adiantamentos (Fornecedores e Clientes)", `${CF}/adiantamentos`, "Central de Fechamento", "Adiantamentos a Fornecedores e de Clientes",
                        ["Adiantamentos que podem ser baixados", "Saldos por fornecedor e cliente"],
                        "Identificar adiantamentos em aberto que já têm nota ou fatura posterior e sugerir a baixa.", "4.8"),
                    screen("cfpre", "Pré-fechamento (Balancete e DRE)", `${CF}/pre-fechamento`, "Central de Fechamento", "Pré-fechamento (Balancete e DRE)",
                        ["Alertas e recomendações", "DRE de pré-fechamento", "Balancete de pré-fechamento"],
                        "Ver balancete e DRE da competência somando a base e os lançamentos preparados, antes de importar no Questor.", "4.9"),
                    screen("cflanc", "Lotes e Exportação Questor", `${CF}/lotes-questor`, "Central de Fechamento", "Lotes e Exportação Questor",
                        ["Lançamentos preparados", "Centro de custo e layouts do Questor"],
                        "Revisar, aprovar e exportar para o Questor, em arquivo, os lançamentos preparados da competência.", "4.10"),
                ],
            },
            {
                label: "Fiscal",
                screens: [
                    screen("cflalur", "IRPJ e CSLL · Lalur (Lucro Real)", `${CF}/fiscal/lalur`, "Central de Fechamento", "IRPJ e CSLL · Lalur",
                        ["Adições e exclusões", "Cálculo", "Parte B · controle de valores para períodos futuros"],
                        "Apuração trimestral do IRPJ e da CSLL no Lucro Real, com Lalur, Lacs e Parte B.", "5.1"),
                    screen("cfpres", "Validador de Lucro Presumido", `${CF}/fiscal/lucro-presumido`, "Central · Fiscal", "Validador de Lucro Presumido",
                        ["Erros e pontos de atenção", "Recálculo do trimestre", "Atividades e percentuais"],
                        "Recalcular o IRPJ e a CSLL do Lucro Presumido do trimestre e comparar com o contabilizado e o declarado.", "5.2"),
                    screen("cfimp", "Validador de ISS, PIS e COFINS", `${CF}/fiscal/iss-pis-cofins`, "Central · Fiscal", "Validador de ISS, PIS e COFINS",
                        ["Erros e pontos de atenção", "Apuração mês a mês", "Atividades e alíquotas"],
                        "Recalcular mês a mês PIS, COFINS e ISS e confrontar com o contabilizado e o declarado.", "5.3"),
                ],
            },
            {
                label: "Folha",
                screens: [
                    screen("cffolha", "Validador e Conciliador da Folha", `${CF}/folha/validador`, "Central · Folha", "Validador e Conciliador da Folha",
                        ["Relatórios da folha", "Totais da folha (confira)", "Testes e alertas", "Contas de ativo e passivo × folha", "Despesas de pessoal × folha"],
                        "Cruzar os totais da folha da competência com as contas de ativo e passivo da folha e com as despesas de pessoal.", "5.4"),
                ],
            },
            {
                label: "Qualidade da base",
                screens: [
                    screen("cfcong", "Períodos Congelados", `${CF}/qualidade/periodos-congelados`, "Fechamento", "Períodos Congelados",
                        ["Alterações em meses congelados", "Registro (quem, quando e por quê)"],
                        "Proteger meses fechados e listar mudanças da base nesses meses, que só valem depois de aceitas com motivo.", "6.1"),
                    screen("diag", "Diagnóstico das Bases", `${CF}/qualidade/diagnostico`, "Qualidade da base", "Diagnóstico das Bases",
                        ["Inconsistências", "Verificações aprovadas"],
                        "Verificar a integridade da base sincronizada no período.", "6.2"),
                    screen("depara", "De-Para do Plano de Contas", `${CF}/qualidade/de-para`, "Qualidade da base", "De-Para do Plano de Contas",
                        ["Classificação das contas", "Regras de-para ativas"],
                        "Garantir que toda conta do Questor tenha classificação gerencial.", "6.3"),
                    screen("cfvol", "Volumetria do Contrato", `${CF}/qualidade/volumetria`, "Central de Fechamento", "Volumetria do Contrato",
                        ["Ano a ano", "Lançamentos por mês", "Funcionários na folha por mês", "Reajuste de honorários por volume"],
                        "Medir o volume de trabalho do escritório para o cliente e apoiar o reajuste de honorários.", "6.4"),
                ],
            },
        ],
    },
    {
        id: "pacote",
        label: "Pacote de Consolidação",
        icon: "fech",
        subgroups: [
            {
                label: "Demonstrações e movimentações",
                screens: [
                    screen("gcmapa", "Estrutura e Mapa", `${PC}/mapa`, "Gestão Contábil", "Estrutura do pacote e mapa das folhas",
                        ["Folhas do pacote: o que foi criado e o que ficou de fora"],
                        "Lista das folhas do modelo de pacote: o que foi criado, o que não foi e onde ver.", "7"),
                    screen("gcbs", "Balanço (BS)", `${PC}/bs`, "Gestão Contábil · BS", "Balanço patrimonial",
                        ["Balanço no formato do pacote"], PACOTE_OBJ, "7"),
                    screen("gcpl", "Resultado (PL)", `${PC}/pl`, "Gestão Contábil · PL", "Demonstração do resultado",
                        ["Resultado no formato do pacote"], PACOTE_OBJ, "7"),
                    screen("gcexps", "Custos por Natureza (EXPS)", `${PC}/exps`, "Gestão Contábil · EXPS", "Custos e despesas por natureza",
                        ["Custos por natureza", "Maiores naturezas de gasto no ano"], PACOTE_OBJ, "7"),
                    screen("gcss", "Patrimônio Líquido (SS)", `${PC}/ss`, "Gestão Contábil · SS", "Mutações do patrimônio líquido",
                        ["Mutações do PL"], PACOTE_OBJ, "7"),
                    screen("gctfa", "Imobilizado (TFA)", `${PC}/tfa`, "Gestão Contábil · TFA / GROSS1", "Imobilizado e intangível",
                        ["Movimentação do valor líquido por classe (TFA)", "Posição bruta: custo, depreciação acumulada e líquido (GROSS1)"], PACOTE_OBJ, "7"),
                    screen("gpifa", "Intangível (IFA)", `${PC}/ifa`, "Gestão Contábil · ConsA", "Intangível (IFA)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                    screen("gcbb", "Empréstimos (BB)", `${PC}/bb`, "Gestão Contábil · BB", "Empréstimos e financiamentos",
                        ["Empréstimos no formato do pacote"], PACOTE_OBJ, "7"),
                    screen("gccf", "Fluxo de Caixa (CF)", `${PC}/cf`, "Gestão Contábil · CF", "Fluxo de caixa (método indireto)",
                        ["Fluxo de caixa no formato do pacote"], PACOTE_OBJ, "7"),
                    screen("gpcfinfo", "Informações do Fluxo de Caixa (CF_INFO)", `${PC}/cf-info`, "Gestão Contábil · ConsA", "Informações do Fluxo de Caixa (CF_INFO)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                ],
            },
            {
                label: "Informações complementares",
                screens: [
                    screen("gpinvest", "Investimentos (INVEST)", `${PC}/invest`, "Gestão Contábil · ConsA", "Investimentos (INVEST)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                    screen("gplease", "Arrendamentos (LEASE)", `${PC}/lease`, "Gestão Contábil · ConsA", "Arrendamentos (LEASE)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                    screen("gppldtl", "Detalhe de Outros Resultados (PLDTL)", `${PC}/pldtl`, "Gestão Contábil · ConsA", "Detalhe de outros resultados (PLDTL)",
                        ["Detalhe de outros resultados (PLDTL)"], PACOTE_OBJ, "7"),
                    screen("gpmgt", "Perdas de Rentabilidade (MGT)", `${PC}/mgt`, "Gestão Contábil · ConsA", "Perdas de Rentabilidade (MGT)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                    screen("gpexcng", "Câmbio (EXCNG)", `${PC}/excng`, "Gestão Contábil · ConsA", "Câmbio e moeda estrangeira (EXCNG)",
                        ["Câmbio e moeda estrangeira (EXCNG)"], PACOTE_OBJ, "7"),
                    screen("gpbonus", "Bônus (BONUS)", `${PC}/bonus`, "Gestão Contábil · ConsA", "Bônus de empregados (BONUS1 / BONUS2)",
                        ["Bônus de empregados (BONUS1 / BONUS2)"], PACOTE_OBJ, "7"),
                    screen("gpinfo", "Informações da Empresa (INFO)", `${PC}/info`, "Gestão Contábil · ConsA", "Informações da empresa (INFO)",
                        ["Informações da empresa (INFO)"], PACOTE_OBJ, "7"),
                ],
            },
            {
                label: "Intercompany",
                screens: [
                    screen("gpint", "Intercompany (INT_BS / INT_PL)", `${PC}/intercompany`, "Gestão Contábil · ConsA", "Intercompany (INT_BS / INT_PL)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                ],
            },
            {
                label: "Projeção e orçamento",
                screens: [
                    screen("gpfcst", "Projeção (PL_F)", `${PC}/projecao`, "Gestão Contábil · ConsA", "Projeção (PL_F)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                    screen("gpbud", "Orçamento (PL_B / EXPS_B)", `${PC}/orcamento`, "Gestão Contábil · ConsA", "Orçamento (PL_B / EXPS_B)",
                        ["Folha do pacote"], PACOTE_OBJ, "7"),
                ],
            },
        ],
    },
    {
        id: "sucesso",
        label: "Sucesso do Cliente",
        icon: "ata",
        subgroups: [
            {
                label: "",
                screens: [
                    screen("sucesso", "Acompanhamento das Ações", `${SC}/acoes`, "Sucesso do Cliente", "Acompanhamento das Ações",
                        ["Por responsável", ""],
                        "Acompanhar e cobrar as ações em aberto da Ata de Decisões, com resumo por responsável e registro de andamento.", "8"),
                ],
            },
        ],
    },
]

export const accountingScreens: AccountingScreen[] = accountingModules.flatMap((module) =>
    module.subgroups.flatMap((subgroup) => subgroup.screens),
)

/** Tela inicial da área (Cockpit de Fechamento). */
export const ACCOUNTING_AREA_HOME = accountingScreens[0].path

export function findAccountingScreenById(id: string) {
    return accountingScreens.find((candidate) => candidate.id === id) ?? null
}

export function findAccountingScreen(pathname: string) {
    for (const module of accountingModules) {
        for (const subgroup of module.subgroups) {
            const found = subgroup.screens.find((candidate) => candidate.path === pathname)

            if (found) {
                return { module, subgroup, screen: found }
            }
        }
    }

    return null
}
