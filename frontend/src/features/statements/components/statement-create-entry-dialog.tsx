import { useRef } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { FinancialEntryDialog } from "../../financial-entries/components/financial-entry-dialog"
import {
    nextCommandAttempt,
    type CommandAttempt,
} from "../../financial-entries/lib/command-key"
import { refreshCompanyFinancialData } from "../../financial-entries/lib/financial-entry-refresh"
import type { FinancialCategoryOption, ResultCenterOption } from "../../financial-entries/types/financial-catalog.types"
import type { FinancialEntryCommandValues } from "../../financial-entries/types/financial-entry.types"
import type { BankAccount } from "../../bank-accounts/types/bank-account.types"
import { createEntryFromStatement } from "../api/statements-api"
import { statementItemsQueryKey } from "../hooks/use-statement-items"
import {
    statementCreateValues,
    statementEntryInitialForm,
} from "../lib/statement-entry-form"
import { describeStatementResolutionError } from "../lib/statement-resolution-errors"
import type { StatementItem } from "../types/statement.types"

interface StatementCreateEntryDialogProps {
    companyId: string
    item: StatementItem
    bankAccounts: BankAccount[]
    categories: FinancialCategoryOption[]
    resultCenters: ResultCenterOption[]
    onClose: () => void
}

export function StatementCreateEntryDialog({
    companyId,
    item,
    bankAccounts,
    categories,
    resultCenters,
    onClose,
}: StatementCreateEntryDialogProps) {
    const queryClient = useQueryClient()
    const lastAttempt = useRef<CommandAttempt | null>(null)

    const create = useMutation({
        mutationFn: (values: FinancialEntryCommandValues) => {
            const requestValues = statementCreateValues(values)
            const body = JSON.stringify({
                statementId: item.id,
                expectedStatementVersion: item.version,
                values: requestValues,
            })
            const attempt = nextCommandAttempt(lastAttempt.current, body)
            lastAttempt.current = attempt

            return createEntryFromStatement(
                companyId,
                item.id,
                item.version,
                requestValues,
                attempt.key,
            )
        },
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () =>
            Promise.all([
                queryClient.invalidateQueries({
                    queryKey: statementItemsQueryKey(companyId, item.bankAccountId),
                }),
                refreshCompanyFinancialData(queryClient, companyId),
            ]),
    })

    const initialForm = statementEntryInitialForm(item)

    return (
        <FinancialEntryDialog
            companyId={companyId}
            direction={initialForm.direction}
            bankAccounts={bankAccounts}
            categories={categories}
            resultCenters={resultCenters}
            initialForm={initialForm}
            fixedSource
            titleOverride="Criar lançamento a partir do extrato"
            createOverride={{
                submit: (values) => create.mutateAsync(values),
                pending: create.isPending,
                successMessage: "Lançamento criado e conciliado.",
                describeError: describeStatementResolutionError,
            }}
            onClose={onClose}
        />
    )
}
