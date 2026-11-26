import { useEffect, useMemo, useState, type ReactNode } from "react"
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { useAccountingToast } from "../accounting-toast"
import {
    classifyImportedAccount,
    listDreCatalog,
    searchImportedAccounts,
    type DreCatalogEntry,
    type ImportedAccount,
} from "../api/accounting-data-api"
import { useNavigate } from "react-router"
import { InfoButton } from "../components/accounting-info"
import { ChartUniverseNotice } from "../components/chart-universe-notice"
import { MovementDrilldownModal } from "../components/movement-drilldown-modal"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { useAccountingSources } from "../hooks/use-accounting-sources"
import { useChartMembership } from "../hooks/use-chart-membership"
import { INFERRED_ACCOUNT_KIND_NOTE, inferredAccountKind } from "../lib/inferred-account-kind"
import { findAccountingScreenById } from "../lib/accounting-screens"
import { accountingQueryKey } from "../lib/query-keys"
import { SoonPanel } from "./accounting-screen-page"

const PAGE_SIZE = 100

type Filter = "pend" | "todas"

/** Pílulas do protótipo (#dpFiltro). Só "Pendentes" e "Todas" têm contrato (#442). */
const FILTERS: { key: Filter | "novas" | "div" | "regra"; label: string; available: boolean }[] = [
    { key: "pend", label: "Pendentes", available: true },
    { key: "novas", label: "Contas novas", available: false },
    { key: "div", label: "Divergentes", available: false },
    { key: "regra", label: "Com regra", available: false },
    { key: "todas", label: "Todas as contas · alterar classificação", available: true },
]

function useDebounced(value: string, delay: number) {
    const [debounced, setDebounced] = useState(value)

    useEffect(() => {
        const timer = window.setTimeout(() => setDebounced(value), delay)

        return () => window.clearTimeout(timer)
    }, [value, delay])

    return debounced
}

function destination(account: ImportedAccount) {
    if (account.status !== "CLASSIFIED") {
        return <span className="muted">Sem classificação</span>
    }

    return (
        <>
            <span className="dp-dem res">DRE</span> {account.dreClassification} › {account.dreOpenClassification} ›{" "}
            {account.ebitdaClassification}
        </>
    )
}

function Card({ label, value, note, alert }: { label: string; value: string; note: string; alert: string }) {
    return (
        <div className={`card cxc al-${alert}`}>
            <span className="lab">{label}</span>
            <span className="val">{value}</span>
            <span className="note">{note}</span>
        </div>
    )
}

interface EditorProps {
    account: ImportedAccount
    catalog: DreCatalogEntry[] | undefined
    saving: boolean
    onCancel: () => void
    onSave: (classificationId: string) => void
}

/** Editor da conta (dpEditor do protótipo), no modo "Escolher na estrutura das demonstrações". */
function ClassificationEditor({ account, catalog, saving, onCancel, onSave }: EditorProps) {
    const [selected, setSelected] = useState(account.classificationId ?? "")
    const groups = useMemo(() => {
        const map = new Map<string, DreCatalogEntry[]>()

        for (const entry of catalog ?? []) {
            map.set(entry.dreClassification, [...(map.get(entry.dreClassification) ?? []), entry])
        }

        return [...map.entries()]
    }, [catalog])

    return (
        <tr className="dp-ed">
            <td colSpan={5}>
                <div className="dp-edf">
                    <div className="dp-modos">
                        <label className="dp-modo">
                            <input type="radio" name={`dpModo${account.accountId}`} value="est" defaultChecked />
                            <span>
                                <b>Escolher na estrutura das demonstrações</b>
                                <select
                                    data-f="est"
                                    value={selected}
                                    disabled={!catalog}
                                    onChange={(event) => setSelected(event.target.value)}
                                >
                                    <option value="" disabled>
                                        {catalog ? "Escolha a linha da DRE…" : "Carregando…"}
                                    </option>
                                    {groups.map(([group, entries]) => (
                                        <optgroup key={group} label={`DRE · ${group}`}>
                                            {entries.map((entry) => (
                                                <option key={entry.id} value={entry.id}>
                                                    {entry.dreOpenClassification} › {entry.ebitdaClassification}
                                                </option>
                                            ))}
                                        </optgroup>
                                    ))}
                                </select>
                            </span>
                        </label>
                    </div>
                    <div className="dp-bts">
                        <button type="button" className="btn sq" data-dp="cancelar" onClick={onCancel}>
                            Cancelar
                        </button>
                        <button
                            type="button"
                            className="btn primary sq"
                            data-dp="salvar"
                            disabled={!selected || saving}
                            onClick={() => onSave(selected)}
                        >
                            {saving ? "Salvando…" : "Salvar classificação"}
                        </button>
                    </div>
                    <p className="dp-hint">
                        Atual: <b>{account.status === "CLASSIFIED" ? destination(account) : "sem classificação"}</b>. A
                        classificação vale só para esta conta. Balanço, regras por grupo de contas e novas categorias:
                        Em breve.
                    </p>
                </div>
            </td>
        </tr>
    )
}

