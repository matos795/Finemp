import { useState } from "react"
import { Panel } from "../../../components/finemp/panel"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useAllBankAccounts } from "../../bank-accounts/hooks/use-bank-accounts"
import {
    useStatementItemCounts,
    useStatementItems,
} from "../hooks/use-statement-items"
import { StatementItemsTable } from "../components/statement-items-table"
import type { StatementItemStatus } from "../types/statement.types"

/** Máximo do endpoint: o protótipo mostra todos os movimentos da conta. */
const PAGE_SIZE = 100

const statusPills: {
    status: StatementItemStatus | undefined
    label: string
    countKey: "PENDING" | "RECONCILED" | "IGNORED" | "ALL"
}[] = [
    { status: "PENDING", label: "Pendentes", countKey: "PENDING" },
    { status: "RECONCILED", label: "Conciliados", countKey: "RECONCILED" },
    { status: "IGNORED", label: "Ignorados", countKey: "IGNORED" },
    { status: undefined, label: "Todos", countKey: "ALL" },
]

const errorMessages = {
    403: "Você não tem acesso aos extratos desta empresa.",
    404: "A consulta de extratos não está disponível neste ambiente.",
}

/** Fluxo de caixa › Extratos e conciliação (ext do protótipo homologado). */
export function StatementsPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id

    const accounts =
        useAllBankAccounts(companyId)

    const [selection, setSelection] = useState<{
        companyId: string | undefined
        bankId: string
    } | null>(null)

    const accountItems = accounts.data ?? []

    // Como no protótipo: começa pela primeira conta ativa da empresa.
    const bankId =
        selection?.companyId === companyId &&
        accountItems.some((account) => account.id === selection?.bankId)
            ? selection?.bankId
            : (accountItems.find((account) => account.active) ?? accountItems[0])?.id

    const [status, setStatus] =
        useState<StatementItemStatus | undefined>("PENDING")

    const [page, setPage] =
        useCompanyPage(companyId, `${bankId}:${status ?? ""}`)

    const items =
        useStatementItems(companyId, bankId, { status }, { page, size: PAGE_SIZE })

    const counts =
        useStatementItemCounts(companyId, bankId)

    return (
        <>
            <Panel
                title="Importar extrato bancário"
                help={
                    <>
                        Aceita arquivo <b>OFX</b> (baixado no internet
                        banking, opção "Money/OFX") e planilhas <b>CSV</b> com
                        colunas de data, histórico e valor. Antes de gravar,
                        o sistema mostra uma prévia para conferência. A
                        importação não concilia nem baixa lançamentos.
                    </>
                }
            >
                {accounts.isError ? (
                    <QueryError
                        error={accounts.error}
                        messages={{
                            403: "Para escolher a conta do extrato é preciso acesso às contas bancárias desta empresa.",
                            404: "O cadastro de contas bancárias não está disponível neste ambiente.",
                        }}
                        fallback="Não foi possível carregar as contas bancárias."
                        onRetry={() => accounts.refetch()}
                    />
                ) : (
                    <div className="tools">
                        <div className="f">
                            <label htmlFor="exConta">Conta do extrato</label>
                            <select
                                id="exConta"
                                value={bankId ?? ""}
                                disabled={!accountItems.length}
                                onChange={(event) =>
                                    setSelection({
                                        companyId,
                                        bankId: event.target.value,
                                    })
                                }
                            >
                                {!accountItems.length && (
                                    <option value="">
                                        {accounts.isPending ? "Carregando…" : "Nenhuma conta cadastrada"}
                                    </option>
                                )}

                                {accountItems.map((account) => (
                                    <option
                                        key={account.id}
                                        value={account.id}
                                    >
                                        {account.name}
                                        {account.active ? "" : " (inativa)"}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="f">
                            <label>&nbsp;</label>
                            <button
                                type="button"
                                className="btn"
                                disabled
                                title="Em breve"
                            >
                                Escolher arquivo OFX / CSV
                            </button>
                        </div>
                    </div>
                )}
            </Panel>

            {bankId && (
                <>
                    <div className="tools">
                        <div className="tabs">
                            {statusPills.map((pill) => {
                                const count = counts[pill.countKey]

                                return (
                                    <button
                                        key={pill.label}
                                        type="button"
                                        className={pill.status === status ? "pill on" : "pill"}
                                        onClick={() => setStatus(pill.status)}
                                    >
                                        {pill.label}
                                        {count === undefined ? "" : ` (${count})`}
                                    </button>
                                )
                            })}
                        </div>
                    </div>

                    <div
                        className="panel"
                        style={{ padding: 0 }}
                    >
                        {items.isError ? (
                            <div style={{ padding: 16 }}>
                                <QueryError
                                    error={items.error}
                                    messages={errorMessages}
                                    fallback="Não foi possível carregar os movimentos do extrato."
                                    onRetry={() => items.refetch()}
                                />
                            </div>
                        ) : (
                            <>
                                <StatementItemsTable
                                    items={items.data?.items ?? []}
                                    loading={items.isPending}
                                    dimmed={items.isPlaceholderData}
                                />

                                {items.data && items.data.items.length === 0 && (
                                    <div className="empty">
                                        {counts.ALL === 0
                                            ? "Nenhum extrato importado para esta conta."
                                            : "Nada nesta situação."}
                                    </div>
                                )}

                                {items.data && items.data.totalPages > 1 && (
                                    <div style={{ padding: "0 16px 16px" }}>
                                        <Pager
                                            page={page}
                                            totalPages={items.data.totalPages}
                                            disabled={items.isFetching}
                                            onChange={setPage}
                                        />
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                </>
            )}
        </>
    )
}
