import { Fragment, useEffect, useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ConfirmDialog } from "../../../components/finemp/confirm-dialog"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useApplyStandardPlan } from "../../financial-entries/hooks/use-apply-standard-plan"
import {
    describeStandardPlanFailure,
    standardPlanSuccessMessage,
} from "../../financial-entries/lib/financial-onboarding"
import { FinancialCategoryClassificationDialog } from "../components/financial-category-classification-dialog"
import { FinancialCategoryEmptyState } from "../components/financial-category-empty-state"
import { FinancialCategoryGroupDialog } from "../components/financial-category-group-dialog"
import { FinancialCategoryRenameDialog } from "../components/financial-category-rename-dialog"
import { FinancialSubcategoryDialog } from "../components/financial-subcategory-dialog"
import {
    useFinancialCategoryMutations,
    useFinancialCategoryPage,
} from "../hooks/use-financial-categories"
import {
    financialCategoryCapabilities,
    rowActions,
} from "../lib/financial-category-access"
import { handleFinancialCategoryFailure } from "../lib/financial-category-errors"
import {
    CASH_ACTIVITY_LABELS,
    COST_BEHAVIOR_LABELS,
    DIRECTION_SECTION_LABELS,
    DRE_LINE_LABELS,
    KIND_LABELS,
    defaultFinancialCategoryFilter,
    inheritedFields,
    isUnfilteredEmptyCatalog,
    usesDirectionDefaultCost,
} from "../lib/financial-category-form"
import type {
    FinancialCategory,
    FinancialCategoryDirection,
    FinancialCategoryFilter,
} from "../types/financial-category.types"

const listErrorMessages = {
    400: "Confira a busca e os filtros.",
    403: "Você não tem acesso ao plano de contas desta empresa.",
    404: "O plano de contas não está disponível neste ambiente.",
}

type DialogState =
    | { mode: "group", direction: FinancialCategoryDirection }
    | { mode: "subcategory", group: FinancialCategory }
    | { mode: "rename", category: FinancialCategory }
    | { mode: "classify", category: FinancialCategory }
    | { mode: "delete", category: FinancialCategory }

const actionLabels = {
    "create-subcategory": "+ Sub",
    rename: "Renomear",
    classify: "Classificar",
    delete: "✕",
} as const

/**
 * Cadastros › Plano de contas (cadPlano): categorias financeiras usadas
 * nos lançamentos, fluxo de caixa e DRE operacional. Lista paginada e
 * filtrada no servidor; código, herança e classificação efetiva vêm da
 * API. Estrutura (criar, renomear, excluir, plano padrão) com
 * STRUCTURE_MANAGE ou MANAGE; classificação somente com MANAGE (#331).
 * Distinto do plano de contas da contabilidade (Questor).
 */