/**
 * De-Para do Plano de Contas (`depara`) com o contrato #442: contas que vieram
 * nos movimentos sincronizados (não é o plano completo) e a classificação na
 * DRE. Filtros, cards e regras sem contrato ficam visíveis como "Em breve".
 */
export function DeparaScreen() {
    const { company, companyGrant } = useAccountingContext()
    const toast = useAccountingToast()
    const queryClient = useQueryClient()
    const userId = useSessionUserId()
    const [filter, setFilter] = useState<Filter>("pend")
    const [query, setQuery] = useState("")
    const [editing, setEditing] = useState<string | null>(null)
    const [drilldown, setDrilldown] = useState<ImportedAccount | null>(null)
    const search = useDebounced(query, 300)
    const companyId = company?.companyId ?? null
    const permissions = companyGrant?.permissions ?? []
    const canRead =
        permissions.includes("ACCOUNT_CLASSIFICATION_VIEW") || permissions.includes("ACCOUNT_CLASSIFICATION_MANAGE")
    const canClassify = permissions.includes("ACCOUNT_CLASSIFICATION_MANAGE")
    const canSeeMovements = permissions.includes("ACCOUNTING_MOVEMENT_VIEW")
    const navigate = useNavigate()
    const sources = useAccountingSources(companyId, permissions)
    const chartScreen = findAccountingScreenById("plano")
    const sourceIds = useMemo(() => sources.options.map((option) => option.id), [sources.options])
    const membership = useChartMembership(companyId, sourceIds, canRead)

    const accounts = useInfiniteQuery({
        queryKey: accountingQueryKey(userId, "accounts", companyId, filter, search),
        queryFn: ({ pageParam }) =>
            searchImportedAccounts(companyId as string, {
                status: filter === "pend" ? "PENDING" : null,
                search,
                page: pageParam,
                size: PAGE_SIZE,
            }),
        initialPageParam: 0,
        getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
        enabled: companyId !== null && canRead,
        retry: false,
    })

    const catalog = useQuery({
        queryKey: accountingQueryKey(userId, "dre-catalog"),
        queryFn: listDreCatalog,
        enabled: editing !== null,
        staleTime: 60_000,
        retry: false,
    })

    const classify = useMutation({
        mutationFn: ({ accountId, classificationId }: { accountId: string; classificationId: string }) =>
            classifyImportedAccount(companyId as string, accountId, classificationId),
        onSuccess: async () => {
            setEditing(null)
            toast("Classificação salva.", "ok")
            await queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, "accounts", companyId) })
            await queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, "dre", companyId) })
        },
        onError: (error) =>
            toast(
                apiErrorStatus(error) === 403
                    ? "Sem permissão para classificar contas desta empresa."
                    : "Não foi possível salvar a classificação.",
                "err",
            ),
    })

    const firstPage = accounts.data?.pages[0]
    const rows = accounts.data?.pages.flatMap((page) => page.items) ?? []
    const remaining = firstPage ? firstPage.totalItems - rows.length : 0
    const summary = firstPage?.summary

    let body: ReactNode

    if (!company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!canRead) {
        body = (
            <div className="empty">
                Sem acesso à classificação de contas desta empresa. Escolher a empresa não libera os dados dela.
            </div>
        )
    } else if (accounts.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (accounts.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(accounts.error) === 403
                    ? "Sem acesso à classificação de contas desta empresa."
                    : "Não foi possível carregar as contas."}{" "}
                <button type="button" className="lnk" onClick={() => void accounts.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else {
        body = (
            <div className="tbl-wrap">
                <table className="dp-t" id="dpTbl">
                    <thead>
                        <tr>
                            <th>Conta</th>
                            <th className="r">Saldo / resultado 12m</th>
                            <th>Classificação atual nas demonstrações</th>
                            <th>Origem</th>
                            <th />
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map((account) => (
                            <AccountRows
                                key={account.accountId}
                                account={account}
                                open={editing === account.accountId}
                                canClassify={canClassify}
                                canSeeMovements={canSeeMovements}
                                catalog={catalog.data}
                                saving={classify.isPending}
                                onToggle={() =>
                                    setEditing(editing === account.accountId ? null : account.accountId)
                                }
                                onSave={(classificationId) =>
                                    classify.mutate({ accountId: account.accountId, classificationId })
                                }
                                onDrilldown={() => setDrilldown(account)}
                                membership={
                                    membership.fullChartSources.has(account.accountingSourceId)
                                        ? membership.accounts.has(account.accountId)
                                            ? { inChart: true, accountType: membership.accounts.get(account.accountId)?.sourceCodes.accountType ?? null }
                                            : membership.complete
                                              ? { inChart: false, accountType: null }
                                              : null
                                        : null
                                }
                            />
                        ))}
                        {rows.length === 0 && (
                            <tr>
                                <td colSpan={5} className="empty">
                                    {filter === "pend"
                                        ? "Nenhuma conta importada pendente. Tudo classificado."
                                        : "Nenhuma conta para o filtro."}
                                </td>
                            </tr>
                        )}
                        {remaining > 0 && (
                            <tr>
                                <td colSpan={5}>
                                    <button
                                        type="button"
                                        className="lnk"
                                        data-dp="mais"
                                        disabled={accounts.isFetchingNextPage}
                                        onClick={() => void accounts.fetchNextPage()}
                                    >
                                        {accounts.isFetchingNextPage
                                            ? "Carregando…"
                                            : `Mostrar mais ${Math.min(PAGE_SIZE, remaining)} de ${remaining} contas restantes`}
                                    </button>
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        )
    }

    return (
        <section id="p-depara" className="grid" style={{ gap: 18 }} data-handoff="6.3">
            <div className="cx-grid g-big" id="dpCards">
                <Card
                    label="Contas importadas"
                    value={summary ? String(summary.total) : "—"}
                    note="contas conhecidas pelo FINEMP (veja o universo abaixo)"
                    alert="info"
                />
                <Card
                    label="Pendentes de classificação"
                    value={summary ? String(summary.pending) : "—"}
                    note="contas importadas ainda sem linha da DRE"
                    alert={summary && summary.pending > 0 ? "bad" : "ok"}
                />
                <Card
                    label="Classificadas"
                    value={summary ? String(summary.classified) : "—"}
                    note="com linha da DRE"
                    alert="ok"
                />
                <Card label="Regras de-para ativas" value="—" note="Em breve" alert="info" />
            </div>

            <div className="panel">
                <div className="toolbar">
                    <div className="l">
                        <h3 style={{ margin: 0 }}>
                            Classificação das contas
                            <InfoButton title="Classificação das contas">
                                <p className="info-p">
                                    {firstPage
                                        ? `${firstPage.totalItems} contas no filtro · ${company?.legalName ?? ""}`
                                        : "Escolha uma empresa com acesso à classificação."}
                                </p>
                                <p className="info-p">
                                    Contas conhecidas pelo FINEMP para a empresa. Sem o plano completo publicado, só
                                    aparecem as contas que vieram nos movimentos importados (não é o plano completo);
                                    com o plano publicado, aparecem também as contas sem movimento. O aviso "Universo
                                    de contas" diz qual é o caso. Toda conta precisa dizer em que linha da DRE ela
                                    entra. A classificação do balanço e as regras por grupo de contas chegam em uma
                                    próxima etapa.
                                </p>
                            </InfoButton>
                        </h3>
                        <div className="tabs" id="dpFiltro" role="group" aria-label="Filtro">
                            {FILTERS.map((item) => (
                                <button
                                    key={item.key}
                                    className={item.key === filter ? "pill on" : "pill"}
                                    data-f={item.key}
                                    type="button"
                                    disabled={!item.available}
                                    title={item.available ? undefined : "Em breve"}
                                    onClick={() => {
                                        if (item.available) {
                                            setFilter(item.key as Filter)
                                            setEditing(null)
                                        }
                                    }}
                                >
                                    {item.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="rr">
                        <input
                            className="search"
                            id="dpBusca"
                            type="search"
                            placeholder="Buscar código ou conta…"
                            aria-label="Buscar contas"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                        />
                    </div>
                </div>
                {company && canRead && (
                    <ChartUniverseNotice
                        companyId={company.companyId}
                        sources={sources.options}
                        onOpenChart={chartScreen ? () => navigate(chartScreen.path) : undefined}
                    />
                )}
                {body}
            </div>

            <SoonPanel title="Regras de-para ativas" />

            {drilldown && company && (
                <MovementDrilldownModal
                    companyId={company.companyId}
                    account={drilldown}
                    onClose={() => setDrilldown(null)}
                />
            )}
        </section>
    )
}

interface AccountRowsProps {
    account: ImportedAccount
    open: boolean
    canClassify: boolean
    canSeeMovements: boolean
    catalog: DreCatalogEntry[] | undefined
    saving: boolean
    onToggle: () => void
    onSave: (classificationId: string) => void
    onDrilldown: () => void
    /** Só quando a fonte tem plano completo publicado (FULL_CHART); null = sem informação. */
    membership: { inChart: boolean; accountType: string | null } | null
}

function AccountRows({
    account,
    open,
    canClassify,
    canSeeMovements,
    catalog,
    saving,
    onToggle,
    onSave,
    onDrilldown,
    membership,
}: AccountRowsProps) {
    const kind = membership?.inChart ? inferredAccountKind(membership.accountType) : null

    return (
        <>
            <tr className={open ? "dp-on" : undefined}>
                <td className="wrapcell">
                    {canSeeMovements && (
                        <button
                            type="button"
                            className="it-lupa"
                            aria-label={`Lançamentos de ${account.description}`}
                            title="Ver os lançamentos da conta"
                            onClick={onDrilldown}
                        >
                            <svg viewBox="0 0 24 24">
                                <circle cx="11" cy="11" r="6" />
                                <path d="M20 20l-4.5-4.5" />
                            </svg>
                        </button>
                    )}
                    {account.description}
                    <small className="dp-cod">{account.accountKey}</small>
                    {membership && (
                        <small className="ctb-membership">
                            {membership.inChart ? (
                                <span className="tag ok">No plano completo</span>
                            ) : (
                                <span className="tag pend" title="Conta vista nos movimentos importados, fora do plano publicado">
                                    Só observada
                                </span>
                            )}
                            {kind && (
                                <span title={INFERRED_ACCOUNT_KIND_NOTE}>
                                    {" "}
                                    {kind.label} <span className="tag est">{kind.tag}</span>
                                </span>
                            )}
                        </small>
                    )}
                </td>
                <td className="n muted" title="Saldo ainda não sincronizado (Em breve)">
                    —
                </td>
                <td className="wrapcell">{destination(account)}</td>
                <td>
                    {account.status === "CLASSIFIED" ? (
                        <span className="tag ok">Classificada</span>
                    ) : (
                        <span className="tag pend" title="Conta importada ainda sem linha da DRE">
                            Pendente
                        </span>
                    )}
                </td>
                <td>
                    {canClassify && (
                        <button type="button" className="btn sq" data-dp="editar" onClick={onToggle}>
                            {open ? "Fechar" : account.status === "CLASSIFIED" ? "Alterar" : "Classificar"}
                        </button>
                    )}
                </td>
            </tr>
            {open && (
                <ClassificationEditor
                    account={account}
                    catalog={catalog}
                    saving={saving}
                    onCancel={onToggle}
                    onSave={onSave}
                />
            )}
        </>
    )
}
