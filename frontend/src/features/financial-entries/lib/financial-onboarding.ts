import { ApiError } from "../../../lib/api/api-client.ts"
import type { CompanyPermission } from "../../auth/types/auth.types.ts"
import type { StandardPlanResult } from "../types/financial-catalog.types.ts"

/** Situação de um catálogo carregado pelo React Query. */
export type CatalogLoad<T> =
    | { status: "loading" }
    | { status: "error" }
    | { status: "ready", items: T[] }

export type BankAccountStep =
    | { kind: "ok" }
    | { kind: "loading" }
    | { kind: "no-access" }
    /** Sem conta ativa; o usuário pode cadastrar em Cadastros › Contas bancárias. */
    | { kind: "missing", action: { type: "navigate", to: "/bank-accounts" } }
    /** Sem conta ativa e sem BANK_ACCOUNT_MANAGE: só informa. */
    | { kind: "missing-no-permission" }

export type CategoryStep =
    | { kind: "ok" }
    | { kind: "loading" }
    | { kind: "no-access" }
    /** Catálogo totalmente vazio e FINANCIAL_CATEGORY_MANAGE: oferece o plano padrão. */
    | { kind: "empty", action: { type: "apply-standard-plan" } }
    /** Catálogo vazio sem permissão de gerenciar: só informa. */
    | { kind: "empty-no-permission" }
    /** Há grupos, mas nenhuma subcategoria: o plano padrão não se aplica. */
    | { kind: "incomplete" }

export interface FinancialOnboarding {
    /** true quando há conta ativa e subcategoria: comportamento normal da #315. */
    ready: boolean
    bankAccount: BankAccountStep
    categories: CategoryStep
}

interface OnboardingInput {
    hasPermission: (permission: CompanyPermission) => boolean
    bankAccounts: CatalogLoad<{ active: boolean }>
    categories: CatalogLoad<{ kind: "GROUP" | "SUBCATEGORY" }>
}

/**
 * O que falta para a Company ativa poder receber o primeiro lançamento.
 * Decide só por permissões explícitas da Company e pelos catálogos lidos;
 * o papel (CLIENT, ACCOUNTANT…) não entra na decisão.
 */
export function financialOnboarding({
    hasPermission,
    bankAccounts,
    categories,
}: OnboardingInput): FinancialOnboarding {
    const bankAccount: BankAccountStep =
        bankAccounts.status === "loading"
            ? { kind: "loading" }
            : bankAccounts.status === "error"
                ? { kind: "no-access" }
                : bankAccounts.items.some((account) => account.active)
                    ? { kind: "ok" }
                    : hasPermission("BANK_ACCOUNT_MANAGE")
                        ? { kind: "missing", action: { type: "navigate", to: "/bank-accounts" } }
                        : { kind: "missing-no-permission" }

    const categoryStep: CategoryStep =
        categories.status === "loading"
            ? { kind: "loading" }
            : categories.status === "error"
                ? { kind: "no-access" }
                : categories.items.some((category) => category.kind === "SUBCATEGORY")
                    ? { kind: "ok" }
                    : categories.items.length > 0
                        ? { kind: "incomplete" }
                        : hasPermission("FINANCIAL_CATEGORY_MANAGE")
                            ? { kind: "empty", action: { type: "apply-standard-plan" } }
                            : { kind: "empty-no-permission" }

    return {
        ready: bankAccount.kind === "ok" && categoryStep.kind === "ok",
        bankAccount,
        categories: categoryStep,
    }
}

export interface StandardPlanFailure {
    message: string
    /** Refazer o catálogo antes de qualquer nova tentativa (409). */
    refreshCatalog: boolean
    /** Não oferecer o botão de novo: o plano já existe ou está sendo criado. */
    allowRetry: boolean
    sessionExpired: boolean
}

/**
 * Falha do plano padrão para o usuário, sem códigos internos. Conflitos
 * (PLAN_NOT_EMPTY ou concorrência) nunca levam a uma nova tentativa
 * automática: o catálogo é relido e a tela decide pelo estado atual.
 */
export function describeStandardPlanFailure(error: unknown): StandardPlanFailure {
    const failure = (message: string, extra: Partial<StandardPlanFailure> = {}): StandardPlanFailure => ({
        message,
        refreshCatalog: false,
        allowRetry: true,
        sessionExpired: false,
        ...extra,
    })

    if (!(error instanceof ApiError)) {
        return failure("Não foi possível aplicar o plano padrão. Verifique a conexão e tente de novo.", { refreshCatalog: true })
    }

    if (error.code === "PLAN_NOT_EMPTY") {
        return failure("Esta empresa já tem categorias cadastradas, então o plano padrão não foi aplicado. A lista foi atualizada.", {
            refreshCatalog: true,
            allowRetry: false,
        })
    }

    switch (error.status) {
        case 400:
            return failure("O sistema recusou a aplicação do plano padrão. Recarregue a página e tente de novo.")
        case 401:
            return failure("Sua sessão terminou. Entre de novo.", { sessionExpired: true, allowRetry: false })
        case 403:
            return failure("Você não tem permissão para gerenciar as categorias desta empresa.", { allowRetry: false })
        case 404:
            return failure("O cadastro de categorias não está disponível neste ambiente.", { allowRetry: false })
        case 409:
            return failure("As categorias foram alteradas ao mesmo tempo por outra pessoa. A lista foi atualizada.", {
                refreshCatalog: true,
                allowRetry: false,
            })
    }

    return failure("Não foi possível aplicar o plano padrão. Tente de novo em instantes.", { refreshCatalog: true })
}

/** Chave do catálogo de categorias de uma Company (a mesma de useFinancialEntryCatalogs). */
export function financialCategoriesQueryKey(companyId: string) {
    return ["companies", companyId, "financial-categories"] as const
}

/** Relê o catálogo de categorias só da Company informada. */
export function refreshFinancialCategories(
    queryClient: { invalidateQueries: (filters: { queryKey: readonly unknown[] }) => Promise<unknown> },
    companyId: string,
) {
    return queryClient.invalidateQueries({ queryKey: financialCategoriesQueryKey(companyId) })
}

/** Aviso do onboarding; pertence à Company em que a ação foi feita. */
export interface OnboardingNotice {
    companyId: string
    tone: "ok" | "info" | "error"
    message: string
}

/** Só mostra o aviso na mesma Company em que ele surgiu. */
export function noticeForCompany(
    notice: OnboardingNotice | null,
    companyId: string | undefined,
) {
    return notice && notice.companyId === companyId ? notice : null
}

export function standardPlanSuccessMessage(result: StandardPlanResult) {
    return `Plano padrão FINEMP aplicado: ${result.groups} grupos e ${result.subcategories} subcategorias criados.`
}
