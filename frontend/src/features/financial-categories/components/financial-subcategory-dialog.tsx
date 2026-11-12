import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { useCategoryCommand } from "../hooks/use-category-command"
import { useFinancialCategoryMutations } from "../hooks/use-financial-categories"
import { validateCategoryName } from "../lib/financial-category-form"
import type { FinancialCategory } from "../types/financial-category.types"
import { CategoryNameField } from "./category-name-field"
import { DialogError } from "./dialog-error"

/** Nova subcategoria dentro de um grupo: só o nome. */
export function FinancialSubcategoryDialog({ companyId, group, onClose }: {
    companyId: string
    group: FinancialCategory
    onClose: () => void
}) {
    const { createSubcategory } = useFinancialCategoryMutations(companyId)
    const { error, run } = useCategoryCommand(companyId, onClose)
    const [name, setName] = useState("")
    const [nameError, setNameError] = useState<string>()
    const busy = createSubcategory.isPending

    function submit() {
        const problem = validateCategoryName(name)
        setNameError(problem)

        if (!problem) {
            void run(() => createSubcategory.mutateAsync({ group, name }), "Subcategoria criada.")
        }
    }

    return (
        <Modal width={480} labelledBy="category-sub-title" onClose={busy ? undefined : onClose}>
            <h2 id="category-sub-title">Nova subcategoria</h2>
            <p className="help">
                Em {group.code} · {group.name}. O código é gerado pelo sistema; a classificação
                vem do grupo até ser alterada por quem gerencia a classificação.
            </p>

            <div className="fg">
                <CategoryNameField id="categorySubName" value={name} error={nameError} onChange={setName} />
            </div>

            <DialogError message={error} />

            <div className="mact">
                <button type="button" className="btn sec" disabled={busy} onClick={onClose}>Cancelar</button>
                <button type="button" className="btn" disabled={busy} onClick={submit}>
                    {busy ? "Criando…" : "Criar subcategoria"}
                </button>
            </div>
        </Modal>
    )
}
