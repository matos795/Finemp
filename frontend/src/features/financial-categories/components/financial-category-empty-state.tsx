import { emptyCatalogActions, type FinancialCategoryCapabilities } from "../lib/financial-category-access"

/**
 * Company sem nenhuma categoria. Nada é criado ao abrir a tela: o plano
 * padrão só é aplicado pelo clique de quem pode gerir a estrutura.
 */
export function FinancialCategoryEmptyState({ capabilities, busy, onApplyStandardPlan, onCreateFirstGroup }: {
    capabilities: FinancialCategoryCapabilities
    busy: boolean
    onApplyStandardPlan: () => void
    onCreateFirstGroup: () => void
}) {
    const actions = emptyCatalogActions(capabilities)

    return (
        <div className="panel">
            <h2>Esta empresa ainda não possui plano de contas financeiro</h2>

            {actions.length > 0 ? (
                <>
                    <p className="help">
                        Aplique o plano padrão FINEMP (grupos e subcategorias de receitas e despesas já
                        classificados para fluxo de caixa e DRE) ou crie os grupos um a um.
                    </p>
                    <div className="mact" style={{ justifyContent: "flex-start" }}>
                        {actions.includes("apply-standard-plan") ? (
                            <button type="button" className="btn" disabled={busy} onClick={onApplyStandardPlan}>
                                {busy ? "Aplicando…" : "Aplicar plano padrão"}
                            </button>
                        ) : null}
                        {actions.includes("create-first-group") ? (
                            <button type="button" className="btn sec" disabled={busy} onClick={onCreateFirstGroup}>
                                Criar primeiro grupo
                            </button>
                        ) : null}
                    </div>
                </>
            ) : (
                <p className="help">
                    Peça a quem administra o financeiro desta empresa para aplicar o plano padrão ou criar
                    os grupos de receitas e despesas.
                </p>
            )}
        </div>
    )
}
