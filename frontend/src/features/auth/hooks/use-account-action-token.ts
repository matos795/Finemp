import { useEffect, useState } from "react"
import {
    readAndClearAccountActionToken,
    releaseCapturedAccountActionToken,
} from "../lib/account-action-token"

/**
 * Token do link (#token=…) da página atual, só em memória. O fragmento sai
 * da barra de endereço já na primeira renderização; depois de montar, a
 * cópia temporária (que cobre a dupla renderização do StrictMode) é
 * descartada e o token fica apenas no estado desta página.
 */
export function useAccountActionToken() {
    const [token] = useState(() => readAndClearAccountActionToken(window))

    useEffect(() => {
        releaseCapturedAccountActionToken()
    }, [])

    return token
}
