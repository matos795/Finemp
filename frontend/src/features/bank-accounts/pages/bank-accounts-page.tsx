import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { ConfirmDialog } from "../../../components/finemp/confirm-dialog"
import { useToast } from "../../../components/finemp/toast-context"
import {
    formatExactCurrency,
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import {
    apiErrorCode,
    apiErrorStatus,
} from "../../../lib/api/api-errors"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useBankAccounts } from "../hooks/use-bank-accounts"
import { useBankAccountMutations } from "../hooks/use-bank-account-mutations"
import { getBankAccountTypeLabel } from "../lib/bank-account-type-labels"
import { BankAccountEditRow } from "../components/bank-account-edit-row"
import type {
    BankAccount,
    BankAccountWrite,
} from "../types/bank-account.types"

/** Tamanho padrão do endpoint no OpenAPI (1–100). */
const PAGE_SIZE = 50

const errorMessages = {
    403: "Você não tem acesso às contas bancárias desta empresa.",
    404: "O cadastro de contas bancárias não está disponível neste ambiente.",
}

/** Padrões da nova conta no protótipo (addConta) e no contrato (#118). */
function newAccountValues(): BankAccountWrite {
    const today = new Date()
    const month = String(today.getMonth() + 1).padStart(2, "0")

    return {
        name: "Nova conta",
        type: "CURRENT",
        openingBalance: "0",
        openingDate: `${today.getFullYear()}-${month}-01`,
        active: true,
    }
}

function describeWriteError(error: unknown) {
    switch (apiErrorCode(error)) {
        case "STALE_VERSION":
            return "A conta foi alterada por outra pessoa. A lista foi atualizada."
        case "LAST_ACCOUNT":
            return "Mantenha pelo menos uma conta."
    }

    switch (apiErrorStatus(error)) {
        case 400:
            return "Dados inválidos. Confira os campos da conta."
        case 403:
            return "Você não tem permissão para alterar as contas bancárias desta empresa."
        case 404:
            return "A conta não foi encontrada. A lista foi atualizada."
        default:
            return "Não foi possível salvar. Atualize a página e tente novamente."
    }
}