export function FinancialCategoriesPage() {
    const { selectedCompany } = useCompanyAccess()

    // O estado da tela pertence à Company ativa.
    return <FinancialCategoriesCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function FinancialCategoriesCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const capabilities = financialCategoryCapabilities(hasPermission)
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<FinancialCategoryFilter>(defaultFinancialCategoryFilter)
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState | null>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const categories = useFinancialCategoryPage(companyId, filter, page)
    const { remove } = useFinancialCategoryMutations(companyId ?? "")
    const applyPlan = useApplyStandardPlan(companyId ?? "")

    useEffect(() => {
        if (searchText === filter.search) {
            return
        }

        const timer = window.setTimeout(() => setFilter((current) => ({ ...current, search: searchText })), 350)

        return () => window.clearTimeout(timer)
    }, [searchText, filter.search])

    // Uma exclusão pode esvaziar a última página: volta para a última válida.
    useEffect(() => {
        const totalPages = categories.data?.totalPages

        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [categories.data?.totalPages, page, setPage])

    async function applyStandardPlan() {
        try {
            const result = await applyPlan.mutateAsync()
            showToast(standardPlanSuccessMessage(result))
        } catch (error) {
            const failure = describeStandardPlanFailure(error)

            if (failure.sessionExpired) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            showToast(failure.message, true)
        }
    }

    async function confirmDelete(category: FinancialCategory) {
        try {
            await remove.mutateAsync(category)
            showToast(category.kind === "GROUP" ? "Grupo excluído com as subcategorias." : "Subcategoria excluída.")
        } catch (error) {
            const failure = await handleFinancialCategoryFailure(queryClient, companyId ?? "", error)

            if (!failure.sessionExpired) {
                showToast(failure.message, true)
            }
        } finally {
            setDialog(null)
        }
    }

    const data = categories.data
    const items = data?.items ?? []
    const empty = isUnfilteredEmptyCatalog(filter, page, data)
    const hasActions = capabilities.canManageStructure || capabilities.canClassify
    const columns = hasActions ? 7 : 6

    function classificationCell(category: FinancialCategory, field: "cashActivity" | "dreLine" | "costBehavior") {
        const labels: Record<string, string> = field === "cashActivity"
            ? CASH_ACTIVITY_LABELS
            : field === "dreLine" ? DRE_LINE_LABELS : COST_BEHAVIOR_LABELS
        const value = category.effective[field]
        const inherited = inheritedFields(category).includes(field)
        const systemDefault = field === "costBehavior" && usesDirectionDefaultCost(category)

        return (
            <td style={{ fontSize: 13 }}>
                {labels[value] ?? value}
                {inherited ? <span className="mut" title="Sem valor próprio: segue o grupo"> · do grupo</span> : null}
                {systemDefault ? <span className="mut" title="Sem valor próprio: padrão do sistema"> · padrão</span> : null}
            </td>
        )
    }

    return (
        <>
            <p className="help" style={{ marginTop: 0 }}>
                Categorias financeiras usadas nos lançamentos, fluxo de caixa e DRE operacional.
                Os códigos são gerados pelo sistema.
            </p>

            {empty && companyId ? (
                <FinancialCategoryEmptyState
                    capabilities={capabilities}
                    busy={applyPlan.isPending}
                    onApplyStandardPlan={() => void applyStandardPlan()}
                    onCreateFirstGroup={() => setDialog({ mode: "group", direction: "INCOME" })}
                />
            ) : (
                <>
                    <div className="filters">
                        <div className="f">
                            <label htmlFor="fcBusca">Buscar</label>
                            <input
                                type="search"
                                id="fcBusca"
                                maxLength={100}
                                placeholder="Nome ou código…"
                                value={searchText}
                                onChange={(event) => setSearchText(event.target.value)}
                            />
                        </div>

                        <div className="f">
                            <label htmlFor="fcDir">Mostrar</label>
                            <select
                                id="fcDir"
                                value={filter.direction}
                                onChange={(event) => setFilter({ ...filter, direction: event.target.value as FinancialCategoryFilter["direction"] })}
                            >
                                <option value="">Receitas e despesas</option>
                                <option value="INCOME">Receitas</option>
                                <option value="EXPENSE">Despesas</option>
                            </select>
                        </div>

                        <div className="f">
                            <label htmlFor="fcTipo">Tipo</label>
                            <select
                                id="fcTipo"
                                value={filter.kind}
                                onChange={(event) => setFilter({ ...filter, kind: event.target.value as FinancialCategoryFilter["kind"] })}
                            >
                                <option value="">Grupos e subcategorias</option>
                                <option value="GROUP">Grupos</option>
                                <option value="SUBCATEGORY">Subcategorias</option>
                            </select>
                        </div>

                        {capabilities.canManageStructure ? (
                            <div className="f">
                                <label>&nbsp;</label>
                                <button
                                    type="button"
                                    className="btn"
                                    disabled={!companyId}
                                    onClick={() => setDialog({ mode: "group", direction: filter.direction || "INCOME" })}
                                >
                                    + Grupo
                                </button>
                            </div>
                        ) : null}
                    </div>

                    {categories.isError ? (
                        <QueryError
                            error={categories.error}
                            messages={listErrorMessages}
                            fallback="Não foi possível carregar o plano de contas."
                            onRetry={() => categories.refetch()}
                        />
                    ) : (
                        <div className="panel" style={{ padding: 0 }}>
                            <div className="tblwrap" style={categories.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Código</th>
                                            <th>Nome</th>
                                            <th>Tipo</th>
                                            <th>Atividade (fluxo de caixa)</th>
                                            <th>Linha da DRE (competência)</th>
                                            <th>Fixo / variável</th>
                                            {hasActions ? <th>Ações</th> : null}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {items.map((category, index) => {
                                            const newSection = index === 0 || items[index - 1].direction !== category.direction
                                            const isGroup = category.kind === "GROUP"
                                            const actions = rowActions(category, capabilities)

                                            return (
                                                <Fragment key={category.id}>
                                                    {newSection ? (
                                                        <tr>
                                                            <td
                                                                colSpan={columns}
                                                                className={category.direction === "INCOME" ? "pos" : "neg"}
                                                                style={{ fontWeight: 600 }}
                                                            >
                                                                {DIRECTION_SECTION_LABELS[category.direction]}
                                                            </td>
                                                        </tr>
                                                    ) : null}
                                                    <tr className={isGroup ? "grp" : undefined}>
                                                        <td style={{ whiteSpace: "nowrap" }}>
                                                            {isGroup ? <b style={{ color: "var(--gold)" }}>{category.code}</b> : category.code}
                                                        </td>
                                                        <td style={isGroup ? { fontWeight: 600 } : { paddingLeft: 24 }}>{category.name}</td>
                                                        <td style={{ fontSize: 13 }}>{KIND_LABELS[category.kind]}</td>
                                                        {classificationCell(category, "cashActivity")}
                                                        {classificationCell(category, "dreLine")}
                                                        {classificationCell(category, "costBehavior")}
                                                        {hasActions ? (
                                                            <td style={{ whiteSpace: "nowrap" }}>
                                                                {actions.map((action) => (
                                                                    <Fragment key={action}>
                                                                        <button
                                                                            type="button"
                                                                            className="btn sec sm"
                                                                            aria-label={`${action === "delete" ? "Excluir" : actionLabels[action]} ${category.code} ${category.name}`}
                                                                            disabled={remove.isPending}
                                                                            onClick={() => setDialog(
                                                                                action === "create-subcategory"
                                                                                    ? { mode: "subcategory", group: category }
                                                                                    : action === "rename"
                                                                                        ? { mode: "rename", category }
                                                                                        : action === "classify"
                                                                                            ? { mode: "classify", category }
                                                                                            : { mode: "delete", category },
                                                                            )}
                                                                        >
                                                                            {actionLabels[action]}
                                                                        </button>{" "}
                                                                    </Fragment>
                                                                ))}
                                                            </td>
                                                        ) : null}
                                                    </tr>
                                                </Fragment>
                                            )
                                        })}
                                    </tbody>
                                </table>
                            </div>

                            {categories.isPending && categories.fetchStatus !== "idle" ? (
                                <div className="empty">Carregando…</div>
                            ) : items.length === 0 && data ? (
                                <div className="empty">Nenhuma categoria com esses filtros.</div>
                            ) : null}

                            <Pager
                                page={page}
                                totalPages={data?.totalPages ?? 0}
                                disabled={categories.isFetching}
                                onChange={setPage}
                            />
                        </div>
                    )}
                </>
            )}

            {companyId && dialog?.mode === "group" ? (
                <FinancialCategoryGroupDialog companyId={companyId} initialDirection={dialog.direction} onClose={() => setDialog(null)} />
            ) : null}

            {companyId && dialog?.mode === "subcategory" ? (
                <FinancialSubcategoryDialog companyId={companyId} group={dialog.group} onClose={() => setDialog(null)} />
            ) : null}

            {companyId && dialog?.mode === "rename" ? (
                <FinancialCategoryRenameDialog
                    key={`${dialog.category.id}-${dialog.category.version}`}
                    companyId={companyId}
                    category={dialog.category}
                    onClose={() => setDialog(null)}
                />
            ) : null}

            {companyId && dialog?.mode === "classify" && capabilities.canClassify ? (
                <FinancialCategoryClassificationDialog
                    key={`${dialog.category.id}-${dialog.category.version}`}
                    companyId={companyId}
                    category={dialog.category}
                    onClose={() => setDialog(null)}
                />
            ) : null}

            {dialog?.mode === "delete" ? (
                <ConfirmDialog
                    title={dialog.category.kind === "GROUP" ? "Excluir grupo" : "Excluir subcategoria"}
                    message={dialog.category.kind === "GROUP"
                        ? `O grupo "${dialog.category.code} · ${dialog.category.name}" e suas subcategorias serão excluídos. Só é possível excluir quando não há lançamentos vinculados.`
                        : `A subcategoria "${dialog.category.code} · ${dialog.category.name}" será excluída. Só é possível excluir quando não há lançamentos vinculados.`}
                    confirmLabel="Excluir"
                    busy={remove.isPending}
                    onConfirm={() => void confirmDelete(dialog.category)}
                    onCancel={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}
