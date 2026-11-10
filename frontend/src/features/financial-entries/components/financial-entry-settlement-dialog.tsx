import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { useToast } from "../../../components/finemp/toast-context"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import {
    formatExactCurrency,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import type { BankAccount } from "../../bank-accounts/types/bank-account.types"
import { useSettleFinancialEntry } from "../hooks/use-settle-financial-entry"
import { describeFinancialEntryError } from "../lib/financial-entry-errors"
import {
    newSettlementForm,
    settlementAccountOptions,
    settlementCopy,
    settlementDiffersFromNominal,
    toSettlementValues,
    validateSettlementForm,
    type SettlementForm,
    type SettlementFormErrors,
} from "../lib/financial-entry-settlement"
import type { FinancialEntry } from "../types/financial-entry.types"

interface FinancialEntrySettlementDialogProps {
    companyId: string
    entry: FinancialEntry
    /** Catálogo de contas; null quando o usuário não tem acesso a ele. */
    bankAccounts: BankAccount[] | null
    onClose: () => void
}

/**
 * "Registrar pagamento / Registrar recebimento" (abrirBaixa/confirmarBaixa
 * do protótipo): data, valor pago e conta. O backend aplica a baixa.
 * Comprovante, estorno e baixa em lote ficam para entregas próprias.
 */
export function FinancialEntrySettlementDialog({
    companyId,
    entry,
    bankAccounts,
    onClose,
}: FinancialEntrySettlementDialogProps) {
    const queryClient = useQueryClient()
    const showToast = useToast()
    const settle = useSettleFinancialEntry(companyId)

    const today = todayIso()
    const copy = settlementCopy(entry.values.direction)

    const [form, setForm] = useState<SettlementForm>(() => newSettlementForm(entry, today))
    const [errors, setErrors] = useState<SettlementFormErrors>({})
    const [saveError, setSaveError] = useState("")

    const busy = settle.isPending
    const accounts = settlementAccountOptions(entry, bankAccounts)
    const differs = settlementDiffersFromNominal(form, entry)

    function update(changes: Partial<SettlementForm>) {
        setForm((current) => ({ ...current, ...changes }))
        setErrors((current) => {
            const next = { ...current }
            Object.keys(changes).forEach((field) => delete next[field as keyof SettlementForm])
            return next
        })
    }

    async function submit() {
        setSaveError("")

        const found = validateSettlementForm(form, todayIso())
        setErrors(found)

        if (Object.keys(found).length > 0) {
            return
        }

        try {
            await settle.mutateAsync({ entry, values: toSettlementValues(form) })
            showToast(copy.done)
            onClose()
        } catch (error) {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            setSaveError(describeFinancialEntryError(error, "settle"))
        }
    }

    const fieldError = (name: keyof SettlementForm) =>
        errors[name] ? <div className="err">{errors[name]}</div> : null

    return (
        <Modal
            width={460}
            labelledBy="financial-entry-settlement-title"
            onClose={busy ? undefined : onClose}
        >
            <h2 id="financial-entry-settlement-title">{copy.title}</h2>
            <p className="help">
                {entry.values.description} · vencimento {formatIsoDate(entry.values.dueDate)} · {formatExactCurrency(entry.values.nominalAmount)}
            </p>

            <div className="fg">
                <div className="fl">
                    <label htmlFor="bData">Data do pagamento</label>
                    <input
                        type="date"
                        id="bData"
                        max={today}
                        value={form.paymentDate}
                        onChange={(event) => update({ paymentDate: event.target.value })}
                    />
                    {fieldError("paymentDate")}
                </div>

                <div className="fl">
                    <label htmlFor="bValor">Valor pago (R$)</label>
                    <input
                        type="text"
                        id="bValor"
                        inputMode="decimal"
                        value={form.amount}
                        onChange={(event) => update({ amount: event.target.value })}
                    />
                    {fieldError("amount")}
                </div>

                <div className="fl full">
                    <label htmlFor="bConta">Conta</label>
                    <select
                        id="bConta"
                        value={form.bankAccountId}
                        onChange={(event) => update({ bankAccountId: event.target.value })}
                    >
                        {accounts.map((account) => (
                            <option
                                key={account.id}
                                value={account.id}
                            >
                                {account.inactive ? `${account.name} (inativa)` : account.name}
                            </option>
                        ))}
                    </select>
                    {fieldError("bankAccountId")}
                </div>

                {differs ? (
                    <div
                        className="fl full alert w"
                        role="status"
                    >
                        O valor pago é diferente do valor do título. A baixa quita o título
                        inteiro: a diferença será registrada como ajuste financeiro (juros ou
                        desconto). Não é pagamento parcial e não fica saldo em aberto.
                    </div>
                ) : null}
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
                    className="btn ok"
                    disabled={busy}
                    onClick={submit}
                >
                    {busy ? "Registrando…" : "Confirmar baixa"}
                </button>
            </div>
        </Modal>
    )
}
