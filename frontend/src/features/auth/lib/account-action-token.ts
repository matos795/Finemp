/**
 * Token dos links de convite (/activate) e recuperação (/reset-password).
 *
 * O backend o envia só no fragmento (#token=…), que o navegador não manda
 * ao servidor. Aqui ele é lido uma vez, removido da barra de endereço com
 * history.replaceState e mantido apenas em memória (estado da página).
 * Nunca vai para query string, storage, chave de cache, log ou mensagem.
 */

/** Mesmo formato aceito pelo backend: base64url de 32 bytes, sem padding. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/

/** O mínimo de window que a leitura usa (permite testar sem DOM). */
export interface AddressBar {
    location: Pick<Location, "pathname" | "search" | "hash">
    history: Pick<History, "state" | "replaceState">
}

/*
 * Em StrictMode o React renderiza a página duas vezes ao montar: a segunda
 * leitura já encontra o endereço limpo. Esta cópia cobre só esse intervalo
 * e é descartada por releaseCapturedAccountActionToken assim que a página
 * monta (o token passa a viver só no estado da página).
 */
let captured: { pathname: string, token: string | null } | null = null

/** Lê o token do fragmento, sem tocar no endereço. Formato inválido → null. */
function tokenFromHash(hash: string): string | null {
    const params = new URLSearchParams(hash.replace(/^#/, ""))
    const values = params.getAll("token")

    return values.length === 1 && TOKEN_PATTERN.test(values[0]) ? values[0] : null
}

/**
 * Lê o token do fragmento e limpa o fragmento da barra de endereço na
 * hora ("read and clear" = só no navegador; o token é usado no backend
 * apenas no envio do formulário). Ausente ou malformado → null, e a página
 * não chama a API. Qualquer fragmento é removido, válido ou não.
 */
export function readAndClearAccountActionToken(address: AddressBar): string | null {
    const { pathname, search, hash } = address.location

    if (hash) {
        address.history.replaceState(address.history.state, "", pathname + search)
        captured = { pathname, token: tokenFromHash(hash) }

        return captured.token
    }

    return captured?.pathname === pathname ? captured.token : null
}

/** Descarta a cópia temporária; chamar quando a página já guardou o token. */
export function releaseCapturedAccountActionToken() {
    captured = null
}
