import {
    useState,
    type ReactNode,
} from "react"
import { Link } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useApplyStandardPlan } from "../hooks/use-apply-standard-plan"
import {
    describeStandardPlanFailure,
    noticeForCompany,
    standardPlanSuccessMessage,
    type FinancialOnboarding,
    type OnboardingNotice,
} from "../lib/financial-onboarding"

interface FinancialOnboardingPanelProps {
    companyId: string
    onboarding: FinancialOnboarding
}

/**
 * Passos que faltam para o primeiro lançamento da Company ativa: conta
 * bancária ativa e categorias. Conta é cadastrada na tela existente
 * (Cadastros › Contas bancárias); categorias, pelo plano padrão FINEMP,
 * só por clique explícito. Nada é criado ao abrir a página.
 */
export function FinancialOnboardingPanel({
    companyId,
    onboarding,
}: FinancialOnboardingPanelProps) {
    const queryClient = useQueryClient()
    const applyPlan = useApplyStandardPlan(companyId)
    const [notice, setNotice] = useState<OnboardingNotice | null>(null)
    const [planLocked, setPlanLocked] = useState(false)

    const visibleNotice = noticeForCompany(notice, companyId)

    async function applyStandardPlan() {
        setNotice(null)

        try {
            const result = await applyPlan.mutateAsync()

            setPlanLocked(true)
            setNotice({ companyId, tone: "ok", message: standardPlanSuccessMessage(result) })
        } catch (error) {
            const failure = describeStandardPlanFailure(error)

            if (failure.sessionExpired) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            if (!failure.allowRetry) {
                setPlanLocked(true)
            }

            setNotice({
                companyId,
                tone: failure.refreshCatalog && !failure.allowRetry ? "info" : "error",
                message: failure.message,
            })
        }
    }

    const { bankAccount, categories } = onboarding

    const steps: { key: string, content: ReactNode }[] = []

    if (bankAccount.kind === "no-access") {
        steps.push({
            key: "bank",
            content: "Seu acesso não inclui as contas bancárias desta empresa. Peça ao administrador para liberar a visualização das contas.",
        })
    } else if (bankAccount.kind === "missing") {
        steps.push({
            key: "bank",
            content: (
                <>
                    Para lançar, a empresa precisa de pelo menos uma conta bancária ativa.{" "}
                    <Link
                        to={bankAccount.action.to}
                        className="btn sec sm"
                    >
                        Cadastrar conta bancária
                    </Link>
                </>
            ),
        })
    } else if (bankAccount.kind === "missing-no-permission") {
        steps.push({
            key: "bank",
            content: "Para lançar, a empresa precisa de pelo menos uma conta bancária ativa. Seu acesso não permite cadastrar contas; peça a quem administra a empresa.",
        })
    }

    if (categories.kind === "no-access") {
        steps.push({
            key: "categories",
            content: "Seu acesso não inclui as categorias financeiras desta empresa. Peça ao administrador para liberar a visualização das categorias.",
        })
    } else if (categories.kind === "empty") {
        steps.push({
            key: "categories",
            content: (
                <>
                    A empresa ainda não tem categorias financeiras. Você pode aplicar o
                    plano padrão FINEMP (grupos e subcategorias homologados).{" "}
                    <button
                        type="button"
                        className="btn sm"
                        disabled={applyPlan.isPending || planLocked}
                        onClick={applyStandardPlan}
                    >
                        {applyPlan.isPending ? "Aplicando plano…" : "Aplicar plano padrão FINEMP"}
                    </button>
                </>
            ),
        })
    } else if (categories.kind === "empty-no-permission") {
        steps.push({
            key: "categories",
            content: "A empresa ainda não tem categorias financeiras. Elas precisam ser configuradas por alguém com permissão para gerenciar categorias.",
        })
    } else if (categories.kind === "incomplete") {
        steps.push({
            key: "categories",
            content: "A empresa tem grupos de categorias, mas nenhuma subcategoria. É preciso cadastrar subcategorias antes de lançar.",
        })
    }

    if (steps.length === 0 && !visibleNotice) {
        return null
    }

    return (
        <div aria-live="polite">
            {visibleNotice ? (
                <div
                    className={visibleNotice.tone === "error" ? "alert e" : "alert i"}
                    role={visibleNotice.tone === "error" ? "alert" : "status"}
                >
                    {visibleNotice.message}
                </div>
            ) : null}

            {steps.map((step) => (
                <div
                    key={step.key}
                    className="alert i"
                >
                    {step.content}
                </div>
            ))}
        </div>
    )
}
