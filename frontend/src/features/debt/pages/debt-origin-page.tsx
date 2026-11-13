import { useEffect, useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import {
    formatExactCurrency,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createDebt,
    getDebtSchedule,
    listDebtPartnerOptions,
    listDebts,
    updateDebt,
} from "../api/debts-api"
import type {
    DebtAgreement,
    DebtAgreementValues,
    DebtAmortizationSystem,
    DebtOrigin,
    DebtPartnerOption,
} from "../types/debt.types"

const moneyPattern = /^\d+(?:[.,]\d{1,2})?$/
const ratePattern = /^\d+(?:[.,]\d{1,24})?$/

const originConfig: Record<
    DebtOrigin,
    {
        title: string
        itemName: string
        partnerRequired: boolean
    }
> = {
    BANK: {
        title: "Dívidas bancárias",
        itemName: "dívida bancária",
        partnerRequired: true,
    },
    SUPPLIER: {
        title: "Dívidas com fornecedores",
        itemName: "dívida com fornecedor",
        partnerRequired: true,
    },
    TAX: {
        title: "Parcelamentos de impostos",
        itemName: "parcelamento de imposto",
        partnerRequired: false,
    },
    THIRD_PARTY: {
        title: "Mútuos e terceiros",
        itemName: "dívida com terceiro",
        partnerRequired: true,
    },
}

const systemLabel: Record<DebtAmortizationSystem, string> = {
    SAC: "SAC",
    FIXED: "Parcela fixa",
}

function formatExactRate(value: string) {
    const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value.trim())
    if (!match) return value

    const [, sign, integer, rawFraction = ""] = match
    const fraction = rawFraction.replace(/0+$/, "")
    return fraction
        ? `${sign}${integer},${fraction}%`
        : `${sign}${integer}%`
}

function normalizeDecimal(value: string) {
    return value.trim().replace(",", ".")
}

export function DebtOriginPage({ origin }: { origin: DebtOrigin }) {
    const { selectedCompany } = useCompanyAccess()
    return (
        <DebtOriginCompanyPage
            key={selectedCompany?.id ?? "no-company"}
            origin={origin}
        />
    )
}

