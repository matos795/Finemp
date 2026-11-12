import { useState } from "react"
import {
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    classifyAccountingAccount,
    listAccountingAccounts,
    listDreClassifications,
    type AccountingAccountClassification,
} from "../api/accounting-api"

function describeError(error: unknown) {
    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para consultar ou classificar o plano contábil."
    }

    return "Não foi possível carregar o plano de contas da contabilidade."
}

function AccountingAccountRow({
    companyId,
    account,
    canManage,
}: {
    companyId: string
    account: AccountingAccountClassification
    canManage: boolean
}) {
    const queryClient = useQueryClient()
    const [selectedClassificationId, setSelectedClassificationId] =
        useState(account.classificationId ?? "")
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState("")

    const classificationsQuery = useQuery({
        queryKey: ["dre-classifications"] as const,
        queryFn: listDreClassifications,
        enabled: canManage,
        staleTime: 5 * 60_000,
        retry: false,
    })

    async function save() {
        if (!selectedClassificationId || !canManage) return

        setBusy(true)
        setMessage("")

        try {
            await classifyAccountingAccount(
                companyId,
                account.accountId,
                selectedClassificationId,
            )
            setMessage("Classificação salva.")
            await queryClient.invalidateQueries({
                queryKey: ["accounting-accounts", companyId],
            })
        } catch (error) {
            setMessage(describeError(error))
        } finally {
            setBusy(false)
        }
    }

    return (
        <article className="card">
            <strong>{account.accountKey} — {account.description}</strong>
            <div>
                {account.status === "CLASSIFIED"
                    ? "Classificada"
                    : "Pendente de classificação"}
            </div>

            {account.classificationId && (
                <small>
                    DRE: {account.dreClassification ?? "—"} · DRE aberta:{" "}
                    {account.dreOpenClassification ?? "—"} · EBITDA:{" "}
                    {account.ebitdaClassification ?? "—"}
                </small>
            )}

            {canManage && (
                <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
                    <select
                        aria-label={`Classificação de ${account.accountKey}`}
                        value={selectedClassificationId}
                        onChange={(event) =>
                            setSelectedClassificationId(event.target.value)
                        }
                        disabled={busy || classificationsQuery.isLoading}
                    >
                        <option value="">Selecione uma classificação</option>
                        {classificationsQuery.data?.map((classification) => (
                            <option
                                key={classification.id}
                                value={classification.id}
                            >
                                {classification.dreClassification} ·{" "}
                                {classification.dreOpenClassification} ·{" "}
                                {classification.ebitdaClassification}
                            </option>
                        ))}
                    </select>
                    <button
                        className="btn"
                        type="button"
                        disabled={busy || !selectedClassificationId}
                        onClick={() => void save()}
                    >
                        Salvar classificação
                    </button>
                </div>
            )}

            {message && <small role="status">{message}</small>}
        </article>
    )
}

function AccountingAccountsForCompany({
    companyId,
    canManage,
}: {
    companyId: string
    canManage: boolean
}) {
    const [filter, setFilter] = useState("")

    const accountsQuery = useQuery({
        queryKey: ["accounting-accounts", companyId] as const,
        queryFn: () => listAccountingAccounts(companyId),
        retry: false,
    })

    const normalized = filter.trim().toLocaleLowerCase("pt-BR")
    const accounts =
        accountsQuery.data?.filter((account) =>
            !normalized ||
            account.accountKey.toLocaleLowerCase("pt-BR").includes(normalized) ||
            account.description.toLocaleLowerCase("pt-BR").includes(normalized)
        ) ?? []

    return (
        <div>
            <section className="mbox">
                <h1>Plano de contas da contabilidade</h1>
                <p className="help">
                    Estas contas vêm da fonte contábil externa. O FINEMP não cria
                    contas no Questor; apenas mantém a classificação gerencial
                    associada à conta importada/sincronizada.
                </p>

                <div className="fl">
                    <label htmlFor="accounting-account-filter">
                        Buscar por código ou descrição
                    </label>
                    <input
                        id="accounting-account-filter"
                        value={filter}
                        onChange={(event) => setFilter(event.target.value)}
                    />
                </div>

                {accountsQuery.error && (
                    <div className="alert e">
                        {describeError(accountsQuery.error)}
                    </div>
                )}
            </section>

            <section className="mbox">
                <h2>Contas contábeis</h2>
                {accountsQuery.isLoading && <p>Carregando...</p>}

                {!accountsQuery.isLoading &&
                    !accountsQuery.error &&
                    accountsQuery.data?.length === 0 && (
                        <p className="help">
                            Nenhuma conta contábil foi sincronizada para esta
                            empresa ainda.
                        </p>
                    )}

                <div style={{ display: "grid", gap: 8 }}>
                    {accounts.map((account) => (
                        <AccountingAccountRow
                            key={`${account.accountId}:${account.classificationId ?? "pending"}`}
                            companyId={companyId}
                            account={account}
                            canManage={canManage}
                        />
                    ))}
                </div>
            </section>
        </div>
    )
}

export function AccountingAccountsPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <div className="mbox">
                Selecione uma empresa para consultar o plano contábil.
            </div>
        )
    }

    return (
        <AccountingAccountsForCompany
            key={selectedCompany.id}
            companyId={selectedCompany.id}
            canManage={hasPermission("ACCOUNT_CLASSIFICATION_MANAGE")}
        />
    )
}
