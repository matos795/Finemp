import type {
    FieldErrors,
    Resolver,
} from "react-hook-form"
import { z } from "zod"

export const loginSchema = z.object({
    email: z
        .string()
        .min(1, "Informe seu e-mail.")
        .email("Informe um e-mail válido."),

    password: z
        .string()
        .min(1, "Informe sua senha."),
})

export type LoginFormData =
    z.infer<typeof loginSchema>

/**
 * Validação do formulário com o zod 4 do projeto. O zodResolver do
 * @hookform/resolvers 3.x lança ZodError com o zod 4 em vez de devolver
 * os erros, o que travava o botão em "Entrando...".
 */
export const loginResolver: Resolver<LoginFormData> = async (values) => {
    const result = loginSchema.safeParse(values)

    if (result.success) {
        return {
            values: result.data,
            errors: {},
        }
    }

    const errors: FieldErrors<LoginFormData> = {}

    for (const issue of result.error.issues) {
        const field = issue.path[0]

        if ((field === "email" || field === "password") && !errors[field]) {
            errors[field] = {
                type: issue.code,
                message: issue.message,
            }
        }
    }

    return {
        values: {},
        errors,
    }
}
