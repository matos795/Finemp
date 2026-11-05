import {
    useRef,
    useState,
} from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { useToast } from "../../../components/finemp/toast-context"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { previewStatement } from "../api/statements-api"
import { StatementImportDialog } from "../components/statement-import-dialog"
import {
    csvSeparators,
    STATEMENT_FILE_ACCEPT,
    statementFormatOf,
} from "../lib/statement-file"
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
import type {
    CsvSeparator,
    StatementItemStatus,
    StatementPreviewSession,
} from "../types/statement.types"

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

function describePreviewError(error: unknown) {
    switch (apiErrorStatus(error)) {
        case 400:
            return "Não foi possível ler o arquivo. Confira o formato e, no CSV, o separador."
        case 403:
            return "Você não tem permissão para importar extratos nesta empresa."
        case 404:
            return "A análise de extratos não está disponível neste ambiente."
        case 413:
            return "O arquivo excede o limite de tamanho da análise."
        case 415:
            return "Tipo de arquivo ou codificação não suportados."
        case 429:
            return "Outra análise de arquivo está em andamento. Tente de novo em instantes."
        default:
            return "Não foi possível analisar o arquivo."
    }
}

/** Fluxo de caixa › Extratos e conciliação (ext do protótipo homologado). */
function StatementsCompanyPage() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    const companyId = selectedCompany?.id

    // Prévia e importação exigem FINANCIAL_ENTRY_MANAGE ALL (só UX aqui).
    const canImport = hasPermission("FINANCIAL_ENTRY_MANAGE")

    const queryClient = useQueryClient()
    const showToast = useToast()
    const fileInput = useRef<HTMLInputElement>(null)

    const [separator, setSeparator] =
        useState<CsvSeparator>("SEMICOLON")

    const [previewSession, setPreviewSession] =
        useState<StatementPreviewSession | null>(null)

    const preview = useMutation({
        mutationFn: ({
            companyId: requestCompanyId,
            file,
            bankId,
            separator: requestSeparator,
        }: {
            companyId: string
            file: File
            bankId: string
            separator: CsvSeparator
        }) => {
            const format = statementFormatOf(file.name)!

            return previewStatement(
                requestCompanyId,
                bankId,
                file,
                format,
                requestSeparator,
            )
        },
        onSuccess: setPreviewSession,
        onError: (error) => {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            showToast(describePreviewError(error), true)
        },
    })

    function handleFile(file: File | undefined) {
        if (!file || !bankId) {
            return
        }

        if (!statementFormatOf(file.name)) {
            // PA2: o protótipo aceitava Excel; a API aceita OFX e CSV.
            showToast("Use arquivo OFX ou CSV. Planilhas Excel ainda não são aceitas.", true)
            return
        }

        preview.mutate({
            companyId: companyId!,
            file,
            bankId,
            separator,
        })
    }

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

    function handleStatementIgnored() {
        if (
            status === "PENDING" &&
            page > 0 &&
            items.data?.items.length === 1
        ) {
            setPage(page - 1)
        }
    }

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

                        {canImport && (
                            <>
                                <div className="f">
                                    <label htmlFor="exSep">Separador (CSV)</label>
                                    <select
                                        id="exSep"
                                        value={separator}
                                        onChange={(event) =>
                                            setSeparator(event.target.value as CsvSeparator)
                                        }
                                    >
                                        {csvSeparators.map((option) => (
                                            <option
                                                key={option.value}
                                                value={option.value}
                                            >
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="f">
                                    <label>&nbsp;</label>
                                    <button
                                        type="button"
                                        className="btn"
                                        disabled={!bankId || preview.isPending}
                                        onClick={() => fileInput.current?.click()}
                                    >
                                        {preview.isPending
                                            ? "Analisando arquivo…"
                                            : "Escolher arquivo OFX / CSV"}
                                    </button>

                                    <input
                                        ref={fileInput}
                                        type="file"
                                        accept={STATEMENT_FILE_ACCEPT}
                                        hidden
                                        onChange={(event) => {
                                            const file = event.target.files?.[0]
                                            event.target.value = ""
                                            handleFile(file)
                                        }}
                                    />
                                </div>
                            </>
                        )}
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

                        <span className="sp" />

                        {status === "PENDING" && Boolean(counts.PENDING) && (
                            <button
                                type="button"
                                className="btn sec sm"
                                disabled
                                title="Conciliar ainda não existe na API"
                            >
                                Aceitar todas as sugestões
                            </button>
                        )}
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
                                    companyId={companyId}
                                    canIgnore={canImport}
                                    onIgnored={handleStatementIgnored}
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
            {previewSession &&
                companyId &&
                previewSession.companyId === companyId && (
                    <StatementImportDialog
                        // Nova prévia = nova seleção e novas confirmações.
                        key={previewSession.sha256 + previewSession.file.name}
                        companyId={companyId}
                        session={previewSession}
                        accountName={
                            accountItems.find(
                                (account) => account.id === previewSession.bankId,
                            )?.name ?? ""
                        }
                        canImport={canImport}
                        onClose={() => setPreviewSession(null)}
                    />
                )}
        </>
    )
}


/**
 * Remonta o estado da tela quando a empresa muda.
 * Isso descarta preview, seleção e mutations pendentes do escopo anterior.
 */
export function StatementsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <StatementsCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}
