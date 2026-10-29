import { Panel } from "../../../components/finemp/panel"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useBankAccounts } from "../hooks/use-bank-accounts"
import { getBankAccountTypeLabel } from "../lib/bank-account-type-labels"

/** Tamanho padrão do endpoint no OpenAPI (1–100). */
const PAGE_SIZE = 50

const errorMessages = {
    403: "Você não tem acesso às contas bancárias desta empresa.",
    404: "O cadastro de contas bancárias não está disponível neste ambiente.",
}

/** Cadastros › Contas bancárias (cadContas do protótipo homologado). */
export function BankAccountsPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id

    const [page, setPage] =
        useCompanyPage(companyId)

    const bankAccounts =
        useBankAccounts(companyId, {
            page,
            size: PAGE_SIZE,
        })

    const data = bankAccounts.data

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
                            </tr>
                        </thead>

                        <tbody>
                            {bankAccounts.isPending && (
                                <tr>
                                    <td
                                        colSpan={5}
                                        className="empty"
                                    >
                                        Carregando…
                                    </td>
                                </tr>
                            )}

                            {data?.items.map((account) => (
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
                            ))}
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
        </Panel>
    )
}
