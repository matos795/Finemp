import { useQueries } from "@tanstack/react-query"
import { apiErrorCode, apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getAccountingDre, type AccountingDre } from "../api/accounting-data-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { lastDayOf, monthLabel } from "../lib/accounting-period"
import { buildDreMatrix, dreCoverageUntil, monthAfterImportedCoverage, trailingMonths } from "../lib/dre-matrix"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { accountingQueryKey } from "../lib/query-keys"
import { InfoButton } from "../components/accounting-info"
import { ScreenHeader, SoonPanel, type AccountingScreenPageProps } from "./accounting-screen-page"

const MONTHS = 7

type CellState = "ok" | "failed"

function Cell({ value, state }: { value: string | null; state: CellState }) {
    if (state === "failed") {
        return (
            <td className="n muted" title="A DRE deste mês não foi calculada">
                —
            </td>
        )
    }

    if (value === null) {
        return <td className="n muted">0,00</td>
    }

    return <td className={isNegativeDecimal(value) ? "n neg" : "n"}>{formatExactDecimal(value)}</td>
}

function monthError(error: unknown) {
    if (apiErrorStatus(error) === 422 && apiErrorCode(error) === "DRE_UNSUPPORTED_NEGATIVE_AMOUNT") {
        return "a base contém movimento com valor negativo; a regra da DRE para esse caso ainda está em decisão (#449)."
    }

    return "não foi possível calcular."
}

/** "DRE de pré-fechamento" com os totais do backend (#446), mês a mês. */
function DrePanel({ competence }: { competence: string }) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const months = trailingMonths(competence, MONTHS)
    const companyId = company?.companyId ?? null
    const canRead = companyGrant?.permissions.includes("DRE_VIEW") ?? false

    const results = useQueries({
        queries: months.map((month) => ({
            queryKey: accountingQueryKey(userId, "dre", companyId, month),
            queryFn: () => getAccountingDre(companyId as string, `${month}-01`, lastDayOf(month)),
            enabled: companyId !== null && canRead,
            retry: false,
        })),
    })

    let body

    if (!company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!canRead) {
        body = <div className="empty">Sem acesso à DRE desta empresa. Escolher a empresa não libera os dados dela.</div>
    } else if (results.some((result) => result.isLoading)) {
        body = <div className="empty">Carregando…</div>
    } else if (results.some((result) => apiErrorStatus(result.error) === 403)) {
        body = <div className="empty">Sem acesso à DRE desta empresa.</div>
    } else {
        const data: (AccountingDre | null)[] = results.map((result) => result.data ?? null)
        const coveredUntil = data.map(dreCoverageUntil).find((value) => value !== null) ?? null
        // Falha de cálculo nunca vira zero: a coluna fica "—" e o motivo aparece no aviso.
        // Totais calculados pelo backend sempre aparecem: a cobertura é factual e cumulativa,
        // mas o aviso simples abaixo olha apenas o maior fim conhecido; lacunas ficam no contrato.
        const states: CellState[] = months.map((_, index) => (results[index].isError ? "failed" : "ok"))
        const rows = buildDreMatrix(data)
        const current = data[MONTHS - 1]
        const errors = results
            .map((result, index) => (result.isError ? `${monthLabel(months[index])}: ${monthError(result.error)}` : null))
            .filter((message): message is string => message !== null)

        body = (
            <>
                {errors.length > 0 && (
                    <div className="warnbar" role="alert">
                        {errors.map((message) => (
                            <div key={message}>{message}</div>
                        ))}
                    </div>
                )}
                {monthAfterImportedCoverage(competence, coveredUntil) && (
                    <div className="warnbar" role="status">
                        A competência {monthLabel(competence)} é posterior ao fim da base contábil importada (até{" "}
                        {monthLabel(coveredUntil as string)}); os valores podem estar incompletos. Lançamentos preparados
                        ainda não existem no FINEMP.
                    </div>
                )}
                <div className="tbl-wrap" tabIndex={0} aria-label="DRE pré-fechamento">
                    <table className="dre gc">
                        <thead>
                            <tr>
                                <th>Linha da DRE</th>
                                {months.map((month, index) => (
                                    <th key={month} className="r">
                                        {index === MONTHS - 1 ? `${monthLabel(month)} (base)` : monthLabel(month)}
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map((row) => (
                                <tr
                                    key={row.key}
                                    className={row.kind === "group" ? "sub" : row.kind === "open" ? "ab" : "ab ab2"}
                                >
                                    <td title={row.label}>{row.label}</td>
                                    {row.values.map((value, index) => (
                                        <Cell key={months[index]} value={value} state={states[index]} />
                                    ))}
                                </tr>
                            ))}
                            {rows.length === 0 && (
                                <tr>
                                    <td colSpan={MONTHS + 1} className="empty">
                                        Nenhuma conta classificada com movimento no período.
                                    </td>
                                </tr>
                            )}
                            {rows.length > 0 && (
                                <tr className="rec">
                                    <td>Soma das linhas classificadas</td>
                                    {data.map((month, index) => (
                                        <Cell
                                            key={months[index]}
                                            value={month ? month.classifiedTotal : null}
                                            state={states[index]}
                                        />
                                    ))}
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                {current && current.unclassifiedAccountCount > 0 && (
                    <div className="warnbar" role="status" style={{ marginTop: 12, marginBottom: 0 }}>
                        {current.unclassifiedAccountCount} conta(s) com movimento em {monthLabel(competence)} ainda sem
                        classificação ficam fora da soma: classifique no De-Para do Plano de Contas.
                    </div>
                )}
            </>
        )
    }

    return (
        <div className="panel">
            <h3>
                DRE de pré-fechamento
                <InfoButton title="DRE de pré-fechamento">
                    <p className="info-p">
                        Totais calculados pelo FINEMP sobre a base contábil importada, mês a mês, por linha da DRE
                        das contas classificadas no De-Para. Nenhum valor é recalculado na tela.
                    </p>
                    <p className="info-p">
                        Média dos 6 meses, variação, alertas de linearidade, flags de pré-fechamento e lançamentos
                        preparados ainda não existem no FINEMP: Em breve.
                    </p>
                </InfoButton>
            </h3>
            {body}
        </div>
    )
}

/**
 * Pré-fechamento (`cfpre`): bloco de cabeçalho com a competência, "Alertas e
 * recomendações" e "Balancete" Em breve, e a DRE com os totais do backend.
 */
export function CfpreScreen(props: AccountingScreenPageProps) {
    const { screen, competence } = props

    return (
        <section id={`p-${screen.id}`} className="grid" style={{ gap: 18 }} data-handoff={screen.handoffSection}>
            <ScreenHeader {...props} />
            <SoonPanel title="Alertas e recomendações" />
            <DrePanel competence={competence} />
            <SoonPanel title="Balancete de pré-fechamento" />
        </section>
    )
}
