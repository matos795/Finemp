import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { useToast } from "../../../components/finemp/toast-context"
import { handleFinancialCategoryFailure } from "../lib/financial-category-errors"

/**
 * Executa um comando de um diálogo do plano de contas: sucesso fecha com
 * aviso; erro que deixou a janela desatualizada (versão, conflito, 404,
 * 403) fecha, avisa e relê a lista; os demais ficam na janela.
 */
export function useCategoryCommand(companyId: string, onClose: () => void) {
    const queryClient = useQueryClient()
    const showToast = useToast()
    const [error, setError] = useState("")

    async function run(command: () => Promise<unknown>, success: string) {
        setError("")

        try {
            await command()
            showToast(success)
            onClose()
        } catch (failure) {
            const result = await handleFinancialCategoryFailure(queryClient, companyId, failure)

            if (result.sessionExpired) {
                return
            }

            if (result.closeDialog) {
                showToast(result.message, true)
                onClose()
                return
            }

            setError(result.message)
        }
    }

    return { error, run }
}
