import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { useToast } from "../../../components/finemp/toast-context"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { formatIsoDate } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import type { BankAccount } from "../../bank-accounts/types/bank-account.types"
import {
    useEntryPartners,
    useResultCenterSettings,
} from "../hooks/use-financial-entry-catalogs"
import { useSaveFinancialEntry } from "../hooks/use-save-financial-entry"
import { describeFinancialEntryError } from "../lib/financial-entry-errors"
import {
    financialEntryFormFrom,
    newFinancialEntryForm,
    toCommandValues,
    validateFinancialEntryForm,
    withDocumentDate,
    type FinancialEntryForm,
    type FinancialEntryFormErrors,
} from "../lib/financial-entry-form"
import type {
    FinancialCategoryOption,
    ResultCenterOption,
} from "../types/financial-catalog.types"
import type {
    FinancialDirection,
    FinancialEntry,
} from "../types/financial-entry.types"

const PAYMENT_METHODS = [
    "Pix",
    "Boleto",
    "Transferência",
    "Cartão de crédito",
    "Cartão de débito",
    "Dinheiro",
    "Débito automático",
    "Cheque",
]

interface FinancialEntryDialogProps {
    companyId: string
    /** Lançamento em edição; ausente cria um novo com `direction`. */
    entry?: FinancialEntry
    direction: FinancialDirection
    bankAccounts: BankAccount[]
    categories: FinancialCategoryOption[]
    resultCenters: ResultCenterOption[]
    onClose: () => void
}

/**
 * Janela "Nova entrada / Nova saída / Editar lançamento" do protótipo
 * (abrirLanc/salvarLanc). Repetir, parcelar, retenções, itens de estoque
 * e comprovantes ficam para entregas próprias.
 */
