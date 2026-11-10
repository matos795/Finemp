import {
    useEffect,
    useState,
} from "react"
import { todayIso } from "../../../lib/formatters/local-date"
import type { BankAccount } from "../../bank-accounts/types/bank-account.types"
import { currentMonthRange } from "../lib/period-shortcuts"
import type {
    FinancialCategoryOption,
    ResultCenterOption,
} from "../types/financial-catalog.types"
import {
    WITHOUT_CENTER,
    emptyFinancialEntryFilter,
    type FinancialEntryFilter,
} from "../types/financial-entry.types"

const PENDING_TITLE = "Aguardando dado da API"

interface FinancialEntryFiltersProps {
    filter: FinancialEntryFilter
    onChange: (filter: FinancialEntryFilter) => void
    bankAccounts: BankAccount[]
    categories: FinancialCategoryOption[]
    resultCenters: ResultCenterOption[]
}

/**
 * Filtros do protótipo (renderLanc) que a API suporta. O servidor filtra;
 * a busca espera o usuário parar de digitar antes de consultar.
 */
export function FinancialEntryFilters({
    filter,
    onChange,
    bankAccounts,
    categories,
    resultCenters,
}: FinancialEntryFiltersProps) {
    const [searchText, setSearchText] = useState(filter.search)

    useEffect(() => {
        if (searchText === filter.search) {
            return
        }

        const timer = window.setTimeout(
            () => onChange({ ...filter, search: searchText }),
            350,
        )

        return () => window.clearTimeout(timer)
    }, [searchText, filter, onChange])

    function set(changes: Partial<FinancialEntryFilter>) {
        onChange({ ...filter, ...changes })
    }

    const groups = categories.filter((category) => category.kind === "GROUP")

    return (
        <div className="filters">
            <div className="f">
                <label htmlFor="fDe">De</label>
                <input
                    type="date"
                    id="fDe"
                    value={filter.from}
                    onChange={(event) => set({ from: event.target.value })}
                />
            </div>

            <div className="f">
                <label htmlFor="fAte">Até</label>
                <input
                    type="date"
                    id="fAte"
                    value={filter.to}
                    onChange={(event) => set({ to: event.target.value })}
                />
            </div>

            <div className="f">
                <label htmlFor="fTipo">Tipo</label>
                <select
                    id="fTipo"
                    value={filter.direction}
                    onChange={(event) => set({ direction: event.target.value as FinancialEntryFilter["direction"] })}
                >
                    <option value="">Todos</option>
                    <option value="INCOME">Entradas</option>
                    <option value="EXPENSE">Saídas</option>
                </select>
            </div>

            <div className="f">
                <label htmlFor="fStatus">Situação</label>
                <select
                    id="fStatus"
                    value={filter.status}
                    onChange={(event) => set({ status: event.target.value as FinancialEntryFilter["status"] })}
                >
                    <option value="">Todas</option>
                    <option value="PAID">Pago / recebido</option>
                    <option value="OPEN">Em aberto</option>
                    <option value="OVERDUE">Vencido</option>
                </select>
            </div>

            <div className="f">
                <label htmlFor="fConta">Conta</label>
                <select
                    id="fConta"
                    value={filter.bankId}
                    onChange={(event) => set({ bankId: event.target.value })}
                >
                    <option value="">Todas</option>
                    {bankAccounts.map((account) => (
                        <option
                            key={account.id}
                            value={account.id}
                        >
                            {account.name}
                        </option>
                    ))}
                </select>
            </div>

            <div className="f">
                <label htmlFor="fCat">Categoria</label>
                <select
                    id="fCat"
                    style={{ maxWidth: 260 }}
                    value={filter.categoryId}
                    onChange={(event) => set({ categoryId: event.target.value })}
                >
                    <option value="">Todas</option>
                    {groups.map((group) => [
                        <option
                            key={group.id}
                            value={group.id}
                        >
                            {group.code} {group.name} (grupo)
                        </option>,
                        ...categories
                            .filter((category) => category.parentId === group.id)
                            .map((category) => (
                                <option
                                    key={category.id}
                                    value={category.id}
                                >
                                    {"  "}{category.code} {category.name}
                                </option>
                            )),
                    ])}
                </select>
            </div>

            <div className="f">
                <label htmlFor="fCR">Centro de resultado</label>
                <select
                    id="fCR"
                    style={{ maxWidth: 260 }}
                    value={filter.center}
                    onChange={(event) => set({ center: event.target.value })}
                >
                    <option value="">Todos</option>
                    <option value={WITHOUT_CENTER}>Sem centro de resultado</option>
                    {resultCenters.map((center) => (
                        <option
                            key={center.id}
                            value={center.id}
                        >
                            {center.code} {center.name}
                        </option>
                    ))}
                </select>
            </div>

            <div className="f">
                <label htmlFor="fBusca">Buscar</label>
                <input
                    type="search"
                    id="fBusca"
                    placeholder="Descrição, cliente, NF…"
                    maxLength={100}
                    value={searchText}
                    onChange={(event) => setSearchText(event.target.value)}
                />
            </div>

            <div className="f">
                <label>&nbsp;</label>
                <div style={{ display: "flex", gap: 6 }}>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => set(currentMonthRange(todayIso()))}
                    >
                        Este mês
                    </button>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => {
                            setSearchText("")
                            onChange(emptyFinancialEntryFilter)
                        }}
                    >
                        Limpar
                    </button>
                </div>
            </div>

            <button
                type="button"
                className="btn sec sm"
                disabled
                title={PENDING_TITLE}
            >
                Exportar filtro (.xlsx)
            </button>
            <button
                type="button"
                className="btn sec sm"
                onClick={() => window.print()}
            >
                Imprimir / PDF
            </button>
        </div>
    )
}
