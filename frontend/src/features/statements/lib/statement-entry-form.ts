import {
    amountInputFromDecimal,
    monthOf,
    newFinancialEntryForm,
    type FinancialEntryForm,
} from "../../financial-entries/lib/financial-entry-form"
import type { FinancialEntryCommandValues } from "../../financial-entries/types/financial-entry.types"
import type {
    StatementCreateEntryValues,
    StatementItem,
} from "../types/statement.types"

function absoluteDecimal(value: string) {
    const trimmed = value.trim()
    return trimmed.startsWith("-") ? trimmed.slice(1) : trimmed
}

export function statementEntryInitialForm(
    item: StatementItem,
): FinancialEntryForm {
    const date = item.values.postedDate
    const direction = item.values.signedAmount.trim().startsWith("-")
        ? "EXPENSE"
        : "INCOME"

    return {
        ...newFinancialEntryForm(direction, date, item.bankAccountId),
        description: item.values.memo.trim().slice(0, 255) || "Movimento bancário",
        amount: amountInputFromDecimal(absoluteDecimal(item.values.signedAmount)),
        documentDate: date,
        entryDate: date,
        competence: monthOf(date),
        dueDate: date,
        paid: true,
        paymentDate: date,
        documentNumber: item.values.documentReference ?? "",
    }
}

export function statementCreateValues(
    values: FinancialEntryCommandValues,
): StatementCreateEntryValues {
    return {
        description: values.description,
        documentDate: values.documentDate,
        entryDate: values.entryDate,
        competence: values.competence,
        dueDate: values.dueDate,
        categoryId: values.categoryId,
        partnerId: values.partnerId,
        resultCenterId: values.resultCenterId,
        costBehaviorOverride: values.costBehaviorOverride,
        paymentMethod: values.paymentMethod,
        documentNumber: values.documentNumber,
        notes: values.notes,
    }
}
