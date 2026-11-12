import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { useCategoryCommand } from "../hooks/use-category-command"
import { useFinancialCategoryMutations } from "../hooks/use-financial-categories"
import { DIRECTION_SECTION_LABELS, validateCategoryName } from "../lib/financial-category-form"
import type { FinancialCategoryDirection } from "../types/financial-category.types"
import { CategoryNameField } from "./category-name-field"
import { DialogError } from "./dialog-error"

/** Novo grupo: direção e nome. Código e subcategoria inicial vêm do backend. */
export function FinancialCategoryGroupDialog({ companyId, initialDirection, onClose }: {
    companyId: string
    initialDirection: FinancialCategoryDirection
    onClose: () => void
}) {
    const { createGroup } = useFinancialCategoryMutations(companyId)
    const { error, run } = useCategoryCommand(companyId, onClose)
    const [direction, setDirection] = useState(initialDirection)
    const [name, setName] = useState("")
    const [nameError, setNameError] = useState<string>()
    const busy = createGroup.isPending

    function submit() {
        const problem = validateCategoryName(name)
        setNameError(problem)

        if (!problem) {
            void run(() => createGroup.mutateAsync({ direction, name }), "Grupo criado com uma subcategoria inicial.")
        }
    }

    return (
        <Modal width={480} labelledBy="category-group-title" onClose={busy ? undefined : onClose}>
            <h2 id="category-group-title">Novo grupo</h2>
            <p className="help">O código e uma subcategoria inicial são gerados pelo sistema.</p>

            <div className="fg">
                <div className="fl full">
                    <label>Tipo de movimento</label>
                    <div className="radios">
                        {(["INCOME", "EXPENSE"] as const).map((value) => (
                            <label key={value}>
                                <input
                                    type="radio"
                                    name="categoryGroupDirection"
                                    checked={direction === value}
                                    onChange={() => setDirection(value)}
                                />{" "}
                                {DIRECTION_SECTION_LABELS[value]}
                            </label>
                        ))}
                    </div>
                </div>

                <CategoryNameField id="categoryGroupName" value={name} error={nameError} onChange={setName} />
            </div>

            <DialogError message={error} />

            <div className="mact">
                <button type="button" className="btn sec" disabled={busy} onClick={onClose}>Cancelar</button>
                <button type="button" className="btn" disabled={busy} onClick={submit}>
                    {busy ? "Criando…" : "Criar grupo"}
                </button>
            </div>
        </Modal>
    )
}
