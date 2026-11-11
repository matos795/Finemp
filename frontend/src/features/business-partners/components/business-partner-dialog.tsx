import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { useToast } from "../../../components/finemp/toast-context"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useBusinessPartnerMutations } from "../hooks/use-business-partners"
import { describeBusinessPartnerError } from "../lib/business-partner-errors"
import {
    businessPartnerFormFrom,
    newBusinessPartnerForm,
    toBusinessPartnerProfile,
    validateBusinessPartnerForm,
    type BusinessPartnerForm,
    type BusinessPartnerFormErrors,
} from "../lib/business-partner-form"
import type {
    BusinessPartner,
    BusinessPartnerRole,
    PartnerPersonType,
} from "../types/business-partner.types"

interface BusinessPartnerDialogProps {
    companyId: string
    partner?: BusinessPartner
    onClose: () => void
}

type TextField = Exclude<keyof BusinessPartnerForm, "personType" | "role" | "active">

/** Campos opcionais do protótipo (mParc), na mesma ordem. */
const optionalFields: { field: TextField, label: string, maxLength: number, full?: boolean, inputMode?: "numeric" }[] = [
    { field: "phone", label: "Telefone", maxLength: 80 },
    { field: "email", label: "E-mail", maxLength: 255 },
    { field: "municipalRegistration", label: "Inscrição municipal", maxLength: 80 },
    { field: "stateRegistration", label: "Inscrição estadual", maxLength: 80 },
    { field: "postalCode", label: "CEP", maxLength: 20, inputMode: "numeric" },
    { field: "contactName", label: "Pessoa de contato", maxLength: 255 },
    { field: "street", label: "Logradouro", maxLength: 255, full: true },
    { field: "streetNumber", label: "Número", maxLength: 40 },
    { field: "complement", label: "Complemento", maxLength: 255 },
    { field: "district", label: "Bairro", maxLength: 150 },
    { field: "city", label: "Cidade", maxLength: 150 },
    { field: "stateCode", label: "UF", maxLength: 80 },
    { field: "municipalityCode", label: "Código IBGE do município", maxLength: 80, inputMode: "numeric" },
]

const roles: [BusinessPartnerRole, string][] = [
    ["CUSTOMER", "Cliente"],
    ["SUPPLIER", "Fornecedor"],
    ["BOTH", "Cliente e fornecedor"],
]

/**
 * "Novo cadastro" / "Editar cadastro" (abrirParc do protótipo). A consulta
 * de dados na Receita (#52) não faz parte desta tela; o backend valida o
 * CPF/CNPJ e a unicidade.
 */
export function BusinessPartnerDialog({
    companyId,
    partner,
    onClose,
}: BusinessPartnerDialogProps) {
    const queryClient = useQueryClient()
    const showToast = useToast()
    const { save } = useBusinessPartnerMutations(companyId)

    const [form, setForm] = useState<BusinessPartnerForm>(() =>
        partner ? businessPartnerFormFrom(partner) : newBusinessPartnerForm(),
    )
    const [errors, setErrors] = useState<BusinessPartnerFormErrors>({})
    const [saveError, setSaveError] = useState("")

    const individual = form.personType === "INDIVIDUAL"
    const busy = save.isPending

    function update(changes: Partial<BusinessPartnerForm>) {
        setForm((current) => ({ ...current, ...changes }))
        setErrors((current) => {
            const next = { ...current }
            Object.keys(changes).forEach((field) => delete next[field as keyof BusinessPartnerForm])
            return next
        })
    }

    async function submit() {
        setSaveError("")

        const found = validateBusinessPartnerForm(form)
        setErrors(found)

        if (Object.keys(found).length > 0) {
            return
        }

        try {
            await save.mutateAsync({ profile: toBusinessPartnerProfile(form), original: partner })
            showToast(partner ? "Cadastro atualizado." : "Cadastro salvo.")
            onClose()
        } catch (error) {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            setSaveError(describeBusinessPartnerError(error, "save"))
        }
    }

    const fieldError = (name: keyof BusinessPartnerForm) =>
        errors[name] ? <div className="err">{errors[name]}</div> : null

    return (
        <Modal
            width={680}
            labelledBy="business-partner-title"
            onClose={busy ? undefined : onClose}
        >
            <h2 id="business-partner-title">{partner ? "Editar cadastro" : "Novo cadastro"}</h2>

            <div className="fg">
                <div className="fl">
                    <label htmlFor="pPessoa">Tipo de pessoa</label>
                    <select
                        id="pPessoa"
                        value={form.personType}
                        onChange={(event) => update({ personType: event.target.value as PartnerPersonType })}
                    >
                        <option value="LEGAL_ENTITY">Pessoa jurídica (CNPJ)</option>
                        <option value="INDIVIDUAL">Pessoa física (CPF)</option>
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="pDoc">{individual ? "CPF" : "CNPJ"}</label>
                    <input
                        type="text"
                        id="pDoc"
                        autoComplete="off"
                        maxLength={64}
                        value={form.document}
                        onChange={(event) => update({ document: event.target.value })}
                    />
                    {fieldError("document")}
                </div>

                <div className="fl full">
                    <label htmlFor="pNome">{individual ? "Nome" : "Razão social"}</label>
                    <input
                        type="text"
                        id="pNome"
                        maxLength={255}
                        value={form.name}
                        onChange={(event) => update({ name: event.target.value })}
                    />
                    {fieldError("name")}
                </div>

                {individual ? null : (
                    <div className="fl full">
                        <label htmlFor="pFant">Nome fantasia</label>
                        <input
                            type="text"
                            id="pFant"
                            maxLength={255}
                            value={form.tradeName}
                            onChange={(event) => update({ tradeName: event.target.value })}
                        />
                    </div>
                )}

                <div className="fl full">
                    <label>Papel</label>
                    <div className="radios">
                        {roles.map(([value, label]) => (
                            <label key={value}>
                                <input
                                    type="radio"
                                    name="pPapel"
                                    value={value}
                                    checked={form.role === value}
                                    onChange={() => update({ role: value })}
                                />{" "}
                                {label}
                            </label>
                        ))}
                    </div>
                    {fieldError("role")}
                </div>

                {optionalFields.map(({ field, label, maxLength, full, inputMode }) => (
                    <div
                        key={field}
                        className={full ? "fl full" : "fl"}
                    >
                        <label htmlFor={`p-${field}`}>{label}</label>
                        <input
                            type="text"
                            id={`p-${field}`}
                            maxLength={maxLength}
                            inputMode={inputMode}
                            value={form[field]}
                            onChange={(event) => update({ [field]: event.target.value })}
                        />
                    </div>
                ))}

                <div className="fl full">
                    <label htmlFor="pObs">Observação</label>
                    <textarea
                        id="pObs"
                        rows={2}
                        maxLength={4000}
                        value={form.notes}
                        onChange={(event) => update({ notes: event.target.value })}
                    />
                </div>

                <div className="fl full">
                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={form.active}
                            onChange={(event) => update({ active: event.target.checked })}
                        />{" "}
                        Ativo
                    </label>
                </div>
            </div>

            {saveError ? (
                <div
                    className="err"
                    role="alert"
                    style={{ marginTop: 10 }}
                >
                    {saveError}
                </div>
            ) : null}

            <div className="mact">
                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={onClose}
                >
                    Cancelar
                </button>
                <button
                    type="button"
                    className="btn"
                    disabled={busy}
                    onClick={submit}
                >
                    {busy ? "Salvando…" : "Salvar cadastro"}
                </button>
            </div>
        </Modal>
    )
}
