import { ApiError } from "../../../lib/api/api-client.ts"

export type BusinessPartnerOperation = "list" | "save" | "delete"

/** Mensagem sem códigos internos e sem repetir documento, nome ou e-mail. */
export function describeBusinessPartnerError(
    error: unknown,
    operation: BusinessPartnerOperation,
): string {
    if (!(error instanceof ApiError)) {
        return operation === "list"
            ? "Não foi possível carregar os cadastros."
            : "Não foi possível concluir. Verifique a conexão e tente de novo."
    }

    switch (error.code) {
        case "DUPLICATE_DOCUMENT":
            return "Já existe um cadastro com este documento nesta empresa (pode estar inativo). Procure-o na lista, inclusive entre os inativos."
        case "STALE_VERSION":
        case "BUSINESS_PARTNER_CONFLICT":
            return "O cadastro foi alterado ao mesmo tempo ou está em uso. Recarregue a lista e tente de novo."
    }

    switch (error.status) {
        case 400:
            return operation === "list"
                ? "Confira a busca."
                : "Confira os dados: o sistema recusou o CPF/CNPJ, o nome ou o e-mail."
        case 401:
            return "Sua sessão terminou. Entre de novo."
        case 403:
            return operation === "list"
                ? "Você não tem acesso aos clientes e fornecedores desta empresa."
                : "Você não tem permissão para alterar clientes e fornecedores desta empresa."
        case 404:
            return operation === "list"
                ? "Clientes e fornecedores não estão disponíveis neste ambiente."
                : "Cadastro não encontrado. Recarregue a lista."
        case 409:
            return "O cadastro mudou ou está em uso. Recarregue a lista e tente de novo."
    }

    return operation === "list"
        ? "Não foi possível carregar os cadastros."
        : "Não foi possível concluir a operação."
}
