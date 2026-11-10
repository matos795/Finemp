import {
    useCallback,
    useState,
} from "react"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { BarActions } from "../../../components/layout/bar-actions"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import { FinancialEntriesTable } from "../components/financial-entries-table"
import { FinancialEntryDialog } from "../components/financial-entry-dialog"
import { FinancialEntryFilters } from "../components/financial-entry-filters"
import { FinancialOnboardingPanel } from "../components/financial-onboarding-panel"
import { useFinancialEntries } from "../hooks/use-financial-entries"
import { useFinancialEntryCatalogs } from "../hooks/use-financial-entry-catalogs"
import { canManageFinancialEntries } from "../lib/financial-entry-access"
import { describeFinancialEntryError } from "../lib/financial-entry-errors"
import {
    financialOnboarding,
    type CatalogLoad,
} from "../lib/financial-onboarding"
import {
    emptyFinancialEntryFilter,
    type FinancialDirection,
    type FinancialEntry,
    type FinancialEntryFilter,
} from "../types/financial-entry.types"

/** A janela pertence à empresa em que foi aberta; trocar de empresa a fecha. */
type DialogState = { companyId: string } & (
    | { mode: "create", direction: FinancialDirection }
    | { mode: "edit", entry: FinancialEntry }
)

const listErrorMessages = {
    400: "Confira os filtros (a data final não pode ser anterior à inicial).",
    403: "Você não tem acesso aos lançamentos desta empresa.",
    404: "Lançamentos não estão disponíveis neste ambiente.",
}

/**
 * Fluxo de caixa › Lançamentos (renderLanc do protótipo): lista, filtros,
 * "+ Entrada", "+ Saída" e "Editar". Leitura exige FINANCIAL_ENTRY_VIEW
 * ou _MANAGE (rota); escrita, FINANCIAL_ENTRY_MANAGE. Baixa, exclusão,
 * repetição, anexos, lote e exportação ficam para entregas próprias.
 */
export function FinancialEntriesPage() {
    const { selectedCompany } = useCompanyAccess()

    // O estado operacional da tela pertence à Company ativa. Remontar o
    // conteúdo evita carregar filtros ou uma janela aberta de outra Company.
    return (
        <FinancialEntriesCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function FinancialEntriesCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = canManageFinancialEntries(hasPermission)

    const [filter, setFilter] = useState<FinancialEntryFilter>(emptyFinancialEntryFilter)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))
    const [dialog, setDialog] = useState<DialogState | null>(null)

    const entries = useFinancialEntries(companyId, filter, page)
    const { bankAccounts, categories, resultCenters } = useFinancialEntryCatalogs(companyId)

    const changeFilter = useCallback(
        (next: FinancialEntryFilter) => setFilter(next),
        [],
    )

    const bankList = bankAccounts.data ?? []
    const categoryList = categories.data ?? []
    const centerList = resultCenters.data ?? []

    // Antes de lançar: conta e categoria vêm de cadastros próprios. O React
    // só orienta (tela de contas, plano padrão por clique); nada é criado ao
    // abrir a página.
    const onboarding = financialOnboarding({
        hasPermission,
        bankAccounts: toCatalogLoad(bankAccounts),
        categories: toCatalogLoad(categories),
    })

    const catalogsReady = onboarding.ready

    const data = entries.data
    const items = data?.items ?? []

    return (
        <>
            {canManage ? (
                <BarActions>
                    <button
                        type="button"
                        className="btn ent"
                        disabled={!catalogsReady}
                        title={catalogsReady ? undefined : "Complete os passos indicados na tela antes de lançar."}
                        onClick={() => setDialog({ companyId: companyId!, mode: "create", direction: "INCOME" })}
                    >
                        + Entrada
                    </button>
                    <button
                        type="button"
                        className="btn sai"
                        disabled={!catalogsReady}
                        title={catalogsReady ? undefined : "Complete os passos indicados na tela antes de lançar."}
                        onClick={() => setDialog({ companyId: companyId!, mode: "create", direction: "EXPENSE" })}
                    >
                        + Saída
                    </button>
                </BarActions>
            ) : null}

            <FinancialEntryFilters
                filter={filter}
                onChange={changeFilter}
                bankAccounts={bankList}
                categories={categoryList}
                resultCenters={centerList}
            />

            {canManage && companyId ? (
                <FinancialOnboardingPanel
                    companyId={companyId}
                    onboarding={onboarding}
                />
            ) : null}

            {entries.isError ? (
                <QueryError
                    error={entries.error}
                    messages={listErrorMessages}
                    fallback={describeFinancialEntryError(entries.error, "list")}
                    onRetry={() => entries.refetch()}
                />
            ) : (
                <>
                    <div className="tools">
                        <span className="mut">
                            {data ? `${data.totalItems} lançamento(s)` : "Carregando…"}
                        </span>
                        <span className="sp" />
                    </div>

                    <div
                        className="panel"
                        style={{ padding: 0 }}
                    >
                        <div
                            className="tblwrap"
                            style={entries.isPlaceholderData ? { opacity: 0.6 } : undefined}
                        >
                            <FinancialEntriesTable
                                entries={items}
                                canManage={canManage && catalogsReady}
                                onEdit={(entry) => setDialog({ companyId: companyId!, mode: "edit", entry })}
                            />
                        </div>

                        {entries.isPending ? (
                            <div className="empty">Carregando…</div>
                        ) : items.length === 0 ? (
                            <div className="empty">
                                {JSON.stringify(filter) === JSON.stringify(emptyFinancialEntryFilter)
                                    ? canManage
                                        ? "Nenhum lançamento ainda. Use os botões + Entrada e + Saída."
                                        : "Nenhum lançamento ainda."
                                    : "Nenhum lançamento com esses filtros."}
                            </div>
                        ) : null}

                        <Pager
                            page={page}
                            totalPages={data?.totalPages ?? 0}
                            disabled={entries.isFetching}
                            onChange={setPage}
                        />
                    </div>
                </>
            )}

            {dialog && companyId && dialog.companyId === companyId && catalogsReady ? (
                <FinancialEntryDialog
                    key={dialog.mode === "edit" ? `edit-${dialog.entry.id}-${dialog.entry.version}` : `new-${dialog.direction}`}
                    companyId={companyId}
                    entry={dialog.mode === "edit" ? dialog.entry : undefined}
                    direction={dialog.mode === "edit" ? dialog.entry.values.direction : dialog.direction}
                    bankAccounts={bankList}
                    categories={categoryList}
                    resultCenters={resultCenters.isError ? [] : centerList}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}

/** Converte o estado de uma consulta de catálogo para a decisão de onboarding. */
function toCatalogLoad<T>(query: { isPending: boolean, isError: boolean, data?: T[] }): CatalogLoad<T> {
    if (query.isError) {
        return { status: "error" }
    }

    if (query.isPending || !query.data) {
        return { status: "loading" }
    }

    return { status: "ready", items: query.data }
}