export function FinancialEntryDialog({
    companyId,
    entry,
    direction,
    bankAccounts,
    categories,
    resultCenters,
    onClose,
}: FinancialEntryDialogProps) {
    const editing = Boolean(entry)
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [form, setForm] = useState<FinancialEntryForm>(() =>
        entry
            ? financialEntryFormFrom(entry)
            : newFinancialEntryForm(
                direction,
                todayIso(),
                bankAccounts.find((account) => account.active)?.id ?? "",
            ),
    )
    const [errors, setErrors] = useState<FinancialEntryFormErrors>({})
    const [saveError, setSaveError] = useState("")

    const partners = useEntryPartners(companyId, form.direction, true)
    const settings = useResultCenterSettings(companyId, !editing)
    const save = useSaveFinancialEntry(companyId)

    const isIncome = form.direction === "INCOME"
    const busy = save.isPending

    function clearErrors(fields: (keyof FinancialEntryForm)[]) {
        setErrors((current) => {
            const next = { ...current }
            fields.forEach((field) => delete next[field])
            return next
        })
    }

    function update(changes: Partial<FinancialEntryForm>) {
        setForm((current) => ({ ...current, ...changes }))
        clearErrors(Object.keys(changes) as (keyof FinancialEntryForm)[])
    }

    function changeDirection(next: FinancialDirection) {
        if (editing || next === form.direction) {
            return
        }

        // Categoria e parceiro dependem do tipo: limpa para escolher de novo.
        update({ direction: next, categoryId: "", partnerId: "" })
    }

    async function submit() {
        setSaveError("")

        const found = validateFinancialEntryForm(form, {
            requireResultCenter: settings.data?.requireResultCenter ?? false,
            editing,
        })

        setErrors(found)

        if (Object.keys(found).length > 0) {
            return
        }

        try {
            await save.mutateAsync({
                values: toCommandValues(form, entry),
                original: entry,
            })

            showToast(editing ? "Lançamento atualizado." : "Lançamento salvo.")
            onClose()
        } catch (error) {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            setSaveError(describeFinancialEntryError(error, editing ? "update" : "create"))
        }
    }

    const groups = categories.filter((category) => category.kind === "GROUP")
    const subcategories = categories.filter(
        (category) => category.kind === "SUBCATEGORY" && category.direction === form.direction,
    )

    const accountOptions = bankAccounts.filter(
        (account) => account.active || account.id === form.bankAccountId,
    )

    const centerOptions = resultCenters.filter(
        (center) =>
            center.type === "ANALYTICAL" &&
            (center.active || center.id === form.resultCenterId),
    )

    const partnerOptions = partners.data ?? []
    const currentPartnerMissing =
        Boolean(form.partnerId) &&
        partners.isSuccess &&
        !partnerOptions.some((partner) => partner.id === form.partnerId)

    const title = editing
        ? "Editar lançamento"
        : isIncome ? "Nova entrada" : "Nova saída"

    const fieldError = (name: keyof FinancialEntryForm) =>
        errors[name] ? <div className="err">{errors[name]}</div> : null

    return (
        <Modal
            labelledBy="financial-entry-title"
            onClose={busy ? undefined : onClose}
        >
            <h2 id="financial-entry-title">{title}</h2>

            <div
                className="seg"
                style={{ marginBottom: 12 }}
            >
                <button
                    type="button"
                    className={isIncome ? "e on" : "e"}
                    disabled={editing}
                    onClick={() => changeDirection("INCOME")}
                >
                    Entrada
                </button>
                <button
                    type="button"
                    className={isIncome ? "s" : "s on"}
                    disabled={editing}
                    onClick={() => changeDirection("EXPENSE")}
                >
                    Saída
                </button>
            </div>

            <div className="fg">
                <div className="fl full">
                    <label htmlFor="lParc">{isIncome ? "Cliente" : "Fornecedor"}</label>
                    <select
                        id="lParc"
                        value={form.partnerId}
                        onChange={(event) => update({ partnerId: event.target.value })}
                    >
                        <option value="">Sem {isIncome ? "cliente" : "fornecedor"}</option>
                        {currentPartnerMissing && entry ? (
                            <option value={form.partnerId}>
                                {entry.labels.partnerName ?? "Cadastro atual"}
                            </option>
                        ) : null}
                        {partnerOptions.map((partner) => (
                            <option
                                key={partner.id}
                                value={partner.id}
                            >
                                {partner.profile.tradeName || partner.profile.name}
                            </option>
                        ))}
                    </select>
                    <div className="accinfo mut">
                        {partners.isError
                            ? "Seu acesso não inclui clientes e fornecedores desta empresa; o lançamento pode ser salvo sem eles."
                            : "Opcional."}
                    </div>
                </div>

                <div className="fl full">
                    <label htmlFor="lDesc">Descrição</label>
                    <input
                        type="text"
                        id="lDesc"
                        maxLength={255}
                        placeholder="Ex.: Venda NF 1234, Aluguel de março"
                        value={form.description}
                        onChange={(event) => update({ description: event.target.value })}
                    />
                    {fieldError("description")}
                </div>

                <div className="fl">
                    <label htmlFor="lValor">Valor (R$)</label>
                    <input
                        type="text"
                        id="lValor"
                        inputMode="decimal"
                        placeholder="1.234,56"
                        value={form.amount}
                        onChange={(event) => update({ amount: event.target.value })}
                    />
                    {fieldError("amount")}
                </div>

                <div className="fl">
                    <label htmlFor="lCat">Categoria</label>
                    <select
                        id="lCat"
                        value={form.categoryId}
                        onChange={(event) => update({ categoryId: event.target.value })}
                    >
                        <option value="">Selecione…</option>
                        {groups
                            .filter((group) => group.direction === form.direction)
                            .map((group) => (
                                <optgroup
                                    key={group.id}
                                    label={`${group.code} ${group.name}`}
                                >
                                    {subcategories
                                        .filter((category) => category.parentId === group.id)
                                        .map((category) => (
                                            <option
                                                key={category.id}
                                                value={category.id}
                                            >
                                                {category.code} {category.name}
                                            </option>
                                        ))}
                                </optgroup>
                            ))}
                    </select>
                    {fieldError("categoryId")}
                </div>

                <div className="fl">
                    <label htmlFor="lCV">Fixo ou variável</label>
                    <select
                        id="lCV"
                        value={form.costBehaviorOverride}
                        onChange={(event) =>
                            update({ costBehaviorOverride: event.target.value as FinancialEntryForm["costBehaviorOverride"] })
                        }
                    >
                        <option value="">Conforme a categoria</option>
                        <option value="FIXED">Fixo</option>
                        <option value="VARIABLE">Variável</option>
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="lDtDoc">Data do documento</label>
                    <input
                        type="date"
                        id="lDtDoc"
                        value={form.documentDate}
                        onChange={(event) => {
                            const documentDate = event.target.value
                            setForm((current) => withDocumentDate(current, documentDate))
                            clearErrors(["documentDate", "competence"])
                        }}
                    />
                    {fieldError("documentDate")}
                </div>

                <div className="fl">
                    <label htmlFor="lVenc">Vencimento</label>
                    <input
                        type="date"
                        id="lVenc"
                        value={form.dueDate}
                        onChange={(event) => update({ dueDate: event.target.value })}
                    />
                    {fieldError("dueDate")}
                </div>

                <div className="fl">
                    <label htmlFor="lComp">Competência (mês da DRE)</label>
                    <input
                        type="month"
                        id="lComp"
                        value={form.competence}
                        onChange={(event) => update({ competence: event.target.value, competenceEdited: true })}
                    />
                    {fieldError("competence")}
                </div>

                <div className="fl">
                    <label htmlFor="lDtLanc">Data de lançamento</label>
                    <input
                        type="date"
                        id="lDtLanc"
                        value={form.entryDate}
                        onChange={(event) => update({ entryDate: event.target.value })}
                    />
                    {fieldError("entryDate")}
                </div>

                <div className="fl">
                    <label htmlFor="lConta">Conta / banco</label>
                    <select
                        id="lConta"
                        value={form.bankAccountId}
                        onChange={(event) => update({ bankAccountId: event.target.value })}
                    >
                        <option value="">Selecione…</option>
                        {accountOptions.map((account) => (
                            <option
                                key={account.id}
                                value={account.id}
                            >
                                {account.name}
                            </option>
                        ))}
                    </select>
                    {fieldError("bankAccountId")}
                </div>

                <div className="fl">
                    <label htmlFor="lCR">Centro de resultado</label>
                    <select
                        id="lCR"
                        value={form.resultCenterId}
                        onChange={(event) => update({ resultCenterId: event.target.value })}
                    >
                        <option value="">
                            {settings.data?.requireResultCenter && !editing ? "Selecione…" : "Sem centro de resultado"}
                        </option>
                        {centerOptions.map((center) => (
                            <option
                                key={center.id}
                                value={center.id}
                            >
                                {center.code} {center.name}
                            </option>
                        ))}
                    </select>
                    {fieldError("resultCenterId")}
                </div>

                {editing ? (
                    <div className="fl full mut">
                        {entry?.values.paymentDate
                            ? `${isIncome ? "Recebido" : "Pago"} em ${formatIsoDate(entry.values.paymentDate)}. A baixa não é alterada por esta janela.`
                            : "Em aberto. A baixa não é feita por esta janela."}
                    </div>
                ) : (
                    <>
                        <div className="fl full">
                            <label className="chk">
                                <input
                                    type="checkbox"
                                    checked={form.paid}
                                    onChange={(event) => update({ paid: event.target.checked })}
                                />{" "}
                                {isIncome ? "Já foi recebido" : "Já foi pago"}
                            </label>
                        </div>

                        {form.paid ? (
                            <div className="fl">
                                <label htmlFor="lPgto">Data do pagamento</label>
                                <input
                                    type="date"
                                    id="lPgto"
                                    value={form.paymentDate}
                                    onChange={(event) => update({ paymentDate: event.target.value })}
                                />
                                {fieldError("paymentDate")}
                            </div>
                        ) : null}
                    </>
                )}

                <div className="fl">
                    <label htmlFor="lForma">Forma de pagamento</label>
                    <select
                        id="lForma"
                        value={form.paymentMethod}
                        onChange={(event) => update({ paymentMethod: event.target.value })}
                    >
                        <option value="" />
                        {form.paymentMethod && !PAYMENT_METHODS.includes(form.paymentMethod) ? (
                            <option value={form.paymentMethod}>{form.paymentMethod}</option>
                        ) : null}
                        {PAYMENT_METHODS.map((method) => (
                            <option key={method}>{method}</option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="lDoc">Documento / NF</label>
                    <input
                        type="text"
                        id="lDoc"
                        maxLength={255}
                        value={form.documentNumber}
                        onChange={(event) => update({ documentNumber: event.target.value })}
                    />
                    {fieldError("documentNumber")}
                </div>

                <div className="fl full">
                    <label htmlFor="lObs">Observação</label>
                    <textarea
                        id="lObs"
                        rows={2}
                        maxLength={4000}
                        value={form.notes}
                        onChange={(event) => update({ notes: event.target.value })}
                    />
                    {fieldError("notes")}
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
                    {busy ? "Salvando…" : "Salvar"}
                </button>
            </div>
        </Modal>
    )
}
