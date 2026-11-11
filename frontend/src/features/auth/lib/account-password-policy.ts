/**
 * Espelho, só para UX, da política do backend (AccountPasswordPolicy):
 * pelo menos 8 caracteres, uma letra de A a Z, um dígito de 0 a 9 e no
 * máximo 72 bytes em UTF-8. O backend continua sendo a autoridade.
 *
 * Detalhes para coincidir com o Java: `length` conta unidades UTF-16 nos
 * dois lados; a letra é [a-zA-Z] (acentuadas não contam) e o dígito é o
 * \d ASCII do Java.
 */

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_UTF8_BYTES = 72

export const PASSWORD_RULES_HINT =
    "Use pelo menos 8 caracteres, com ao menos uma letra (A a Z) e um número."

export interface NewPasswordErrors {
    password?: string
    confirmation?: string
}

const encoder = new TextEncoder()

export function utf8ByteLength(value: string) {
    return encoder.encode(value).length
}

function passwordProblem(password: string): string | undefined {
    if (!password) {
        return "Informe a nova senha."
    }

    if (password.length < PASSWORD_MIN_LENGTH) {
        return "A senha precisa ter pelo menos 8 caracteres."
    }

    if (utf8ByteLength(password) > PASSWORD_MAX_UTF8_BYTES) {
        return "A senha está longa demais (limite de 72 bytes; acentos e símbolos ocupam mais). Use uma senha mais curta."
    }

    if (!/[a-zA-Z]/.test(password)) {
        return "Inclua pelo menos uma letra de A a Z (letras acentuadas não contam)."
    }

    if (!/[0-9]/.test(password)) {
        return "Inclua pelo menos um número."
    }

    return undefined
}

/** Erros por campo; vazio quando a senha atende à política e confere. */
export function validateNewPassword(password: string, confirmation: string): NewPasswordErrors {
    const errors: NewPasswordErrors = {}
    const problem = passwordProblem(password)

    if (problem) {
        errors.password = problem
    }

    if (!confirmation) {
        errors.confirmation = "Confirme a nova senha."
    } else if (confirmation !== password) {
        errors.confirmation = "As senhas não coincidem."
    }

    return errors
}
