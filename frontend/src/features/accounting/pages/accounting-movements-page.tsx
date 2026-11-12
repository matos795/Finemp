import { useState } from "react"
import type { FormEvent } from "react"
import { useQuery } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { formatAmountCell, formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { listAccountingMovements } from "../api/accounting-api"

function currentMonthRange() {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const paddedMonth = String(month).padStart(2, "0")
    const lastDay = new Date(year, month, 0).getDate()

    return {
        start: `${year}-${paddedMonth}-01`,
        end: `${year}-${paddedMonth}-${String(lastDay).padStart(2, "0")}`,
    }
}

function describeError(error: unknown) {
    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para consultar movimentos contábeis."
    }

    return "Não foi possível carregar os movimentos contábeis."
}

function AccountingMovementsForCompany({ companyId }: { companyId: string }) {
    const initial = currentMonthRange()
    const [startInput, setStartInput] = useState(initial.start)
    const [endInput, setEndInput] = useState(initial.end)
    const [period, setPeriod] = useState(initial)

    const movementsQuery = useQuery({
        queryKey: [
            "accounting-movements",
            companyId,
            period.start,
            period.end,
        ] as const,
        queryFn: () =>
            listAccountingMovements(
                companyId,
                period.start,
                period.end,
            ),
        retry: false,
    })

    function applyPeriod(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!startInput || !endInput || startInput > endInput) return

        setPeriod({
            start: startInput,
            end: endInput,
        })
    }

    return (
        <div>
            <section className="mbox">
                <h1>Contabilidade</h1>
                <p className="help">
                    Movimentos contábeis recebidos da fonte externa. Esta tela é
                    somente leitura e não altera Questor/Firebird. Lançamentos
                    financeiros do FINEMP continuam em Fluxo de caixa →
                    Lançamentos.
                </p>

                <form
                    onSubmit={applyPeriod}
                    style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "end" }}
                >
                    <div className="fl">
                        <label htmlFor="accounting-start-date">Início</label>
                        <input
                            id="accounting-start-date"
                            type="date"
                            value={startInput}
                            onChange={(event) => setStartInput(event.target.value)}
                        />
                    </div>
                    <div className="fl">
                        <label htmlFor="accounting-end-date">Fim</label>
                        <input
                            id="accounting-end-date"
                            type="date"
                            value={endInput}
                            onChange={(event) => setEndInput(event.target.value)}
                        />
                    </div>
                    <button
                        className="btn"
                        type="submit"
                        disabled={!startInput || !endInput || startInput > endInput}
                    >
                        Aplicar período
                    </button>
                </form>

                {movementsQuery.error && (
                    <div className="alert e">
                        {describeError(movementsQuery.error)}
                    </div>
                )}
            </section>

            <section className="mbox">
                <h2>Movimentos</h2>
                {movementsQuery.isLoading && <p>Carregando...</p>}
                {!movementsQuery.isLoading &&
                    !movementsQuery.error &&
                    movementsQuery.data?.length === 0 && (
                        <p className="help">
                            Nenhum movimento contábil encontrado no período.
                        </p>
                    )}

                <div style={{ display: "grid", gap: 8 }}>
                    {movementsQuery.data?.map((movement) => (
                        <article className="card" key={movement.id}>
                            <strong>
                                {formatIsoDate(movement.accountingDate)} ·{" "}
                                {movement.accountKey} — {movement.accountDescription}
                            </strong>
                            <div>
                                {movement.side === "DEBIT" ? "Débito" : "Crédito"} ·{" "}
                                {formatAmountCell(movement.amount)}
                            </div>
                            <small>{movement.history || "Sem histórico"}</small>
                        </article>
                    ))}
                </div>
            </section>
        </div>
    )
}

export function AccountingMovementsPage() {
    const { selectedCompany } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <div className="mbox">
                Selecione uma empresa para consultar movimentos contábeis.
            </div>
        )
    }

    return (
        <AccountingMovementsForCompany
            key={selectedCompany.id}
            companyId={selectedCompany.id}
        />
    )
}
