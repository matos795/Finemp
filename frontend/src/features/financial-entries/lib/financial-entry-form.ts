import type {
    CostBehavior,
    FinancialDirection,
    FinancialEntry,
    FinancialEntryCommandValues,
} from "../types/financial-entry.types.ts"

/**
 * Estado do formulário "Nova entrada / Nova saída / Editar lançamento"
 * (abrirLanc/salvarLanc do protótipo). Todos os campos são texto, como
 * nos inputs; a conversão para o contrato fica em `toCommandValues`.
 */
export interface FinancialEntryForm {
    direction: FinancialDirection
    description: string
    /** Texto digitado pelo usuário, ex.: "1.234,56". */
    amount: string
    documentDate: string
    entryDate: string
    competence: string
    /** true quando o usuário mudou a competência à mão. */
    competenceEdited: boolean
    dueDate: string
    bankAccountId: string
    categoryId: string
    partnerId: string
    resultCenterId: string
    costBehaviorOverride: CostBehavior | ""
    paymentMethod: string
    documentNumber: string
    notes: string
    /** Só na criação: "Já foi pago / Já foi recebido". */
    paid: boolean
    paymentDate: string
}

export type FinancialEntryFormErrors =
    Partial<Record<keyof FinancialEntryForm, string>>

/** Mês (AAAA-MM) de uma data ISO, sem usar Date (evita fuso). */
export function monthOf(isoDate: string) {
    return /^\d{4}-\d{2}-\d{2}$/.test(isoDate) ? isoDate.slice(0, 7) : ""
}

/** Formulário vazio do protótipo: datas de hoje, competência do documento. */
export function newFinancialEntryForm(
    direction: FinancialDirection,
    today: string,
    defaultBankAccountId = "",
): FinancialEntryForm {
    return {
        direction,
        description: "",
        amount: "",
        documentDate: today,
        entryDate: today,
        competence: monthOf(today),
        competenceEdited: false,
        dueDate: today,
        bankAccountId: defaultBankAccountId,
        categoryId: "",
        partnerId: "",
        resultCenterId: "",
        costBehaviorOverride: "",
        paymentMethod: "",
        documentNumber: "",
        notes: "",
        paid: false,
        paymentDate: today,
    }
}

/** Exibe o texto decimal exato da API no padrão do campo ("1234.5" → "1234,50"). */
export function amountInputFromDecimal(value: string) {
    const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(value.trim())

    if (!match) {
        return value
    }

    return `${match[1]},${(match[2] ?? "").padEnd(2, "0")}`
}

/** Formulário de edição a partir do lançamento lido. */
export function financialEntryFormFrom(
    entry: FinancialEntry,
): FinancialEntryForm {
    const values = entry.values

    return {
        direction: values.direction,
        description: values.description,
        amount: amountInputFromDecimal(values.nominalAmount),
        documentDate: values.documentDate,
        entryDate: values.entryDate,
        competence: values.competence,
        competenceEdited: values.competence !== monthOf(values.documentDate),
        dueDate: values.dueDate,
        bankAccountId: values.bankAccountId,
        categoryId: values.categoryId,
        partnerId: values.partnerId ?? "",
        resultCenterId: values.resultCenterId ?? "",
        costBehaviorOverride: values.costBehaviorOverride ?? "",
        paymentMethod: values.paymentMethod ?? "",
        documentNumber: values.documentNumber ?? "",
        notes: values.notes ?? "",
        paid: values.paymentDate !== null,
        paymentDate: values.paymentDate ?? "",
    }
}

/** Muda a data do documento e, se o usuário não mexeu nela, a competência. */
export function withDocumentDate(
    form: FinancialEntryForm,
    documentDate: string,
): FinancialEntryForm {
    return {
        ...form,
        documentDate,
        competence: form.competenceEdited ? form.competence : monthOf(documentDate),
    }
}

export type ParsedAmount =
    | { ok: true, value: string }
    | { ok: false, message: string }

/**
 * Converte o valor digitado ("1.234,56", "R$ 10", "99.9") no texto
 * decimal positivo exato do contrato ("1234.56"), sem converter para
 * Number. Não arredonda: mais de duas casas é recusado no campo.
 */
