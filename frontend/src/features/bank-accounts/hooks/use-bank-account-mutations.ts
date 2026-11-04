import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import {
    createBankAccount,
    deleteBankAccount,
    updateBankAccount,
} from "../api/bank-accounts-api"
import type {
    BankAccount,
    BankAccountWrite,
} from "../types/bank-account.types"

export interface UpdateBankAccountInput {
    account: BankAccount
    changes: Partial<BankAccountWrite>
}

/** Campos cadastrais atuais da conta, para o PUT de substituição. */
export function toBankAccountWrite(
    account: BankAccount,
): BankAccountWrite {
    return {
        name: account.name,
        type: account.type,
        openingBalance: account.openingBalance,
        openingDate: account.openingDate,
        active: account.active,
    }
}

/**
 * Inclusão, alteração e exclusão de contas da empresa selecionada.
 * Toda escrita recarrega a lista: o servidor é a fonte da verdade.
 */
export function useBankAccountMutations(
    companyId: string | undefined,
) {
    const queryClient = useQueryClient()

    function refreshList() {
        return queryClient.invalidateQueries({
            queryKey: ["companies", companyId, "bank-accounts"],
        })
    }

    const create = useMutation({
        mutationFn: (values: BankAccountWrite) =>
            createBankAccount(companyId!, values),
        onSettled: refreshList,
    })

    const update = useMutation({
        mutationFn: ({ account, changes }: UpdateBankAccountInput) =>
            updateBankAccount(
                companyId!,
                account.id,
                {
                    ...toBankAccountWrite(account),
                    ...changes,
                },
                account.version,
            ),
        onSettled: refreshList,
    })

    const remove = useMutation({
        mutationFn: (account: BankAccount) =>
            deleteBankAccount(companyId!, account.id, account.version),
        onSettled: refreshList,
    })

    return {
        create,
        update,
        remove,
    }
}
