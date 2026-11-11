import type {
    BusinessPartner,
    BusinessPartnerProfile,
    BusinessPartnerRole,
    PartnerPersonType,
} from "../types/business-partner.types.ts"

/** Campos do formulário "Novo cadastro" do protótipo (abrirParc), como texto. */
export interface BusinessPartnerForm {
    personType: PartnerPersonType
    document: string
    name: string
    tradeName: string
    role: BusinessPartnerRole | ""
    active: boolean
    phone: string
    email: string
    contactName: string
    city: string
    stateCode: string
    postalCode: string
    street: string
    streetNumber: string
    complement: string
    district: string
    municipalityCode: string
    stateRegistration: string
    municipalRegistration: string
    notes: string
}

export type BusinessPartnerFormErrors = Partial<Record<keyof BusinessPartnerForm, string>>

const textFields = [
    "phone", "email", "contactName", "city", "stateCode", "postalCode", "street",
    "streetNumber", "complement", "district", "municipalityCode", "stateRegistration",
    "municipalRegistration", "notes",
] as const

/** Padrão do protótipo: pessoa jurídica, ativo, papel a escolher. */
export function newBusinessPartnerForm(): BusinessPartnerForm {
    return {
        personType: "LEGAL_ENTITY",
        document: "",
        name: "",
        tradeName: "",
        role: "",
        active: true,
        phone: "",
        email: "",
        contactName: "",
        city: "",
        stateCode: "",
        postalCode: "",
        street: "",
        streetNumber: "",
        complement: "",
        district: "",
        municipalityCode: "",
        stateRegistration: "",
        municipalRegistration: "",
        notes: "",
    }
}

export function businessPartnerFormFrom(partner: BusinessPartner): BusinessPartnerForm {
    const profile = partner.profile
    const form: BusinessPartnerForm = {
        ...newBusinessPartnerForm(),
        personType: profile.personType,
        document: profile.document,
        name: profile.name,
        tradeName: profile.tradeName ?? "",
        role: profile.role,
        active: profile.active,
    }

    for (const field of textFields) {
        form[field] = profile[field] ?? ""
    }

    return form
}

/**
 * Conferência de UX: só obrigatórios. CPF/CNPJ (dígitos, CNPJ alfanumérico),
 * unicidade, e-mail, UF e código municipal são validados pelo backend.
 */
export function validateBusinessPartnerForm(
    form: BusinessPartnerForm,
): BusinessPartnerFormErrors {
    const errors: BusinessPartnerFormErrors = {}

    if (!form.document.trim()) {
        errors.document = form.personType === "INDIVIDUAL" ? "Informe o CPF." : "Informe o CNPJ."
    }

    if (form.name.trim().length < 2) {
        errors.name = "Informe o nome (pelo menos 2 caracteres)."
    }

    if (!form.role) {
        errors.role = "Escolha o papel."
    }

    return errors
}

function optional(value: string) {
    const text = value.trim()

    return text ? text : null
}

/** Corpo `profile` enviado ao backend (substituição integral). */
export function toBusinessPartnerProfile(
    form: BusinessPartnerForm,
): BusinessPartnerProfile {
    if (!form.role) {
        throw new Error("Escolha o papel.")
    }

    const profile: BusinessPartnerProfile = {
        personType: form.personType,
        document: form.document.trim(),
        name: form.name.trim(),
        tradeName: form.personType === "INDIVIDUAL" ? null : optional(form.tradeName),
        role: form.role,
        active: form.active,
        phone: null,
        email: null,
        contactName: null,
        city: null,
        stateCode: null,
        postalCode: null,
        street: null,
        streetNumber: null,
        complement: null,
        district: null,
        municipalityCode: null,
        stateRegistration: null,
        municipalRegistration: null,
        notes: null,
    }

    for (const field of textFields) {
        profile[field] = optional(form[field])
    }

    return profile
}

/** Exibição do documento normalizado pelo backend (11 ou 14 caracteres). */
export function formatPartnerDocument(document: string) {
    if (/^\d{11}$/.test(document)) {
        return `${document.slice(0, 3)}.${document.slice(3, 6)}.${document.slice(6, 9)}-${document.slice(9)}`
    }

    if (/^[0-9A-Z]{12}\d{2}$/.test(document)) {
        return `${document.slice(0, 2)}.${document.slice(2, 5)}.${document.slice(5, 8)}/${document.slice(8, 12)}-${document.slice(12)}`
    }

    return document
}

export const PARTNER_ROLE_LABELS: Record<BusinessPartnerRole, string> = {
    CUSTOMER: "Cliente",
    SUPPLIER: "Fornecedor",
    BOTH: "Cliente e fornecedor",
}