/** Cadastros › Contas bancárias (cadContas do protótipo homologado). */
export function BankAccountsPage() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    const companyId = selectedCompany?.id

    // Esconder edição é só UX: o backend exige BANK_ACCOUNT_MANAGE ALL.
    const canManage = hasPermission("BANK_ACCOUNT_MANAGE")

    const [page, setPage] =
        useCompanyPage(companyId)

    const bankAccounts =
        useBankAccounts(companyId, {
            page,
            size: PAGE_SIZE,
        })

    const { create, update, remove } =
        useBankAccountMutations(companyId)

    const queryClient = useQueryClient()
    const showToast = useToast()

    const [accountToDelete, setAccountToDelete] =
        useState<BankAccount | null>(null)

    const [editResetVersionById, setEditResetVersionById] =
        useState<Record<string, number>>({})

    function handleWriteError(error: unknown) {
        if (apiErrorStatus(error) === 401) {
            queryClient.setQueryData(currentUserQueryKey, null)
            return
        }

        showToast(describeWriteError(error), true)
    }

    function handleChange(
        account: BankAccount,
        changes: Partial<BankAccountWrite>,
    ) {
        update.mutate(
            { account, changes },
            {
                onSuccess: (saved) => {
                    if (changes.openingBalance !== undefined) {
                        showToast(
                            `Saldo inicial de ${saved.name}: ${formatExactCurrency(saved.openingBalance)}`,
                        )
                    }
                },
                onError: (error) => {
                    setEditResetVersionById((current) => ({
                        ...current,
                        [account.id]: (current[account.id] ?? 0) + 1,
                    }))
                    handleWriteError(error)
                },
            },
        )
    }

    function confirmDelete() {
        if (!accountToDelete) {
            return
        }

        remove.mutate(accountToDelete, {
            onSuccess: (result) => {
                if (
                    result.action === "DELETED" &&
                    page > 0 &&
                    bankAccounts.data?.items.length === 1
                ) {
                    setPage(page - 1)
                }

                if (result.action === "DEACTIVATED") {
                    showToast("A conta tem movimentos: foi desativada (o histórico continua).")
                }
            },
            onError: handleWriteError,
            onSettled: () => setAccountToDelete(null),
        })
    }

    const data = bankAccounts.data

    const busyAccountId =
        update.isPending
            ? update.variables?.account.id
            : remove.isPending
                ? remove.variables?.id
                : undefined

    return (
        <Panel
            title="Contas bancárias e caixa"
            help="O saldo inicial é o saldo da conta na data de início do controle. Lance só os movimentos a partir dessa data."
        >
            {bankAccounts.isError ? (
                <QueryError
                    error={bankAccounts.error}
                    messages={errorMessages}
                    fallback="Não foi possível carregar as contas bancárias."
                    onRetry={() => bankAccounts.refetch()}
                />
            ) : (
                <div className="tblwrap">
                    <table
                        aria-busy={bankAccounts.isFetching}
                        style={
                            bankAccounts.isPlaceholderData
                                ? { opacity: 0.6 }
                                : undefined
                        }
                    >
                        <thead>
                            <tr>
                                <th>Nome</th>
                                <th>Tipo</th>
                                <th className="n">Saldo inicial</th>
                                <th>Início</th>
                                <th>Ativa</th>
                                {canManage && <th />}
                            </tr>
                        </thead>

                        <tbody>
                            {bankAccounts.isPending && (
                                <tr>
                                    <td
                                        colSpan={canManage ? 6 : 5}
                                        className="empty"
                                    >
                                        Carregando…
                                    </td>
                                </tr>
                            )}

                            {data?.items.map((account) =>
                                canManage ? (
                                    <BankAccountEditRow
                                        // A versão na chave recria os campos com o valor salvo.
                                        key={`${account.id}:${account.version}:${editResetVersionById[account.id] ?? 0}`}
                                        account={account}
                                        busy={busyAccountId === account.id}
                                        onChange={(changes) =>
                                            handleChange(account, changes)
                                        }
                                        onInvalid={(message) =>
                                            showToast(message, true)
                                        }
                                        onDelete={() =>
                                            setAccountToDelete(account)
                                        }
                                    />
                                ) : (
                                    <tr key={account.id}>
                                        <td>{account.name}</td>
                                        <td>
                                            {getBankAccountTypeLabel(
                                                account.type,
                                            )}
                                        </td>
                                        <td className="n">
                                            {formatExactDecimal(
                                                account.openingBalance,
                                            )}
                                        </td>
                                        <td>
                                            {formatIsoDate(
                                                account.openingDate,
                                            )}
                                        </td>
                                        <td>
                                            <input
                                                type="checkbox"
                                                checked={account.active}
                                                readOnly
                                                disabled
                                                aria-label="Ativa"
                                            />
                                        </td>
                                    </tr>
                                ),
                            )}
                        </tbody>
                    </table>

                    {data && data.items.length === 0 && (
                        <div className="empty">
                            Nenhuma conta cadastrada.
                        </div>
                    )}
                </div>
            )}

            {data && !bankAccounts.isError && (
                <Pager
                    page={page}
                    totalPages={data.totalPages}
                    disabled={bankAccounts.isFetching}
                    onChange={setPage}
                />
            )}

            {canManage && !bankAccounts.isError && (
                <div className="mact">
                    <button
                        type="button"
                        className="btn sec"
                        disabled={!data || create.isPending}
                        onClick={() =>
                            create.mutate(newAccountValues(), {
                                onError: handleWriteError,
                            })
                        }
                    >
                        + Conta
                    </button>
                </div>
            )}

            {accountToDelete && (
                <ConfirmDialog
                    title="Excluir conta"
                    message={`A conta "${accountToDelete.name}" será removida. Se tiver movimentos, ela será só desativada e o histórico continua.`}
                    confirmLabel="Excluir"
                    busy={remove.isPending}
                    onConfirm={confirmDelete}
                    onCancel={() => setAccountToDelete(null)}
                />
            )}
        </Panel>
    )
}
