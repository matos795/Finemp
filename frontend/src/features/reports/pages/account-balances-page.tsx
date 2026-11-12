import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactCurrency,
} from "../../../lib/formatters/decimal"
import { getBankAccountTypeLabel } from "../../bank-accounts/lib/bank-account-type-labels"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useAccountBalances } from "../hooks/use-account-balances"

const errors = {
    403: "Você não tem acesso aos saldos financeiros desta empresa.",
    404: "Contas e saldos não está disponível neste ambiente.",
}

export function AccountBalancesPage() {
    const { selectedCompany } = useCompanyAccess()
    const balances = useAccountBalances(selectedCompany?.id)

    return (
        <Panel
            title="Contas e saldos"
            help="O saldo atual considera o saldo inicial, baixas realizadas e transferências internas. Valores em aberto não alteram o saldo."
        >
            {balances.isError ? (
                <QueryError
                    error={balances.error}
                    messages={errors}
                    fallback="Não foi possível carregar os saldos das contas."
                    onRetry={() => balances.refetch()}
                />
            ) : (
                <div className="tblwrap">
                    <table aria-busy={balances.isFetching}>
                        <thead>
                            <tr>
                                <th>Conta</th>
                                <th>Tipo</th>
                                <th>Estado</th>
                                <th className="n">Saldo inicial</th>
                                <th className="n">Saldo atual</th>
                            </tr>
                        </thead>
                        <tbody>
                            {balances.isPending && (
                                <tr>
                                    <td colSpan={5} className="empty">
                                        Carregando…
                                    </td>
                                </tr>
                            )}

                            {balances.data?.accounts.map((account) => (
                                <tr key={account.id}>
                                    <td>{account.name}</td>
                                    <td>{getBankAccountTypeLabel(account.type)}</td>
                                    <td>
                                        <span className="badge b-neu">
                                            {account.active ? "Ativa" : "Inativa"}
                                        </span>
                                    </td>
                                    <td className="n">
                                        {formatExactCurrency(account.openingBalance)}
                                    </td>
                                    <td className="n">
                                        <b>{formatExactCurrency(account.currentBalance)}</b>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    {balances.data?.accounts.length === 0 && (
                        <div className="empty">
                            Nenhuma conta cadastrada.
                        </div>
                    )}
                </div>
            )}
        </Panel>
    )
}