export function parseNominalAmount(
    input: string,
): ParsedAmount {
    let text = input
        .trim()
        .replace(/R\$\s?/, "")
        .replace(/\s/g, "")

    if (text.includes(",")) {
        text = text.replace(/\./g, "").replace(",", ".")
    } else if (/^\d{1,3}(\.\d{3})+$/.test(text)) {
        text = text.replace(/\./g, "")
    }

    const match = /^(\d+)(?:\.(\d+))?$/.exec(text)

    if (!match) {
        return { ok: false, message: "Valor inválido. Use 1.234,56." }
    }

    const [, rawInteger, fraction = ""] = match

    if (fraction.length > 2) {
        return { ok: false, message: "Use no máximo duas casas decimais." }
    }

    const integer = rawInteger.replace(/^0+(?=\d)/, "")

    if (integer.length > 17) {
        return { ok: false, message: "Valor muito alto." }
    }

    if (/^0*$/.test(integer + fraction)) {
        return { ok: false, message: "Informe um valor maior que zero." }
    }

    return {
        ok: true,
        value: fraction ? `${integer}.${fraction}` : integer,
    }
}

const isoDate = /^\d{4}-\d{2}-\d{2}$/

/**
 * Conferência de UX antes de enviar (campos obrigatórios e formatos).
 * As regras financeiras continuam no backend, que responde 400/409.
 */
export function validateFinancialEntryForm(
    form: FinancialEntryForm,
    options: { requireResultCenter: boolean, editing: boolean },
): FinancialEntryFormErrors {
    const errors: FinancialEntryFormErrors = {}
    const description = form.description.trim()

    if (description.length < 2) {
        errors.description = "Informe uma descrição."
    } else if (description.length > 255) {
        errors.description = "Use no máximo 255 caracteres."
    }

    const amount = parseNominalAmount(form.amount)

    if (!amount.ok) {
        errors.amount = amount.message
    }

    if (!form.categoryId) {
        errors.categoryId = "Escolha a categoria."
    }

    if (!form.bankAccountId) {
        errors.bankAccountId = "Escolha a conta."
    }

    if (!isoDate.test(form.dueDate)) {
        errors.dueDate = "Informe o vencimento."
    }

    if (!isoDate.test(form.documentDate)) {
        errors.documentDate = "Informe a data do documento."
    }

    if (!isoDate.test(form.entryDate)) {
        errors.entryDate = "Informe a data de lançamento."
    }

    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(form.competence)) {
        errors.competence = "Informe o mês de competência."
    }

    if (options.requireResultCenter && !options.editing && !form.resultCenterId) {
        errors.resultCenterId = "Escolha o centro de resultado (exigido nesta empresa)."
    }

    if (!options.editing && form.paid && !isoDate.test(form.paymentDate)) {
        errors.paymentDate = "Informe a data do pagamento."
    }

    if (form.paymentMethod.length > 80) {
        errors.paymentMethod = "Use no máximo 80 caracteres."
    }

    if (form.documentNumber.length > 255) {
        errors.documentNumber = "Use no máximo 255 caracteres."
    }

    if (form.notes.length > 4000) {
        errors.notes = "Use no máximo 4000 caracteres."
    }

    return errors
}

function optionalText(value: string) {
    const text = value.trim()

    return text ? text : null
}

/**
 * Corpo `values` de POST/PUT. Dinheiro vai como string exata; opcionais
 * vazios vão como null.
 *
 * Na edição, a data de pagamento é sempre a lida do backend
 * (`original.values.paymentDate`): baixa e estorno têm comandos próprios
 * e não fazem parte deste formulário. O valor pago (`actualPaidAmount`)
 * não é enviado; o backend o preserva enquanto o título continuar pago.
 */
export function toCommandValues(
    form: FinancialEntryForm,
    original?: FinancialEntry,
): FinancialEntryCommandValues {
    const amount = parseNominalAmount(form.amount)

    if (!amount.ok) {
        throw new Error(amount.message)
    }

    const paymentDate = original
        ? original.values.paymentDate
        : form.paid ? form.paymentDate : null

    return {
        direction: original ? original.values.direction : form.direction,
        description: form.description.trim(),
        nominalAmount: amount.value,
        documentDate: form.documentDate,
        entryDate: form.entryDate,
        competence: form.competence,
        dueDate: form.dueDate,
        paymentDate,
        bankAccountId: form.bankAccountId,
        categoryId: form.categoryId,
        partnerId: form.partnerId || null,
        resultCenterId: form.resultCenterId || null,
        costBehaviorOverride: form.costBehaviorOverride || null,
        paymentMethod: optionalText(form.paymentMethod),
        documentNumber: optionalText(form.documentNumber),
        notes: optionalText(form.notes),
    }
}
