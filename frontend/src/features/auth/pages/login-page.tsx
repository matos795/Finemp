import { zodResolver } from "@hookform/resolvers/zod"
import { BarChart3, LockKeyhole } from "lucide-react"
import { useForm } from "react-hook-form"
import { useQueryClient } from "@tanstack/react-query"
import { Navigate, useNavigate } from "react-router"
import { useState } from "react"
import { currentUserQueryKey, useCurrentUser } from "../hooks/use-current-user"
import { loginSchema, type LoginFormData } from "../schemas/login-schema"
import { getCurrentUser, login } from "../api/auth-api"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../../components/ui/card"
import { Label } from "../../../components/ui/label"
import { Input } from "../../../components/ui/input"
import { Button } from "../../../components/ui/button"



export function LoginPage() {
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const currentUser = useCurrentUser()

    const [authenticationError, setAuthenticationError] =
        useState<string | null>(null)

    const form = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
        defaultValues: {
            email: "",
            password: "",
        },
    })

    if (currentUser.data) {
        return <Navigate to="/" replace />
    }

    async function onSubmit(data: LoginFormData) {
        try {
            setAuthenticationError(null)

            await login(data)

            const user = await getCurrentUser()

            queryClient.setQueryData(
                currentUserQueryKey,
                user,
            )

            navigate("/", {
                replace: true,
            })
        } catch {
            setAuthenticationError(
                "Não foi possível entrar. Confira seu e-mail e senha.",
            )
        }
    }

    return (
        <main className="min-h-screen bg-[#f6f7f9] lg:grid lg:grid-cols-[1.05fr_0.95fr]">
            <section className="hidden bg-[#102238] p-12 text-white lg:flex lg:flex-col lg:justify-between">
                <div>
                    <div className="mb-16 flex items-center gap-3">
                        <div className="flex size-10 items-center justify-center rounded-xl bg-[#c7a35a] text-[#102238]">
                            <BarChart3 className="size-5" />
                        </div>

                        <div>
                            <p className="text-xl font-semibold tracking-tight">
                                FINEMP
                            </p>

                            <p className="text-xs text-white/50">
                                Inteligência contábil
                            </p>
                        </div>
                    </div>

                    <div className="max-w-xl">
                        <p className="mb-5 text-sm font-medium uppercase tracking-[0.18em] text-[#d0ad64]">
                            Gestão financeira
                        </p>

                        <h1 className="text-5xl font-semibold leading-[1.08] tracking-tight">
                            Informação contábil
                            transformada em decisão.
                        </h1>

                        <p className="mt-6 max-w-lg text-lg leading-8 text-white/60">
                            Indicadores, demonstrações e análises
                            financeiras reunidos em uma experiência
                            segura e objetiva.
                        </p>
                    </div>
                </div>

                <div className="flex items-center gap-2 text-sm text-white/40">
                    <LockKeyhole className="size-4" />
                    Acesso seguro e individualizado
                </div>
            </section>

            <section className="flex min-h-screen items-center justify-center px-6 py-12">
                <div className="w-full max-w-md">
                    <div className="mb-8 lg:hidden">
                        <p className="text-2xl font-semibold text-[#102238]">
                            FINEMP
                        </p>
                        <p className="text-sm text-muted-foreground">
                            Inteligência contábil
                        </p>
                    </div>

                    <Card className="border-0 shadow-xl shadow-slate-200/60">
                        <CardHeader className="space-y-2 pb-7">
                            <CardTitle className="text-2xl">
                                Acesse sua conta
                            </CardTitle>

                            <CardDescription>
                                Entre com suas credenciais para acessar
                                as empresas e informações disponíveis
                                para o seu perfil.
                            </CardDescription>
                        </CardHeader>

                        <CardContent>
                            <form
                                onSubmit={form.handleSubmit(onSubmit)}
                                className="space-y-5"
                            >
                                <div className="space-y-2">
                                    <Label htmlFor="email">
                                        E-mail
                                    </Label>

                                    <Input
                                        id="email"
                                        type="email"
                                        autoComplete="email"
                                        placeholder="nome@empresa.com.br"
                                        {...form.register("email")}
                                    />

                                    {form.formState.errors.email && (
                                        <p className="text-sm text-destructive">
                                            {form.formState.errors.email.message}
                                        </p>
                                    )}
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="password">
                                        Senha
                                    </Label>

                                    <Input
                                        id="password"
                                        type="password"
                                        autoComplete="current-password"
                                        {...form.register("password")}
                                    />

                                    {form.formState.errors.password && (
                                        <p className="text-sm text-destructive">
                                            {form.formState.errors.password.message}
                                        </p>
                                    )}
                                </div>

                                {authenticationError && (
                                    <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive">
                                        {authenticationError}
                                    </div>
                                )}

                                <Button
                                    type="submit"
                                    disabled={form.formState.isSubmitting}
                                    className="h-11 w-full bg-[#102238] hover:bg-[#18324e]"
                                >
                                    {form.formState.isSubmitting
                                        ? "Entrando..."
                                        : "Entrar"}
                                </Button>
                            </form>
                        </CardContent>
                    </Card>
                </div>
            </section>
        </main>
    )
}