function DebtOriginCompanyPage({ origin }: { origin: DebtOrigin }) {
    const config = originConfig[origin]
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = hasPermission("DEBT_MANAGE")
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [searchText, setSearchText] = useState("")
    const [search, setSearch] = useState("")
    const [dialog, setDialog] = useState<DebtAgreement | "new" | null>(null)
    const [scheduleDebt, setScheduleDebt] = useState<DebtAgreement | null>(null)
    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ origin, search }),
    )

    useEffect(() => {
        if (searchText === search) return
        const timer = window.setTimeout(() => {
            setSearch(searchText)
            setPage(0)
        }, 350)
        return () => window.clearTimeout(timer)
    }, [searchText, search, setPage])

    const debts = useQuery({
        queryKey: ["debts", companyId, origin, search, page] as const,
        queryFn: ({ signal }) =>
            listDebts(companyId!, origin, search, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    useEffect(() => {
        const totalPages = debts.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [debts.data?.totalPages, page, setPage])

    const items = debts.data?.items ?? []

    return (
        <>
            <div className="panel">
                <div className="ph">
                    <div>
                        <h2>{config.title}</h2>
                        <p className="help">
                            Acordos e cronogramas planejados. Pagamentos e quitação
                            serão integrados ao Financeiro em uma etapa posterior.
                        </p>
                    </div>
                </div>

                <div className="filters">
                    <div className="f">
                        <label htmlFor={`debtSearch-${origin}`}>Buscar</label>
                        <input
                            id={`debtSearch-${origin}`}
                            type="search"
                            maxLength={100}
                            placeholder="Descrição ou credor…"
                            value={searchText}
                            onChange={(event) => setSearchText(event.target.value)}
                        />
                    </div>

                    {canManage ? (
                        <div className="f">
                            <label>&nbsp;</label>
                            <button
                                type="button"
                                className="btn"
                                onClick={() => setDialog("new")}
                            >
                                + Nova dívida
                            </button>
                        </div>
                    ) : null}
                </div>
            </div>

            {debts.isError ? (
                <QueryError
                    error={debts.error}
                    messages={{
                        400: "Confira os filtros.",
                        403: "Você não tem acesso ao endividamento desta empresa.",
                    }}
                    fallback="Não foi possível carregar as dívidas."
                    onRetry={() => debts.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={debts.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Credor</th>
                                    <th>Descrição</th>
                                    <th>Sistema</th>
                                    <th className="n">Principal</th>
                                    <th className="n">Parcelas</th>
                                    <th className="n">Taxa efetiva a.m.</th>
                                    <th>1º vencimento</th>
                                    <th>Ações</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((debt) => (
                                    <tr key={debt.id}>
                                        <td>{debt.businessPartnerName ?? "—"}</td>
                                        <td>{debt.description}</td>
                                        <td>
                                            <span className="badge b-neu">
                                                {systemLabel[debt.amortizationSystem]}
                                            </span>
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(debt.principalAmount)}
                                        </td>
                                        <td className="n">{debt.installmentCount}</td>
                                        <td className="n">
                                            {formatExactRate(debt.effectiveMonthlyRate)}
                                        </td>
                                        <td>{formatIsoDate(debt.firstDueDate)}</td>
                                        <td>
                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    onClick={() => setScheduleDebt(debt)}
                                                >
                                                    Cronograma
                                                </button>
                                                {canManage ? (
                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        onClick={() => setDialog(debt)}
                                                    >
                                                        Editar
                                                    </button>
                                                ) : null}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {debts.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            Nenhuma dívida encontrada nesta origem.
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={debts.data?.totalPages ?? 0}
                        disabled={debts.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog && companyId ? (
                <DebtDialog
                    key={
                        dialog === "new"
                            ? `new-${origin}`
                            : `${dialog.id}-${dialog.version}`
                    }
                    companyId={companyId}
                    origin={origin}
                    debt={dialog === "new" ? undefined : dialog}
                    onClose={() => setDialog(null)}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({
                            queryKey: ["debts", companyId],
                        })
                        showToast(
                            dialog === "new"
                                ? "Dívida cadastrada."
                                : "Dívida atualizada.",
                        )
                        setDialog(null)
                    }}
                />
            ) : null}

            {scheduleDebt && companyId ? (
                <DebtScheduleDialog
                    companyId={companyId}
                    debt={scheduleDebt}
                    onClose={() => setScheduleDebt(null)}
                />
            ) : null}
        </>
    )
}

function DebtDialog({
    companyId,
    origin,
    debt,
    onClose,
    onSaved,
}: {
    companyId: string
    origin: DebtOrigin
    debt?: DebtAgreement
    onClose: () => void
    onSaved: () => Promise<void>
}) {
    const config = originConfig[origin]
    const queryClient = useQueryClient()

    const [partnerSearch, setPartnerSearch] = useState("")
    const [businessPartnerId, setBusinessPartnerId] =
        useState(debt?.businessPartnerId ?? "")
    const [selectedPartner, setSelectedPartner] = useState<DebtPartnerOption | null>(
        debt?.businessPartnerId && debt.businessPartnerName
            ? {
                  id: debt.businessPartnerId,
                  name: debt.businessPartnerName,
              }
            : null,
    )
    const [description, setDescription] = useState(debt?.description ?? "")
    const [system, setSystem] = useState<DebtAmortizationSystem>(
        debt?.amortizationSystem ?? "SAC",
    )
    const [principalAmount, setPrincipalAmount] =
        useState(debt?.principalAmount ?? "")
    const [installmentCount, setInstallmentCount] =
        useState(String(debt?.installmentCount ?? 12))
    const [ratePeriod, setRatePeriod] = useState<"MONTHLY" | "ANNUAL">(
        debt?.inputRatePeriod === "ANNUAL" ? "ANNUAL" : "MONTHLY",
    )
    const [inputRate, setInputRate] = useState(debt?.inputRate ?? "0")
    const [fixedInstallmentAmount, setFixedInstallmentAmount] =
        useState(debt?.fixedInstallmentAmount ?? "")
    const [firstDueDate, setFirstDueDate] =
        useState(debt?.firstDueDate ?? "")
    const [error, setError] = useState("")

    const partners = useQuery({
        queryKey: ["debt-partners", companyId, partnerSearch] as const,
        queryFn: ({ signal }) =>
            listDebtPartnerOptions(companyId, partnerSearch, signal),
        enabled: config.partnerRequired,
        retry: false,
    })

    const partnerItems = useMemo(() => {
        const current = partners.data?.items ?? []
        if (
            selectedPartner &&
            !current.some((partner) => partner.id === selectedPartner.id)
        ) {
            return [selectedPartner, ...current]
        }
        return current
    }, [partners.data?.items, selectedPartner])

    const save = useMutation({
        mutationFn: async () => {
            setError("")

            const count = Number(installmentCount)
            if (
                description.trim().length < 2 ||
                description.length > 255 ||
                !moneyPattern.test(principalAmount.trim()) ||
                !Number.isInteger(count) ||
                count < 1 ||
                count > 1200 ||
                !firstDueDate ||
                (config.partnerRequired && !businessPartnerId)
            ) {
                throw new Error("VALIDATION")
            }

            if (
                system === "SAC" &&
                !ratePattern.test(inputRate.trim())
            ) {
                throw new Error("VALIDATION")
            }

            if (
                system === "FIXED" &&
                !moneyPattern.test(fixedInstallmentAmount.trim())
            ) {
                throw new Error("VALIDATION")
            }

            const values: DebtAgreementValues = {
                businessPartnerId: config.partnerRequired
                    ? businessPartnerId
                    : null,
                origin,
                description: description.trim(),
                amortizationSystem: system,
                principalAmount: normalizeDecimal(principalAmount),
                installmentCount: count,
                inputRate:
                    system === "SAC"
                        ? normalizeDecimal(inputRate)
                        : null,
                inputRatePeriod:
                    system === "SAC"
                        ? ratePeriod
                        : "IMPLIED",
                fixedInstallmentAmount:
                    system === "FIXED"
                        ? normalizeDecimal(fixedInstallmentAmount)
                        : null,
                firstDueDate,
            }

            return debt
                ? updateDebt(companyId, debt, values)
                : createDebt(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: async (caught) => {
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError(
                    "Confira credor, descrição, principal, parcelas, taxa/parcela e vencimento.",
                )
                return
            }

            if (caught instanceof ApiError && caught.status === 409) {
                await queryClient.invalidateQueries({
                    queryKey: ["debts", companyId],
                })
                setError(
                    "Esta dívida foi alterada por outra operação. A lista foi atualizada; feche e abra novamente.",
                )
                return
            }

            if (caught instanceof ApiError && caught.status === 403) {
                setError("Seu acesso não permite alterar endividamento.")
                return
            }

            if (caught instanceof ApiError && caught.status === 400) {
                setError("Os parâmetros financeiros da dívida são inválidos.")
                return
            }

            setError("Não foi possível salvar a dívida.")
        },
    })

    return (
        <div className="modal" role="presentation">
            <div
                className="mbox"
                role="dialog"
                aria-modal="true"
                aria-labelledby="debtDialogTitle"
            >
                <div className="mh">
                    <h2 id="debtDialogTitle">
                        {debt ? "Editar" : "Nova"} {config.itemName}
                    </h2>
                    <button type="button" className="x" onClick={onClose}>×</button>
                </div>

                {config.partnerRequired ? (
                    <>
                        <div className="fl">
                            <label htmlFor="debtPartnerSearch">Buscar credor</label>
                            <input
                                id="debtPartnerSearch"
                                type="search"
                                value={partnerSearch}
                                onChange={(event) =>
                                    setPartnerSearch(event.target.value)
                                }
                            />
                        </div>

                        <div className="fl">
                            <label htmlFor="debtPartner">Credor</label>
                            <select
                                id="debtPartner"
                                value={businessPartnerId}
                                onChange={(event) => {
                                    const id = event.target.value
                                    setBusinessPartnerId(id)
                                    const partner = partnerItems.find(
                                        (candidate) => candidate.id === id,
                                    )
                                    setSelectedPartner(partner ?? null)
                                }}
                            >
                                <option value="">Selecione</option>
                                {partnerItems.map((partner) => (
                                    <option key={partner.id} value={partner.id}>
                                        {partner.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </>
                ) : (
                    <div className="alert i">
                        Parcelamentos de impostos não usam parceiro comercial
                        nesta etapa.
                    </div>
                )}

                <div className="fl">
                    <label htmlFor="debtDescription">Descrição</label>
                    <input
                        id="debtDescription"
                        maxLength={255}
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                    />
                </div>

                <div className="grid2">
                    <div className="fl">
                        <label htmlFor="debtPrincipal">Principal</label>
                        <input
                            id="debtPrincipal"
                            inputMode="decimal"
                            value={principalAmount}
                            onChange={(event) =>
                                setPrincipalAmount(event.target.value)
                            }
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="debtInstallments">Parcelas</label>
                        <input
                            id="debtInstallments"
                            type="number"
                            min="1"
                            max="1200"
                            value={installmentCount}
                            onChange={(event) =>
                                setInstallmentCount(event.target.value)
                            }
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="debtSystem">Sistema</label>
                        <select
                            id="debtSystem"
                            value={system}
                            onChange={(event) =>
                                setSystem(
                                    event.target.value as DebtAmortizationSystem,
                                )
                            }
                        >
                            <option value="SAC">SAC</option>
                            <option value="FIXED">Parcela fixa</option>
                        </select>
                    </div>

                    <div className="fl">
                        <label htmlFor="debtFirstDue">1º vencimento</label>
                        <input
                            id="debtFirstDue"
                            type="date"
                            value={firstDueDate}
                            onChange={(event) =>
                                setFirstDueDate(event.target.value)
                            }
                        />
                    </div>
                </div>

                {system === "SAC" ? (
                    <div className="grid2">
                        <div className="fl">
                            <label htmlFor="debtRatePeriod">Período da taxa</label>
                            <select
                                id="debtRatePeriod"
                                value={ratePeriod}
                                onChange={(event) =>
                                    setRatePeriod(
                                        event.target.value as
                                            | "MONTHLY"
                                            | "ANNUAL",
                                    )
                                }
                            >
                                <option value="MONTHLY">Mensal</option>
                                <option value="ANNUAL">Anual efetiva</option>
                            </select>
                        </div>

                        <div className="fl">
                            <label htmlFor="debtRate">Taxa (%)</label>
                            <input
                                id="debtRate"
                                inputMode="decimal"
                                value={inputRate}
                                onChange={(event) =>
                                    setInputRate(event.target.value)
                                }
                            />
                        </div>
                    </div>
                ) : (
                    <div className="fl">
                        <label htmlFor="debtFixedAmount">Valor da parcela fixa</label>
                        <input
                            id="debtFixedAmount"
                            inputMode="decimal"
                            value={fixedInstallmentAmount}
                            onChange={(event) =>
                                setFixedInstallmentAmount(event.target.value)
                            }
                        />
                        <p className="help">
                            A taxa mensal efetiva será inferida pelo backend.
                        </p>
                    </div>
                )}

                {debt ? (
                    <div className="alert i">
                        Taxa efetiva atual:{" "}
                        <strong>{formatExactRate(debt.effectiveMonthlyRate)}</strong>.
                        O cronograma será regenerado ao salvar enquanto não houver
                        integração de pagamentos.
                    </div>
                ) : null}

                {partners.isError ? (
                    <div className="alert e">
                        Não foi possível consultar os credores desta empresa.
                    </div>
                ) : null}

                {error ? <div className="alert e">{error}</div> : null}

                <div className="mact">
                    <button
                        type="button"
                        className="btn sec"
                        disabled={save.isPending}
                        onClick={onClose}
                    >
                        Cancelar
                    </button>
                    <button
                        type="button"
                        className="btn"
                        disabled={save.isPending}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Salvando…" : "Salvar"}
                    </button>
                </div>
            </div>
        </div>
    )
}

function DebtScheduleDialog({
    companyId,
    debt,
    onClose,
}: {
    companyId: string
    debt: DebtAgreement
    onClose: () => void
}) {
    const schedule = useQuery({
        queryKey: ["debt-schedule", companyId, debt.id, debt.version] as const,
        queryFn: ({ signal }) =>
            getDebtSchedule(companyId, debt.id, signal),
        retry: false,
    })

    return (
        <div className="modal" role="presentation">
            <div
                className="mbox"
                role="dialog"
                aria-modal="true"
                aria-labelledby="debtScheduleTitle"
            >
                <div className="mh">
                    <div>
                        <h2 id="debtScheduleTitle">Cronograma</h2>
                        <p className="help">{debt.description}</p>
                    </div>
                    <button type="button" className="x" onClick={onClose}>×</button>
                </div>

                {schedule.isError ? (
                    <QueryError
                        error={schedule.error}
                        messages={{
                            403: "Você não tem acesso ao cronograma desta dívida.",
                            404: "A dívida não foi encontrada.",
                        }}
                        fallback="Não foi possível carregar o cronograma."
                        onRetry={() => schedule.refetch()}
                    />
                ) : (
                    <div className="tblwrap">
                        <table>
                            <thead>
                                <tr>
                                    <th className="n">#</th>
                                    <th>Vencimento</th>
                                    <th className="n">Amortização</th>
                                    <th className="n">Juros</th>
                                    <th className="n">Parcela</th>
                                    <th className="n">Saldo</th>
                                </tr>
                            </thead>
                            <tbody>
                                {(schedule.data ?? []).map((installment) => (
                                    <tr key={installment.id}>
                                        <td className="n">
                                            {installment.installmentNumber}
                                        </td>
                                        <td>{formatIsoDate(installment.dueDate)}</td>
                                        <td className="n">
                                            {formatExactCurrency(
                                                installment.amortizationAmount,
                                            )}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(
                                                installment.interestAmount,
                                            )}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(
                                                installment.installmentAmount,
                                            )}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(
                                                installment.balanceAfter,
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {schedule.isPending ? (
                    <div className="empty">Carregando cronograma…</div>
                ) : null}

                <div className="mact">
                    <button type="button" className="btn sec" onClick={onClose}>
                        Fechar
                    </button>
                </div>
            </div>
        </div>
    )
}
