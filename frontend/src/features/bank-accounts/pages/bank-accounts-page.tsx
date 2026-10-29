import {
    useEffect,
    useState,
} from "react"
import { Banknote } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"
import { cn } from "cn"
import { ApiError } from "../../../lib/api/api-client"
import {
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useBankAccounts } from "../hooks/use-bank-accounts"
import { getBankAccountTypeLabel } from "../lib/bank-account-type-labels"

/** Tamanho padrão do endpoint no OpenAPI (1–100). */
const PAGE_SIZE = 50

// Tokens do painel de Cadastros › Contas bancárias do protótipo homologado.
const panelClass =
    "min-w-0 rounded-xl border border-[#1F3F9A] bg-[#011C69] p-4 text-white"
const headCellClass =
    "whitespace-nowrap border-b border-[#F0B323] bg-[#011C69] px-2 py-2 text-left text-xs font-bold uppercase tracking-[0.5px] text-[#F0B323]"
const bodyCellClass =
    "border-b border-white/[0.07] px-2 py-[7px] align-middle"
const pagerButtonClass =
    "rounded-md border border-[#1F3F9A] bg-transparent px-2.5 py-1 text-xs font-bold text-white hover:border-[#F0B323] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[#1F3F9A]"

function describeError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 403) {
            return "Você não tem acesso às contas bancárias desta empresa."
        }

        if (error.status === 404) {
            return "O cadastro de contas bancárias não está disponível neste ambiente."
        }
    }

    return "Não foi possível carregar as contas bancárias."
}

export function BankAccountsPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id

    const queryClient = useQueryClient()

    // A página volta para a primeira ao trocar de empresa.
    const [pageState, setPageState] = useState({
        companyId,
        page: 0,
    })

    const page =
        pageState.companyId === companyId
            ? pageState.page
            : 0

    function goToPage(nextPage: number) {
        setPageState({
            companyId,
            page: nextPage,
        })
    }

    const bankAccounts =
        useBankAccounts(companyId, {
            page,
            size: PAGE_SIZE,
        })

    const sessionExpired =
        bankAccounts.error instanceof ApiError &&
        bankAccounts.error.status === 401

    useEffect(() => {
        // Sessão expirada: volta ao fluxo de login existente.
        if (sessionExpired) {
            queryClient.setQueryData(
                currentUserQueryKey,
                null,
            )
        }
    }, [sessionExpired, queryClient])

    const data = bankAccounts.data

    return (
        <div className="space-y-6">
            <div>
                <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[#b08d49]">
                    <Banknote className="size-4" />
                    Cadastros
                </div>

                <h1 className="text-3xl font-semibold tracking-tight text-[#102238]">
                    Contas bancárias
                </h1>
            </div>

            <section className={panelClass}>
                <h2 className="mb-2.5 text-xl font-semibold text-[#F0B323]">
                    Contas bancárias e caixa
                </h2>

                <p className="text-[13px] leading-normal text-[#AFC0EA]">
                    O saldo inicial é o saldo da conta na data de início do
                    controle. Lance só os movimentos a partir dessa data.
                </p>

                {bankAccounts.isError && !sessionExpired && (
                    <div
                        role="alert"
                        className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-[10px] border border-[#FF6B6B]/50 bg-[#FF6B6B]/[0.12] px-3.5 py-2.5 text-sm"
                    >
                        <span>
                            {describeError(bankAccounts.error)}
                        </span>

                        <button
                            type="button"
                            className={pagerButtonClass}
                            onClick={() => bankAccounts.refetch()}
                        >
                            Tentar novamente
                        </button>
                    </div>
                )}

                {!bankAccounts.isError && (
                    <div className="mt-3 max-w-full overflow-auto">
                        <table
                            className={cn(
                                "w-full border-collapse text-sm",
                                bankAccounts.isPlaceholderData &&
                                    "opacity-60",
                            )}
                            aria-busy={bankAccounts.isFetching}
                        >
                            <thead>
                                <tr>
                                    <th className={headCellClass}>
                                        Nome
                                    </th>
                                    <th className={headCellClass}>
                                        Tipo
                                    </th>
                                    <th
                                        className={cn(
                                            headCellClass,
                                            "text-right font-mono",
                                        )}
                                    >
                                        Saldo inicial
                                    </th>
                                    <th className={headCellClass}>
                                        Início
                                    </th>
                                    <th className={headCellClass}>
                                        Ativa
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                {bankAccounts.isPending &&
                                    Array.from({ length: 3 }, (_, index) => (
                                        <tr key={index}>
                                            <td
                                                colSpan={5}
                                                className={bodyCellClass}
                                            >
                                                <div className="h-4 w-full animate-pulse rounded bg-white/10" />
                                            </td>
                                        </tr>
                                    ))}

                                {data?.items.map((account) => (
                                    <tr key={account.id}>
                                        <td className={bodyCellClass}>
                                            {account.name}
                                        </td>
                                        <td className={bodyCellClass}>
                                            {getBankAccountTypeLabel(
                                                account.type,
                                            )}
                                        </td>
                                        <td
                                            className={cn(
                                                bodyCellClass,
                                                "whitespace-nowrap text-right font-mono",
                                            )}
                                        >
                                            {formatExactDecimal(
                                                account.openingBalance,
                                            )}
                                        </td>
                                        <td
                                            className={cn(
                                                bodyCellClass,
                                                "whitespace-nowrap",
                                            )}
                                        >
                                            {formatIsoDate(
                                                account.openingDate,
                                            )}
                                        </td>
                                        <td className={bodyCellClass}>
                                            <input
                                                type="checkbox"
                                                checked={account.active}
                                                readOnly
                                                disabled
                                                aria-label="Ativa"
                                            />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>

                        {data && data.items.length === 0 && (
                            <p className="p-6 text-center text-[#AFC0EA]">
                                Nenhuma conta cadastrada.
                            </p>
                        )}
                    </div>
                )}

                {data && data.totalPages > 1 && !bankAccounts.isError && (
                    <div className="mt-3.5 flex flex-wrap items-center justify-end gap-2 text-xs text-[#AFC0EA]">
                        <span>
                            Página {data.page + 1} de {data.totalPages}
                        </span>

                        <button
                            type="button"
                            className={pagerButtonClass}
                            disabled={page === 0 || bankAccounts.isFetching}
                            onClick={() => goToPage(page - 1)}
                        >
                            Anterior
                        </button>

                        <button
                            type="button"
                            className={pagerButtonClass}
                            disabled={
                                page + 1 >= data.totalPages ||
                                bankAccounts.isFetching
                            }
                            onClick={() => goToPage(page + 1)}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>
        </div>
    )
}
