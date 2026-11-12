import { useEffect, useState } from "react"
import type { FormEvent } from "react"
import { ApiError } from "../../../lib/api/api-client"
import { useCurrentUser } from "../../auth/hooks/use-current-user"
import {
    listPlatformUsersPage,
    type PagedResponse,
    type PlatformUser,
} from "../api/platform-admin-api"
import { PlatformPermissionsEditor } from "../components/platform-permissions-editor"

const PAGE_SIZE = 20

function describeError(error: unknown) {
    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para administrar usuários da plataforma."
    }

    return "Não foi possível carregar os usuários da plataforma."
}

function statusLabel(user: PlatformUser) {
    if (user.invitationPending) return "Convite pendente"
    if (user.active) return "Conta ativa"
    return "Conta inativa"
}

export function PlatformUsersPage() {
    const { data: currentUser } = useCurrentUser()
    const [query, setQuery] = useState("")
    const [search, setSearch] = useState("")
    const [page, setPage] = useState(0)
    const [result, setResult] = useState<PagedResponse<PlatformUser> | null>(null)
    const [selectedUserId, setSelectedUserId] = useState("")
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")

    useEffect(() => {
        let active = true
        setLoading(true)
        setError("")

        void listPlatformUsersPage(search, page, PAGE_SIZE)
            .then((next) => {
                if (!active) return

                setResult(next)
                setSelectedUserId((current) => {
                    if (next.items.some((user) => user.id === current)) {
                        return current
                    }

                    return next.items[0]?.id ?? ""
                })
            })
            .catch((caught) => {
                if (active) {
                    setError(describeError(caught))
                    setResult(null)
                    setSelectedUserId("")
                }
            })
            .finally(() => {
                if (active) setLoading(false)
            })

        return () => {
            active = false
        }
    }, [page, search])

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setPage(0)
        setSearch(query.trim())
    }

    const selectedUser =
        result?.items.find((user) => user.id === selectedUserId) ?? null
    const actorPermissions = currentUser?.platformPermissions ?? []

    return (
        <div>
            <section className="mbox">
                <h1>Usuários e acessos</h1>
                <p className="help">
                    Administração contínua das identidades e permissões da
                    plataforma. Acesso de escritório e Company continua sendo
                    administrado separadamente.
                </p>

                <form
                    onSubmit={submitSearch}
                    style={{
                        display: "flex",
                        gap: 8,
                        alignItems: "end",
                        flexWrap: "wrap",
                    }}
                >
                    <div className="fl" style={{ flex: "1 1 280px" }}>
                        <label htmlFor="platform-user-search">
                            Buscar por nome ou e-mail
                        </label>
                        <input
                            id="platform-user-search"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Nome ou e-mail"
                        />
                    </div>
                    <button className="btn" type="submit" disabled={loading}>
                        Buscar
                    </button>
                </form>

                {error && <div className="alert e">{error}</div>}
            </section>

            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "minmax(280px, 0.8fr) minmax(360px, 1.2fr)",
                    gap: 16,
                    alignItems: "start",
                }}
            >
                <section className="mbox">
                    <h2>Usuários</h2>

                    {loading && <p>Carregando usuários...</p>}

                    {!loading && result?.items.length === 0 && (
                        <p className="help">
                            Nenhum usuário encontrado para esta busca.
                        </p>
                    )}

                    <div style={{ display: "grid", gap: 8 }}>
                        {result?.items.map((user) => (
                            <button
                                key={user.id}
                                type="button"
                                className="card"
                                onClick={() => setSelectedUserId(user.id)}
                                aria-pressed={selectedUserId === user.id}
                                style={{
                                    textAlign: "left",
                                    cursor: "pointer",
                                    width: "100%",
                                }}
                            >
                                <strong>{user.name}</strong>
                                <div>{user.email}</div>
                                <small>{statusLabel(user)}</small>
                            </button>
                        ))}
                    </div>

                    {result && result.totalPages > 1 && (
                        <div
                            style={{
                                display: "flex",
                                gap: 8,
                                justifyContent: "space-between",
                                alignItems: "center",
                                marginTop: 12,
                            }}
                        >
                            <button
                                className="btn sec"
                                type="button"
                                disabled={loading || result.page === 0}
                                onClick={() =>
                                    setPage((current) => Math.max(0, current - 1))
                                }
                            >
                                Anterior
                            </button>
                            <small>
                                Página {result.page + 1} de {result.totalPages}
                            </small>
                            <button
                                className="btn sec"
                                type="button"
                                disabled={
                                    loading ||
                                    result.page + 1 >= result.totalPages
                                }
                                onClick={() =>
                                    setPage((current) => current + 1)
                                }
                            >
                                Próxima
                            </button>
                        </div>
                    )}
                </section>

                <section className="mbox">
                    <h2>Detalhe do usuário</h2>

                    {!selectedUser && (
                        <p className="help">
                            Selecione um usuário para consultar as permissões.
                        </p>
                    )}

                    {selectedUser && (
                        <div>
                            <div className="card">
                                <strong>{selectedUser.name}</strong>
                                <div>{selectedUser.email}</div>
                                <small>{statusLabel(selectedUser)}</small>
                            </div>

                            <p className="help">
                                Permissões de plataforma não criam grants
                                artificiais de escritório ou Company. SYSTEM_ADMIN
                                é uma capability global e exige outro
                                administrador do sistema para ser gerenciada.
                            </p>

                            <PlatformPermissionsEditor
                                key={selectedUser.id}
                                id={selectedUser.id}
                                actorPermissions={actorPermissions}
                            />
                        </div>
                    )}
                </section>
            </div>
        </div>
    )
}
