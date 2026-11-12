import { useState } from "react"
import {
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    closeFinancialPeriod,
    listFinancialPeriodHistory,
    listFinancialPeriods,
    reopenFinancialPeriod,
} from "../api/financial-period-api"

const PAGE_SIZE = 20

function errorMessage(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
        return "O período mudou desde a última leitura. Atualize e tente novamente."
    }

    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para esta operação de período."
    }

    return "Não foi possível concluir a operação de período."
}

function FinancialPeriodsForCompany({
    companyId,
    canClose,
    canReopen,
}: {
    companyId: string
    canClose: boolean
    canReopen: boolean
}) {
    const queryClient = useQueryClient()
    const [page, setPage] = useState(0)
    const [period, setPeriod] = useState("")
    const [selectedPeriod, setSelectedPeriod] =
        useState("")
    const [reason, setReason] = useState("")
    const [mutationBusy, setMutationBusy] =
        useState(false)
    const [mutationError, setMutationError] =
        useState("")
    const [message, setMessage] = useState("")

    const periodsQuery = useQuery({
        queryKey: [
            "financial-periods",
            companyId,
            page,
        ] as const,
        queryFn: () =>
            listFinancialPeriods(
                companyId,
                page,
                PAGE_SIZE,
            ),
        retry: false,
    })

    const result = periodsQuery.data
    const selected =
        result?.items.find(
            (item) => item.period === selectedPeriod,
        ) ??
        result?.items[0] ??
        null

    const historyQuery = useQuery({
        queryKey: [
            "financial-period-history",
            companyId,
            selected?.period ?? "",
        ] as const,
        queryFn: () =>
            listFinancialPeriodHistory(
                companyId,
                selected?.period ?? "",
                0,
                20,
            ),
        enabled: Boolean(selected?.period),
        retry: false,
    })

    const queryError = periodsQuery.error
        ? errorMessage(periodsQuery.error)
        : ""
    const busy =
        periodsQuery.isFetching || mutationBusy

    async function close() {
        if (!period || !canClose) return

        const current =
            result?.items.find(
                (item) => item.period === period,
            ) ?? null

        setMutationBusy(true)
        setMutationError("")
        setMessage("")

        try {
            const receipt =
                await closeFinancialPeriod(
                    companyId,
                    period,
                    current?.version ?? null,
                )

            setMessage(
                `Período ${receipt.period} fechado.`,
            )
            setSelectedPeriod(receipt.period)

            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: [
                        "financial-periods",
                        companyId,
                    ],
                }),
                queryClient.invalidateQueries({
                    queryKey: [
                        "financial-period-history",
                        companyId,
                        receipt.period,
                    ],
                }),
            ])
        } catch (caught) {
            setMutationError(errorMessage(caught))
        } finally {
            setMutationBusy(false)
        }
    }

    async function reopen() {
        if (
            !selected ||
            selected.version === null ||
            !selected.closed ||
            !canReopen ||
            !reason.trim()
        ) {
            return
        }

        setMutationBusy(true)
        setMutationError("")
        setMessage("")

        try {
            const receipt =
                await reopenFinancialPeriod(
                    companyId,
                    selected.period,
                    selected.version,
                    reason,
                )

            setMessage(
                `Período ${receipt.period} reaberto.`,
            )
            setReason("")

            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: [
                        "financial-periods",
                        companyId,
                    ],
                }),
                queryClient.invalidateQueries({
                    queryKey: [
                        "financial-period-history",
                        companyId,
                        receipt.period,
                    ],
                }),
            ])
        } catch (caught) {
            setMutationError(errorMessage(caught))
        } finally {
            setMutationBusy(false)
        }
    }

    return (
        <div>
            <section className="mbox">
                <h1>Fechamento de períodos</h1>
                <p className="help">
                    Fechamento e reabertura são comandos
                    financeiros formais. Versão, idempotência e
                    invariantes são validadas pelo backend.
                </p>

                {message && (
                    <div className="alert i">{message}</div>
                )}
                {(queryError || mutationError) && (
                    <div className="alert e">
                        {mutationError || queryError}
                    </div>
                )}
            </section>

            {canClose && (
                <section className="mbox">
                    <h2>Fechar competência</h2>
                    <div className="fl">
                        <label htmlFor="financial-period">
                            Competência
                        </label>
                        <input
                            id="financial-period"
                            type="month"
                            value={period}
                            onChange={(event) =>
                                setPeriod(
                                    event.target.value,
                                )
                            }
                        />
                    </div>
                    <button
                        className="btn"
                        type="button"
                        disabled={busy || !period}
                        onClick={() => void close()}
                    >
                        Fechar período
                    </button>
                </section>
            )}

            <section className="mbox">
                <h2>Períodos registrados</h2>

                {periodsQuery.isLoading && (
                    <p>Carregando...</p>
                )}

                {!periodsQuery.isLoading &&
                    result?.items.length === 0 && (
                        <p className="help">
                            Nenhum fechamento foi registrado.
                            Um período aberto só passa a ter
                            estado persistido quando participa de
                            um comando formal.
                        </p>
                    )}

                <div style={{ display: "grid", gap: 8 }}>
                    {result?.items.map((item) => (
                        <button
                            key={item.period}
                            className="card"
                            type="button"
                            aria-pressed={
                                selected?.period ===
                                item.period
                            }
                            onClick={() => {
                                setSelectedPeriod(
                                    item.period,
                                )
                                setReason("")
                            }}
                            style={{
                                textAlign: "left",
                                width: "100%",
                                cursor: "pointer",
                            }}
                        >
                            <strong>{item.period}</strong>
                            <div>
                                {item.closed
                                    ? "Fechado"
                                    : "Aberto"}
                            </div>
                            <small>
                                Versão {item.version ?? 0}
                            </small>
                        </button>
                    ))}
                </div>

                {result && result.totalPages > 1 && (
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            marginTop: 12,
                        }}
                    >
                        <button
                            className="btn sec"
                            type="button"
                            disabled={
                                busy || result.page === 0
                            }
                            onClick={() => {
                                setSelectedPeriod("")
                                setPage((current) =>
                                    Math.max(0, current - 1),
                                )
                            }}
                        >
                            Anterior
                        </button>

                        <small>
                            Página {result.page + 1} de{" "}
                            {result.totalPages}
                        </small>

                        <button
                            className="btn sec"
                            type="button"
                            disabled={
                                busy ||
                                result.page + 1 >=
                                    result.totalPages
                            }
                            onClick={() => {
                                setSelectedPeriod("")
                                setPage((current) =>
                                    current + 1,
                                )
                            }}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>

            {selected && (
                <section className="mbox">
                    <h2>
                        Competência {selected.period}
                    </h2>
                    <p>
                        Estado:{" "}
                        <strong>
                            {selected.closed
                                ? "Fechado"
                                : "Aberto"}
                        </strong>
                    </p>

                    {selected.closed && canReopen && (
                        <div>
                            <div className="fl">
                                <label htmlFor="reopen-reason">
                                    Motivo da reabertura
                                </label>
                                <textarea
                                    id="reopen-reason"
                                    value={reason}
                                    onChange={(event) =>
                                        setReason(
                                            event.target
                                                .value,
                                        )
                                    }
                                    rows={3}
                                />
                            </div>
                            <button
                                className="btn"
                                type="button"
                                disabled={
                                    busy ||
                                    !reason.trim()
                                }
                                onClick={() =>
                                    void reopen()
                                }
                            >
                                Reabrir período
                            </button>
                        </div>
                    )}

                    <h3>Histórico recente</h3>

                    {historyQuery.isLoading ? (
                        <p>Carregando histórico...</p>
                    ) : historyQuery.data?.items.length ? (
                        <div
                            style={{
                                display: "grid",
                                gap: 8,
                            }}
                        >
                            {historyQuery.data.items.map(
                                (event) => (
                                    <div
                                        className="card"
                                        key={event.id}
                                    >
                                        <strong>
                                            {event.action}
                                        </strong>
                                        <div>
                                            {new Date(
                                                event.occurredAt,
                                            ).toLocaleString(
                                                "pt-BR",
                                            )}
                                        </div>
                                        {event.reason && (
                                            <small>
                                                Motivo:{" "}
                                                {event.reason}
                                            </small>
                                        )}
                                    </div>
                                ),
                            )}
                        </div>
                    ) : (
                        <p className="help">
                            Nenhum evento disponível.
                        </p>
                    )}
                </section>
            )}
        </div>
    )
}

export function FinancialPeriodsPage() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <div className="mbox">
                Selecione uma empresa para consultar períodos
                financeiros.
            </div>
        )
    }

    return (
        <FinancialPeriodsForCompany
            key={selectedCompany.id}
            companyId={selectedCompany.id}
            canClose={hasPermission(
                "FINANCIAL_PERIOD_CLOSE",
            )}
            canReopen={hasPermission(
                "FINANCIAL_PERIOD_REOPEN",
            )}
        />
    )
}
