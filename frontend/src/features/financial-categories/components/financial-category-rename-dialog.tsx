import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { useCategoryCommand } from "../hooks/use-category-command"
import { useFinancialCategoryMutations } from "../hooks/use-financial-categories"
import { KIND_LABELS, validateCategoryName } from "../lib/financial-category-form"
import type { FinancialCategory } from "../types/financial-category.types"
import { CategoryNameField } from "./category-name-field"
import { DialogError } from "./dialog-error"

/**
 * Renomear grupo ou subcategoria pelo comando dedicado (POST …/rename),
 * com a versão recebida da API. Não envia nem altera classificação.
 */
export function FinancialCategoryRenameDialog({ companyId, category, onClose }: {
    companyId: string
    category: FinancialCategory
    onClose: () => void
}) {
    const { rename } = useFinancialCategoryMutations(companyId)
    const { error, run } = useCategoryCommand(companyId, onClose)
    const [name, setName] = useState(category.name)
    const [nameError, setNameError] = useState<string>()
    const busy = rename.isPending

    function submit() {
        const problem = validateCategoryName(name)
        setNameError(problem)

        if (problem) {
            return
        }

        if (name.trim() === category.name) {
            onClose()
            return
        }

        void run(() => rename.mutateAsync({ category, name }), "Nome atualizado.")
    }

    return (
        <Modal width={480} labelledBy="category-rename-title" onClose={busy ? undefined : onClose}>
            <h2 id="category-rename-title">Renomear {KIND_LABELS[category.kind].toLowerCase()}</h2>
            <p className="help">{category.code} · a classificação não muda ao renomear.</p>

            <div className="fg">
                <CategoryNameField id="categoryRenameName" value={name} error={nameError} onChange={setName} />
            </div>

            <DialogError message={error} />

            <div className="mact">
                <button type="button" className="btn sec" disabled={busy} onClick={onClose}>Cancelar</button>
                <button type="button" className="btn" disabled={busy} onClick={submit}>
                    {busy ? "Salvando…" : "Salvar nome"}
                </button>
            </div>
        </Modal>
    )
}
