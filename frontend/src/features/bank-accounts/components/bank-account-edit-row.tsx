import { formatExactDecimal } from "../../../lib/formatters/decimal"
import {
    bankAccountTypes,
    getBankAccountTypeLabel,
} from "../lib/bank-account-type-labels"
import { parseOpeningBalance } from "../lib/parse-opening-balance"
import type {
    BankAccount,
    BankAccountType,
    BankAccountWrite,
} from "../types/bank-account.types"

interface BankAccountEditRowProps {
    account: BankAccount
    /** Escrita desta conta em andamento: campos bloqueados. */
    busy: boolean
    onChange: (changes: Partial<BankAccountWrite>) => void
    onInvalid: (message: string) => void
    onDelete: () => void
}

/**
 * Linha editável de cadContas (protótipo homologado): cada campo é salvo
 * ao sair dele ou ao mudar, como no protótipo; aqui cada mudança vira um
 * PUT com a versão atual da conta.
 */
export function BankAccountEditRow({
    account,
    busy,
    onChange,
    onInvalid,
    onDelete,
}: BankAccountEditRowProps) {
    const isKnownType =
        (bankAccountTypes as readonly string[]).includes(account.type)

    return (
        <tr aria-busy={busy}>
            <td>
                <input
                    type="text"
                    defaultValue={account.name}
                    maxLength={255}
                    style={{ width: 150 }}
                    aria-label="Nome da conta"
                    disabled={busy}
                    onBlur={(event) => {
                        const name = event.target.value.trim()

                        // Como no protótipo: nome vazio mantém o anterior.
                        if (!name) {
                            event.target.value = account.name
                            return
                        }

                        if (name !== account.name) {
                            onChange({ name })
                        }
                    }}
                />
            </td>

            <td>
                <select
                    value={account.type}
                    aria-label="Tipo"
                    disabled={busy}
                    onChange={(event) =>
                        onChange({
                            type: event.target.value as BankAccountType,
                        })
                    }
                >
                    {!isKnownType && (
                        <option value={account.type}>
                            {getBankAccountTypeLabel(account.type)}
                        </option>
                    )}

                    {bankAccountTypes.map((type) => (
                        <option
                            key={type}
                            value={type}
                        >
                            {getBankAccountTypeLabel(type)}
                        </option>
                    ))}
                </select>
            </td>

            <td>
                <input
                    type="text"
                    inputMode="decimal"
                    defaultValue={formatExactDecimal(account.openingBalance)}
                    style={{ width: 120, textAlign: "right" }}
                    aria-label="Saldo inicial"
                    disabled={busy}
                    onBlur={(event) => {
                        const original =
                            formatExactDecimal(account.openingBalance)

                        if (event.target.value === original) {
                            return
                        }

                        const parsed = parseOpeningBalance(event.target.value)

                        if (!parsed.ok) {
                            event.target.value = original
                            onInvalid(parsed.message)
                            return
                        }

                        onChange({ openingBalance: parsed.value })
                    }}
                />
            </td>

            <td>
                <input
                    type="date"
                    defaultValue={account.openingDate}
                    aria-label="Data de início"
                    disabled={busy}
                    // Ao sair do campo, como o "change" nativo do protótipo:
                    // evita um PUT a cada dígito do ano.
                    onBlur={(event) => {
                        const openingDate = event.target.value

                        if (openingDate && openingDate !== account.openingDate) {
                            onChange({ openingDate })
                        }
                    }}
                />
            </td>

            <td>
                <input
                    type="checkbox"
                    checked={account.active}
                    aria-label="Ativa"
                    disabled={busy}
                    onChange={(event) =>
                        onChange({ active: event.target.checked })
                    }
                />
            </td>

            <td>
                <button
                    type="button"
                    className="btn sec sm"
                    aria-label="Excluir conta"
                    title="Excluir conta (com movimentos, ela é só desativada)"
                    disabled={busy}
                    onClick={onDelete}
                >
                    ✕
                </button>
            </td>
        </tr>
    )
}
