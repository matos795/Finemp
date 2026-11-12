import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { useCategoryCommand } from "../hooks/use-category-command"
import { useFinancialCategoryMutations } from "../hooks/use-financial-categories"
import {
    CASH_ACTIVITY_LABELS,
    COST_BEHAVIOR_LABELS,
    DRE_LINE_LABELS,
    classificationFormFrom,
    toClassificationRequest,
    validateClassificationForm,
    type ClassificationForm,
    type ClassificationFormErrors,
} from "../lib/financial-category-form"
import type { FinancialCategory } from "../types/financial-category.types"
import { DialogError } from "./dialog-error"

type Field = keyof ClassificationForm

const FIELDS: { field: Field, label: string, options: Record<string, string> }[] = [
    { field: "cashActivity", label: "Atividade (fluxo de caixa)", options: CASH_ACTIVITY_LABELS },
    { field: "dreLine", label: "Linha da DRE (competência)", options: DRE_LINE_LABELS },
    { field: "costBehavior", label: "Fixo / variável", options: COST_BEHAVIOR_LABELS },
]

/** Rótulo da opção "sem valor próprio" de cada campo. */
function emptyOptionLabel(category: FinancialCategory, field: Field) {
    if (category.kind === "SUBCATEGORY") {
        return "Do grupo"
    }

    return field === "costBehavior" ? "Padrão do sistema" : null
}

/**
 * Classificação (PUT completo; somente FINANCIAL_CATEGORY_MANAGE). Edita
 * os valores próprios (raw); o efetivo de cada campo é o que a API
 * devolveu e só é exibido. Herança e a fotografia da DRE ficam no backend.
 */
export function FinancialCategoryClassificationDialog({ companyId, category, onClose }: {
    companyId: string
    category: FinancialCategory
    onClose: () => void
}) {
    const { classify } = useFinancialCategoryMutations(companyId)
    const { error, run } = useCategoryCommand(companyId, onClose)
    const [form, setForm] = useState<ClassificationForm>(() => classificationFormFrom(category))
    const [errors, setErrors] = useState<ClassificationFormErrors>({})
    const busy = classify.isPending
    const subcategory = category.kind === "SUBCATEGORY"

    function submit() {
        const found = validateClassificationForm(category, form)
        setErrors(found)

        if (Object.keys(found).length === 0) {
            void run(() => classify.mutateAsync({ category, classification: toClassificationRequest(form) }),
                "Classificação atualizada.")
        }
    }

    return (
        <Modal width={600} labelledBy="category-classification-title" onClose={busy ? undefined : onClose}>
            <h2 id="category-classification-title">Classificar categoria</h2>
            <p className="help">
                {category.code} · {category.name}. Defina como esta categoria entra no fluxo de caixa,
                na DRE e na separação fixo/variável. Para mudar o nome, use "Renomear".
            </p>

            <div className="fg">
                {FIELDS.map(({ field, label, options }) => {
                    const empty = emptyOptionLabel(category, field)
                    const effective = category.effective[field]

                    return (
                        <div className="fl full" key={field}>
                            <label htmlFor={`classification-${field}`}>{label}</label>
                            <select
                                id={`classification-${field}`}
                                value={form[field]}
                                aria-invalid={Boolean(errors[field])}
                                onChange={(event) => setForm({ ...form, [field]: event.target.value })}
                            >
                                {empty ? <option value="">{empty}</option> : null}
                                {!empty && !form[field] ? <option value="" disabled>Escolha…</option> : null}
                                {Object.entries(options).map(([value, text]) => (
                                    <option key={value} value={value}>{text}</option>
                                ))}
                            </select>
                            <div className="help" style={{ margin: "4px 0 0", fontSize: 12 }}>
                                Efetivo agora: <b>{(options as Record<string, string>)[effective] ?? effective}</b>
                                {category[field] === null ? " (sem valor próprio)" : " (valor próprio)"}
                            </div>
                            {errors[field] ? <div className="err">{errors[field]}</div> : null}
                        </div>
                    )
                })}

                {subcategory ? (
                    <div className="fl full alert i" role="note">
                        "Do grupo" segue a classificação do grupo. Na linha da DRE, salvar "Do grupo" grava a
                        linha que o grupo tem agora (regra homologada do sistema); o resultado aparece na lista
                        após salvar.
                    </div>
                ) : null}
            </div>

            <DialogError message={error} />

            <div className="mact">
                <button type="button" className="btn sec" disabled={busy} onClick={onClose}>Cancelar</button>
                <button type="button" className="btn" disabled={busy} onClick={submit}>
                    {busy ? "Salvando…" : "Salvar classificação"}
                </button>
            </div>
        </Modal>
    )
}
