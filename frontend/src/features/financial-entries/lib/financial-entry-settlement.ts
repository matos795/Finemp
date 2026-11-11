import type { CompanyPermission } from "../../auth/types/auth.types.ts"
import type {
    FinancialDirection,
    FinancialEntry,
    FinancialEntrySettlementValues,
} from "../types/financial-entry.types.ts"
import {
    amountInputFromDecimal,
    parseNominalAmount,
} from "./financial-entry-form.ts"

/** Campos da janela de baixa (abrirBaixa do protótipo), como texto dos inputs. */
export interface SettlementForm {
    paymentDate: string
    /** Valor pago digitado, ex.: "1.500,00". */
    amount: string
    bankAccountId: string
}

export type SettlementFormErrors = Partial<Record<keyof SettlementForm, string>>

/** Rótulos do protótipo para entrada (Receber) e saída (Pagar). */
export function settlementCopy(direction: FinancialDirection) {
    return direction === "INCOME"
        ? { action: "Receber", title: "Registrar recebimento", done: "Recebimento registrado." }
        : { action: "Pagar", title: "Registrar pagamento", done: "Pagamento registrado." }
}

/**
 * "Pagar"/"Receber" só para título em aberto ou vencido (situação do
 * backend) e com FINANCIAL_ENTRY_MANAGE. Título já pago não recebe nova
 * baixa nesta tela. O papel do usuário não entra na decisão.
 */
export function canSettleFinancialEntry(
    entry: Pick<FinancialEntry, "status">,
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return (entry.status === "OPEN" || entry.status === "OVERDUE") &&
        hasPermission("FINANCIAL_ENTRY_MANAGE")
}

/** Valores iniciais: hoje, o nominal (sem retenções) e a conta do lançamento. */
export function newSettlementForm(
    entry: Pick<FinancialEntry, "values">,
    today: string,
): SettlementForm {
    return {
        paymentDate: today,
        amount: amountInputFromDecimal(entry.values.nominalAmount),
        bankAccountId: entry.values.bankAccountId,
    }
}

export interface SettlementAccountOption {
    id: string
    name: string
    inactive: boolean
}

/**
 * Contas oferecidas: as ativas e, se estiver inativa, a atual do lançamento
 * (sinalizada). Sem acesso ao catálogo de contas (`accounts` null), só a
 * conta atual, pelo rótulo que veio com o lançamento.
 */
export function settlementAccountOptions(
    entry: Pick<FinancialEntry, "values" | "labels">,
    accounts: { id: string, name: string, active: boolean }[] | null,
): SettlementAccountOption[] {
    const currentId = entry.values.bankAccountId

    if (!accounts) {
        return [{ id: currentId, name: entry.labels.bankName ?? "Conta atual", inactive: false }]
    }

    return accounts
        .filter((account) => account.active || account.id === currentId)
        .map((account) => ({ id: account.id, name: account.name, inactive: !account.active }))
}

const isoDate = /^\d{4}-\d{2}-\d{2}$/

/**
 * Conferência de UX. A data não pode ser futura: esta janela registra um
 * pagamento já feito, não um agendamento (o backend aceitaria; é uma
 * restrição desta tela). As regras financeiras continuam no backend.
 */
export function validateSettlementForm(
    form: SettlementForm,
    today: string,
): SettlementFormErrors {
    const errors: SettlementFormErrors = {}

    if (!isoDate.test(form.paymentDate)) {
        errors.paymentDate = "Informe a data do pagamento."
    } else if (form.paymentDate > today) {
        errors.paymentDate = "A data não pode ser futura: registre o pagamento quando ele acontecer."
    }

    const amount = parseNominalAmount(form.amount)

    if (!amount.ok) {
        errors.amount = amount.message
    }

    if (!form.bankAccountId) {
        errors.bankAccountId = "Escolha a conta."
    }

    return errors
}

/** "1500" e "1500.00" representam o mesmo valor: compara o texto normalizado. */
function canonicalCents(value: string) {
    const [integer, fraction = ""] = value.split(".")

    return `${integer.replace(/^0+(?=\d)/, "")}.${fraction.padEnd(2, "0")}`
}

/**
 * true quando o valor pago digitado (válido) difere do nominal. A tela
 * avisa que a baixa quita o título inteiro e que a diferença vira ajuste
 * (juros/desconto) no backend: não é pagamento parcial.
 */
export function settlementDiffersFromNominal(
    form: Pick<SettlementForm, "amount">,
    entry: Pick<FinancialEntry, "values">,
) {
    const amount = parseNominalAmount(form.amount)

    return amount.ok &&
        canonicalCents(amount.value) !== canonicalCents(entry.values.nominalAmount)
}

/** Corpo `values` da baixa: dinheiro como texto exato, nunca número JSON. */
export function toSettlementValues(
    form: SettlementForm,
): FinancialEntrySettlementValues {
    const amount = parseNominalAmount(form.amount)

    if (!amount.ok) {
        throw new Error(amount.message)
    }

    return {
        paymentDate: form.paymentDate,
        paidAmount: amount.value,
        bankAccountId: form.bankAccountId,
    }
}

/**
 * "Desfazer baixa" só para título pago (situação do backend) e com
 * FINANCIAL_ENTRY_MANAGE. É uma decisão de UX desta tela: o backend aceita
 * estornar título já aberto, e isso continua sendo regra dele.
 */
export function canReverseFinancialEntrySettlement(
    entry: Pick<FinancialEntry, "status">,
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return entry.status === "PAID" && hasPermission("FINANCIAL_ENTRY_MANAGE")
}

/** Texto da confirmação antes de desfazer a baixa (#321). */
export function reversalConfirmation(
    entry: { values: Pick<FinancialEntry["values"], "description"> },
) {
    return {
        title: `Desfazer a baixa de "${entry.values.description}"?`,
        message: "O título voltará para em aberto. A conta bancária usada na baixa será mantida no lançamento.",
        confirmLabel: "Desfazer baixa",
    }
}

/** Aviso do protótipo (estornarBaixa) depois do estorno. */
export const REVERSAL_DONE_MESSAGE = "Baixa desfeita: voltou para em aberto."
