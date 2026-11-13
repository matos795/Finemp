import { useEffect, useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { ApiError } from "../../../lib/api/api-client"
import { formatExactCurrency } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    cancelServiceInvoice,
    createServiceInvoice,
    listServiceInvoicePartnerOptions,
    listServiceInvoices,
    markServiceInvoiceIssued,
    updateServiceInvoice,
} from "../api/service-invoices-api"
import type {
    ServiceInvoice,
    ServiceInvoiceDraftValues,
    ServiceInvoicePartnerOption,
    ServiceInvoiceStatus,
} from "../types/service-invoice.types"

const moneyPattern = /^\d+(?:[.,]\d{1,2})?$/
const ratePattern = /^\d+(?:[.,]\d{1,4})?$/

const statusLabel: Record<ServiceInvoiceStatus, string> = {
    DRAFT: "Rascunho",
    ISSUED: "Emitida",
    CANCELED: "Cancelada",
}

function statusBadge(status: ServiceInvoiceStatus) {
    if (status === "ISSUED") return "b-ok"
    if (status === "CANCELED") return "b-bad"
    return "b-warn"
}

export function ServiceInvoicesPage() {
    const { selectedCompany } = useCompanyAccess()
    return (
        <ServiceInvoicesCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function ServiceInvoicesCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = Boolean(companyId) && hasPermission("BILLING_MANAGE")
    const cache = useQueryClient()
    const toast = useToast()

    const [month, setMonth] = useState(todayIso().slice(0, 7))
    const [status, setStatus] = useState<ServiceInvoiceStatus | "">("")
    const [searchText, setSearchText] = useState("")
    const [search, setSearch] = useState("")
    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ month, status, search }),
    )
    const [edit, setEdit] = useState<ServiceInvoice | "new" | null>(null)
    const [issue, setIssue] = useState<ServiceInvoice | null>(null)
    const [cancel, setCancel] = useState<ServiceInvoice | null>(null)

    useEffect(() => {
        if (searchText === search) return
        const timer = window.setTimeout(() => {
            setSearch(searchText)
            setPage(0)
        }, 350)
        return () => window.clearTimeout(timer)
    }, [searchText, search, setPage])

    const invoices = useQuery({
        queryKey: ["service-invoices", companyId, month, status, search, page] as const,
        queryFn: ({ signal }) =>
            listServiceInvoices(companyId!, month, status, search, page, signal),
        enabled: Boolean(companyId && /^\d{4}-\d{2}$/.test(month)),
        placeholderData: (previous) => previous,
        retry: false,
    })

    useEffect(() => {
        const totalPages = invoices.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [invoices.data?.totalPages, page, setPage])

    const invalidate = async () => {
        await cache.invalidateQueries({
            queryKey: ["service-invoices", companyId],
        })
    }

    return (
        <>
            <div className="panel">
                <div className="ph">
                    <div>
                        <h2>Notas fiscais de serviço</h2>
                        <p className="help">
                            Preparação e registro. O FINEMP não emite NFS-e na prefeitura
                            nesta etapa; “Emitida” registra uma emissão feita externamente.
                        </p>
                    </div>
                </div>

                <div className="filters">
                    <div className="f">
                        <label htmlFor="invoiceMonth">Competência</label>
                        <input
                            id="invoiceMonth"
                            type="month"
                            value={month}
                            onChange={(event) => {
                                setMonth(event.target.value)
                                setPage(0)
                            }}
                        />
                    </div>

                    <div className="f">
                        <label htmlFor="invoiceStatus">Situação</label>
                        <select
                            id="invoiceStatus"
                            value={status}
                            onChange={(event) => {
                                setStatus(event.target.value as ServiceInvoiceStatus | "")
                                setPage(0)
                            }}
                        >
                            <option value="">Todas</option>
                            <option value="DRAFT">Rascunho</option>
                            <option value="ISSUED">Emitida</option>
                            <option value="CANCELED">Cancelada</option>
                        </select>
                    </div>

                    <div className="f">
                        <label htmlFor="invoiceSearch">Buscar</label>
                        <input
                            id="invoiceSearch"
                            type="search"
                            maxLength={100}
                            placeholder="Parceiro, serviço ou número…"
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
                                onClick={() => setEdit("new")}
                            >
                                + Preparar nota
                            </button>
                        </div>
                    ) : null}
                </div>
            </div>

            {invoices.isError ? (
                <QueryError
                    error={invoices.error}
                    messages={{
                        400: "Confira os filtros.",
                        403: "Você não tem acesso ao faturamento fiscal desta empresa.",
                    }}
                    fallback="Não foi possível carregar as notas fiscais."
                    onRetry={() => invoices.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={invoices.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Parceiro</th>
                                    <th>Serviço</th>
                                    <th className="n">Bruto</th>
                                    <th className="n">Retenções</th>
                                    <th className="n">Líquido</th>
                                    <th>Situação</th>
                                    <th>Nº nota</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {(invoices.data?.items ?? []).map((invoice) => (
                                    <tr key={invoice.id}>
                                        <td>{invoice.businessPartnerName}</td>
                                        <td>{invoice.serviceText}</td>
                                        <td className="n">
                                            {formatExactCurrency(invoice.grossAmount)}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(invoice.totalWithheld)}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(invoice.netAmount)}
                                        </td>
                                        <td>
                                            <span className={`badge ${statusBadge(invoice.status)}`}>
                                                {statusLabel[invoice.status]}
                                            </span>
                                        </td>
                                        <td>{invoice.invoiceNumber ?? "—"}</td>
                                        {canManage ? (
                                            <td>
                                                <div className="row-actions">
                                                    {invoice.status === "DRAFT" ? (
                                                        <>
                                                            <button
                                                                type="button"
                                                                className="btn sec"
                                                                onClick={() => setEdit(invoice)}
                                                            >
                                                                Editar
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="btn sec"
                                                                onClick={() => setIssue(invoice)}
                                                            >
                                                                Registrar emissão
                                                            </button>
                                                        </>
                                                    ) : null}
                                                    {invoice.status !== "CANCELED" ? (
                                                        <button
                                                            type="button"
                                                            className="btn sec"
                                                            onClick={() => setCancel(invoice)}
                                                        >
                                                            Cancelar
                                                        </button>
                                                    ) : null}
                                                </div>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {invoices.isPending && invoices.fetchStatus !== "idle" ? (
                        <div className="empty">Carregando…</div>
                    ) : (invoices.data?.items.length ?? 0) === 0 ? (
                        <div className="empty">
                            Nenhuma nota fiscal encontrada nesta competência.
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={invoices.data?.totalPages ?? 0}
                        disabled={invoices.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {edit && companyId ? (
                <InvoiceDraftDialog
                    companyId={companyId}
                    invoice={edit === "new" ? undefined : edit}
                    defaultMonth={month}
                    onClose={() => setEdit(null)}
                    onSaved={async () => {
                        setEdit(null)
                        await invalidate()
                        toast("Nota fiscal salva.")
                    }}
                    onConflict={invalidate}
                />
            ) : null}

            {issue && companyId ? (
                <IssueInvoiceDialog
                    companyId={companyId}
                    invoice={issue}
                    onClose={() => setIssue(null)}
                    onSaved={async () => {
                        setIssue(null)
                        await invalidate()
                        toast("Emissão registrada.")
                    }}
                />
            ) : null}

            {cancel && companyId ? (
                <CancelInvoiceDialog
                    companyId={companyId}
                    invoice={cancel}
                    onClose={() => setCancel(null)}
                    onSaved={async () => {
                        setCancel(null)
                        await invalidate()
                        toast("Nota fiscal cancelada.")
                    }}
                />
            ) : null}
        </>
    )
}

function InvoiceDraftDialog({
    companyId,
    invoice,
    defaultMonth,
    onClose,
    onSaved,
    onConflict,
}: {
    companyId: string
    invoice?: ServiceInvoice
    defaultMonth: string
    onClose: () => void
    onSaved: () => Promise<void>
    onConflict: () => Promise<void>
}) {
    const [partnerSearch, setPartnerSearch] = useState("")
    const [businessPartnerId, setBusinessPartnerId] =
        useState(invoice?.businessPartnerId ?? "")
    const [competence, setCompetence] =
        useState(invoice?.competence ?? defaultMonth)
    const [serviceText, setServiceText] =
        useState(invoice?.serviceText ?? "")
    const [grossAmount, setGrossAmount] =
        useState(invoice?.grossAmount ?? "")
    const [issRate, setIssRate] = useState(invoice?.issRate ?? "0")
    const [issWithheld, setIssWithheld] =
        useState(invoice?.issWithheld ?? false)
    const [irRate, setIrRate] = useState(invoice?.irRate ?? "0")
    const [pisRate, setPisRate] = useState(invoice?.pisRate ?? "0")
    const [cofinsRate, setCofinsRate] = useState(invoice?.cofinsRate ?? "0")
    const [csllRate, setCsllRate] = useState(invoice?.csllRate ?? "0")
    const [inssRate, setInssRate] = useState(invoice?.inssRate ?? "0")
    const [error, setError] = useState("")

    const partners = useQuery({
        queryKey: ["service-invoice-partners", companyId, partnerSearch] as const,
        queryFn: ({ signal }) =>
            listServiceInvoicePartnerOptions(companyId, partnerSearch, signal),
        retry: false,
    })

    const options = useMemo(() => {
        const current = partners.data?.items ?? []
        if (
            invoice &&
            !current.some((partner) => partner.id === invoice.businessPartnerId)
        ) {
            return [
                {
                    id: invoice.businessPartnerId,
                    name: invoice.businessPartnerName,
                } satisfies ServiceInvoicePartnerOption,
                ...current,
            ]
        }
        return current
    }, [invoice, partners.data?.items])

    const save = useMutation({
        mutationFn: async () => {
            setError("")

            const rates = [issRate, irRate, pisRate, cofinsRate, csllRate, inssRate]
            if (
                !businessPartnerId ||
                !/^\d{4}-\d{2}$/.test(competence) ||
                serviceText.trim().length < 2 ||
                !moneyPattern.test(grossAmount.trim()) ||
                rates.some((rate) => !ratePattern.test(rate.trim()))
            ) {
                throw new Error("Confira parceiro, competência, serviço, valor e alíquotas.")
            }

            const values: ServiceInvoiceDraftValues = {
                businessPartnerId,
                contractId: invoice?.contractId ?? null,
                competence,
                serviceText: serviceText.trim(),
                grossAmount: grossAmount.trim().replace(",", "."),
                issRate: issRate.trim().replace(",", "."),
                issWithheld,
                irRate: irRate.trim().replace(",", "."),
                pisRate: pisRate.trim().replace(",", "."),
                cofinsRate: cofinsRate.trim().replace(",", "."),
                csllRate: csllRate.trim().replace(",", "."),
                inssRate: inssRate.trim().replace(",", "."),
            }

            return invoice
                ? updateServiceInvoice(companyId, invoice, values)
                : createServiceInvoice(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: async (caught) => {
            if (caught instanceof ApiError && caught.status === 409) {
                await onConflict()
                setError("Este rascunho foi alterado. A lista foi atualizada; feche e abra novamente.")
                return
            }
            setError(caught instanceof Error ? caught.message : "Não foi possível salvar.")
        },
    })

    return (
        <Modal onClose={save.isPending ? undefined : onClose}>
                <div className="mh">
                    <h2>{invoice ? "Editar nota fiscal" : "Preparar nota fiscal"}</h2>
                    <button type="button" className="x" disabled={save.isPending} onClick={onClose}>×</button>
                </div>

                <p className="help">
                    Os valores líquido e de retenções são calculados somente no backend.
                </p>

                <div className="fl">
                    <label htmlFor="invoicePartnerSearch">Buscar parceiro</label>
                    <input
                        id="invoicePartnerSearch"
                        type="search"
                        value={partnerSearch}
                        onChange={(event) => setPartnerSearch(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="invoicePartner">Parceiro</label>
                    <select
                        id="invoicePartner"
                        value={businessPartnerId}
                        onChange={(event) => setBusinessPartnerId(event.target.value)}
                    >
                        <option value="">Selecione</option>
                        {options.map((partner) => (
                            <option key={partner.id} value={partner.id}>
                                {partner.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="grid2">
                    <div className="fl">
                        <label htmlFor="invoiceCompetence">Competência</label>
                        <input
                            id="invoiceCompetence"
                            type="month"
                            value={competence}
                            onChange={(event) => setCompetence(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="invoiceGross">Valor bruto</label>
                        <input
                            id="invoiceGross"
                            inputMode="decimal"
                            value={grossAmount}
                            onChange={(event) => setGrossAmount(event.target.value)}
                        />
                    </div>
                </div>

                <div className="fl">
                    <label htmlFor="invoiceServiceText">Descrição do serviço</label>
                    <textarea
                        id="invoiceServiceText"
                        maxLength={4000}
                        value={serviceText}
                        onChange={(event) => setServiceText(event.target.value)}
                    />
                </div>

                <div className="grid3">
                    <RateField label="ISS %" value={issRate} onChange={setIssRate} />
                    <RateField label="IR %" value={irRate} onChange={setIrRate} />
                    <RateField label="PIS %" value={pisRate} onChange={setPisRate} />
                    <RateField label="COFINS %" value={cofinsRate} onChange={setCofinsRate} />
                    <RateField label="CSLL %" value={csllRate} onChange={setCsllRate} />
                    <RateField label="INSS %" value={inssRate} onChange={setInssRate} />
                </div>

                <div className="fl">
                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={issWithheld}
                            onChange={(event) => setIssWithheld(event.target.checked)}
                        />
                        ISS retido
                    </label>
                </div>

                {error ? <div className="alert e">{error}</div> : null}

                <div className="mact">
                    <button type="button" className="btn sec" onClick={onClose}>
                        Cancelar
                    </button>
                    <button
                        type="button"
                        className="btn"
                        disabled={save.isPending}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Salvando…" : "Salvar rascunho"}
                    </button>
                </div>
        </Modal>
    )
}

function RateField({
    label,
    value,
    onChange,
}: {
    label: string
    value: string
    onChange: (value: string) => void
}) {
    return (
        <div className="fl">
            <label>{label}</label>
            <input
                inputMode="decimal"
                value={value}
                onChange={(event) => onChange(event.target.value)}
            />
        </div>
    )
}

function IssueInvoiceDialog({
    companyId,
    invoice,
    onClose,
    onSaved,
}: {
    companyId: string
    invoice: ServiceInvoice
    onClose: () => void
    onSaved: () => Promise<void>
}) {
    const [number, setNumber] = useState("")
    const [error, setError] = useState("")

    const save = useMutation({
        mutationFn: () => markServiceInvoiceIssued(companyId, invoice, number.trim()),
        onSuccess: () => onSaved(),
        onError: (caught) => {
            setError(
                caught instanceof ApiError && caught.status === 409
                    ? "O registro mudou ou este número de nota já está em uso."
                    : "Não foi possível registrar a emissão.",
            )
        },
    })

    return (
        <Modal onClose={save.isPending ? undefined : onClose}>
                <div className="mh">
                    <h2>Registrar emissão externa</h2>
                    <button type="button" className="x" disabled={save.isPending} onClick={onClose}>×</button>
                </div>

                <div className="alert i">
                    O FINEMP não está emitindo a NFS-e. Informe o número da nota
                    emitida no portal fiscal externo.
                </div>

                <div className="fl">
                    <label htmlFor="invoiceNumber">Número da nota</label>
                    <input
                        id="invoiceNumber"
                        maxLength={80}
                        value={number}
                        onChange={(event) => setNumber(event.target.value)}
                    />
                </div>

                {error ? <div className="alert e">{error}</div> : null}

                <div className="mact">
                    <button type="button" className="btn sec" onClick={onClose}>
                        Voltar
                    </button>
                    <button
                        type="button"
                        className="btn"
                        disabled={save.isPending || !number.trim()}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Registrando…" : "Marcar como emitida"}
                    </button>
                </div>
        </Modal>
    )
}

function CancelInvoiceDialog({
    companyId,
    invoice,
    onClose,
    onSaved,
}: {
    companyId: string
    invoice: ServiceInvoice
    onClose: () => void
    onSaved: () => Promise<void>
}) {
    const [error, setError] = useState("")

    const save = useMutation({
        mutationFn: () => cancelServiceInvoice(companyId, invoice),
        onSuccess: () => onSaved(),
        onError: (caught) => {
            setError(
                caught instanceof ApiError && caught.status === 409
                    ? "O registro foi alterado. Atualize a tela antes de cancelar."
                    : "Não foi possível cancelar a nota fiscal.",
            )
        },
    })

    return (
        <Modal onClose={save.isPending ? undefined : onClose}>
                <div className="mh">
                    <h2>Cancelar registro</h2>
                    <button type="button" className="x" disabled={save.isPending} onClick={onClose}>×</button>
                </div>

                <p>
                    Cancelar <strong>{invoice.serviceText}</strong>?
                    {invoice.invoiceNumber
                        ? ` O número ${invoice.invoiceNumber} será preservado no histórico.`
                        : ""}
                </p>

                {error ? <div className="alert e">{error}</div> : null}

                <div className="mact">
                    <button type="button" className="btn sec" onClick={onClose}>
                        Voltar
                    </button>
                    <button
                        type="button"
                        className="btn"
                        disabled={save.isPending}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Cancelando…" : "Confirmar cancelamento"}
                    </button>
                </div>
        </Modal>
    )
}
