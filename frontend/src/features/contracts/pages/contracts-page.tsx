import { useEffect, useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { formatExactCurrency, formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    listBusinessPartnersPage,
} from "../../business-partners/api/business-partners-api"
import {
    defaultBusinessPartnerFilter,
} from "../../business-partners/types/business-partner.types"
import {
    createContract,
    listContracts,
    updateContract,
} from "../api/contracts-api"
import {
    defaultContractFilter,
    type Contract,
    type ContractDirection,
    type ContractFilter,
    type ContractValues,
} from "../types/contract.types"

type DialogState = { contract?: Contract } | null

const directionLabel: Record<ContractDirection, string> = {
    INCOME: "Receita",
    EXPENSE: "Despesa",
}

function canManageContract(
    hasPermission: (permission: import("../../auth/types/auth.types").CompanyPermission) => boolean,
) {
    return hasPermission("CONTRACT_MANAGE")
}

function normalizeMoney(value: string) {
    return value.trim().replace(",", ".")
}

function validMoney(value: string) {
    return /^\d+(?:[.,]\d{1,2})?$/.test(value.trim())
}

export function ContractsPage() {
    const { selectedCompany } = useCompanyAccess()
    return <ContractsCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function ContractsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = canManageContract(hasPermission)
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<ContractFilter>(defaultContractFilter)
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const contracts = useQuery({
        queryKey: ["contracts", companyId, filter, page] as const,
        queryFn: ({ signal }) => listContracts(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    useEffect(() => {
        if (searchText === filter.search) return
        const timer = window.setTimeout(
            () => setFilter((current) => ({ ...current, search: searchText })),
            350,
        )
        return () => window.clearTimeout(timer)
    }, [searchText, filter.search])

    useEffect(() => {
        const totalPages = contracts.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [contracts.data?.totalPages, page, setPage])

    const data = contracts.data
    const items = data?.items ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="contractStatus">Situação</label>
                    <select
                        id="contractStatus"
                        value={filter.active}
                        onChange={(event) =>
                            setFilter({
                                ...filter,
                                active: event.target.value as ContractFilter["active"],
                            })
                        }
                    >
                        <option value="true">Ativos</option>
                        <option value="false">Inativos</option>
                        <option value="">Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="contractSearch">Buscar</label>
                    <input
                        id="contractSearch"
                        type="search"
                        maxLength={100}
                        placeholder="Contrato ou parceiro…"
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
                            onClick={() => setDialog({})}
                        >
                            + Contrato
                        </button>
                    </div>
                ) : null}
            </div>

            {contracts.isError ? (
                <QueryError
                    error={contracts.error}
                    messages={{
                        400: "Confira os filtros.",
                        403: "Você não tem acesso aos contratos desta empresa.",
                        404: "Contratos não estão disponíveis neste ambiente.",
                    }}
                    fallback="Não foi possível carregar os contratos."
                    onRetry={() => contracts.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={contracts.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Parceiro</th>
                                    <th>Contrato</th>
                                    <th>Tipo</th>
                                    <th>Vigência</th>
                                    <th>Dia</th>
                                    <th className="n">Mensal</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((contract) => (
                                    <tr key={contract.id}>
                                        <td>{contract.businessPartnerName}</td>
                                        <td>{contract.description}</td>
                                        <td>
                                            <span className={`badge ${contract.direction === "INCOME" ? "b-ok" : "b-bad"}`}>
                                                {directionLabel[contract.direction]}
                                            </span>
                                        </td>
                                        <td>
                                            {formatIsoDate(contract.startDate)}
                                            {contract.endDate ? ` → ${formatIsoDate(contract.endDate)}` : ""}
                                        </td>
                                        <td>{contract.billingDay}</td>
                                        <td className="n">{formatExactCurrency(contract.monthlyAmount)}</td>
                                        <td>
                                            <span className={`badge ${contract.active ? "b-ok" : "b-neu"}`}>
                                                {contract.active ? "Ativo" : "Inativo"}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    onClick={() => setDialog({ contract })}
                                                >
                                                    Editar
                                                </button>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {contracts.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            Nenhum contrato encontrado.
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={data?.totalPages ?? 0}
                        disabled={contracts.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog && companyId ? (
                <ContractDialog
                    key={dialog.contract ? `${dialog.contract.id}-${dialog.contract.version}` : "new"}
                    companyId={companyId}
                    contract={dialog.contract}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({
                            queryKey: ["contracts", companyId],
                        })
                        showToast(dialog.contract ? "Contrato atualizado." : "Contrato cadastrado.")
                        setDialog(null)
                    }}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}

function ContractDialog({
    companyId,
    contract,
    onSaved,
    onClose,
}: {
    companyId: string
    contract?: Contract
    onSaved: () => Promise<void>
    onClose: () => void
}) {
    const [partnerSearch, setPartnerSearch] = useState("")
    const [businessPartnerId, setBusinessPartnerId] =
        useState(contract?.businessPartnerId ?? "")
    const [direction, setDirection] =
        useState<ContractDirection>(contract?.direction ?? "INCOME")
    const [description, setDescription] = useState(contract?.description ?? "")
    const [startDate, setStartDate] = useState(contract?.startDate ?? "")
    const [endDate, setEndDate] = useState(contract?.endDate ?? "")
    const [billingDay, setBillingDay] = useState(String(contract?.billingDay ?? 10))
    const [monthlyAmount, setMonthlyAmount] = useState(contract?.monthlyAmount ?? "")
    const [active, setActive] = useState(contract?.active ?? true)
    const [error, setError] = useState("")

    const partnerFilter = useMemo(
        () => ({
            ...defaultBusinessPartnerFilter,
            search: partnerSearch,
        }),
        [partnerSearch],
    )

    const partners = useQuery({
        queryKey: ["contract-partners", companyId, partnerFilter] as const,
        queryFn: ({ signal }) =>
            listBusinessPartnersPage(companyId, partnerFilter, 0, signal),
        retry: false,
    })

    const save = useMutation({
        mutationFn: async () => {
            setError("")

            const day = Number(billingDay)
            if (
                !businessPartnerId ||
                description.trim().length < 2 ||
                !startDate ||
                (endDate && endDate < startDate) ||
                !Number.isInteger(day) ||
                day < 1 ||
                day > 31 ||
                !validMoney(monthlyAmount)
            ) {
                throw new Error("VALIDATION")
            }

            const values: ContractValues = {
                businessPartnerId,
                direction,
                description: description.trim(),
                startDate,
                endDate: endDate || null,
                billingDay: day,
                monthlyAmount: normalizeMoney(monthlyAmount),
                active,
            }

            return contract
                ? updateContract(companyId, contract, values)
                : createContract(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: (caught) => {
            const status =
                typeof caught === "object" &&
                caught !== null &&
                "status" in caught
                    ? Number((caught as { status: unknown }).status)
                    : null

            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Confira parceiro, vigência, dia de cobrança e valor mensal.")
            } else if (status === 409) {
                setError("O contrato foi alterado por outro usuário. Feche e abra novamente.")
            } else if (status === 403) {
                setError("Seu acesso não permite alterar contratos.")
            } else {
                setError("Não foi possível salvar o contrato.")
            }
        },
    })

    const partnerItems = partners.data?.items ?? []
    const currentPartnerMissing =
        contract &&
        !partnerItems.some((partner) => partner.id === contract.businessPartnerId)

    return (
        <div className="modal" role="presentation">
            <div className="mbox" role="dialog" aria-modal="true" aria-labelledby="contractDialogTitle">
                <h2 id="contractDialogTitle">
                    {contract ? "Editar contrato" : "Novo contrato"}
                </h2>

                <div className="fg">
                    <div className="fl full">
                        <label htmlFor="contractPartnerSearch">Buscar parceiro</label>
                        <input
                            id="contractPartnerSearch"
                            type="search"
                            value={partnerSearch}
                            onChange={(event) => setPartnerSearch(event.target.value)}
                            placeholder="Nome do cliente/fornecedor…"
                        />
                    </div>

                    <div className="fl full">
                        <label htmlFor="contractPartner">Parceiro</label>
                        <select
                            id="contractPartner"
                            value={businessPartnerId}
                            onChange={(event) => setBusinessPartnerId(event.target.value)}
                        >
                            <option value="">Selecione</option>
                            {currentPartnerMissing ? (
                                <option value={contract.businessPartnerId}>
                                    {contract.businessPartnerName}
                                </option>
                            ) : null}
                            {partnerItems.map((partner) => (
                                <option value={partner.id} key={partner.id}>
                                    {partner.profile.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="fl">
                        <label htmlFor="contractDirection">Tipo</label>
                        <select
                            id="contractDirection"
                            value={direction}
                            onChange={(event) =>
                                setDirection(event.target.value as ContractDirection)
                            }
                        >
                            <option value="INCOME">Receita</option>
                            <option value="EXPENSE">Despesa</option>
                        </select>
                    </div>

                    <div className="fl">
                        <label htmlFor="contractAmount">Valor mensal</label>
                        <input
                            id="contractAmount"
                            type="text"
                            inputMode="decimal"
                            value={monthlyAmount}
                            onChange={(event) => setMonthlyAmount(event.target.value)}
                            placeholder="0,00"
                        />
                    </div>

                    <div className="fl full">
                        <label htmlFor="contractDescription">Descrição</label>
                        <input
                            id="contractDescription"
                            type="text"
                            maxLength={255}
                            value={description}
                            onChange={(event) => setDescription(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="contractStart">Início</label>
                        <input
                            id="contractStart"
                            type="date"
                            value={startDate}
                            onChange={(event) => setStartDate(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="contractEnd">Fim (opcional)</label>
                        <input
                            id="contractEnd"
                            type="date"
                            value={endDate}
                            onChange={(event) => setEndDate(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="contractBillingDay">Dia de cobrança</label>
                        <input
                            id="contractBillingDay"
                            type="number"
                            min="1"
                            max="31"
                            value={billingDay}
                            onChange={(event) => setBillingDay(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label className="chk">
                            <input
                                type="checkbox"
                                checked={active}
                                onChange={(event) => setActive(event.target.checked)}
                            />
                            Contrato ativo
                        </label>
                    </div>
                </div>

                {partners.isError ? (
                    <div className="alert e">
                        Não foi possível consultar os parceiros desta empresa.
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
