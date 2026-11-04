import { useState } from "react"

/**
 * Página atual (base 0) de uma listagem da empresa selecionada.
 * Volta para a primeira página quando a empresa ou a chave muda.
 */
export function useCompanyPage(
    companyId: string | undefined,
    resetKey = "",
) {
    const [state, setState] = useState({
        companyId,
        resetKey,
        page: 0,
    })

    const page =
        state.companyId === companyId && state.resetKey === resetKey
            ? state.page
            : 0

    function setPage(nextPage: number) {
        setState({
            companyId,
            resetKey,
            page: nextPage,
        })
    }

    return [page, setPage] as const
}
