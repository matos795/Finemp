import { useState } from "react"
import type { FormEvent } from "react"
import {
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createResultCenter,
    listResultCenters,
    updateResultCenter,
    type ResultCenter,
    type ResultCenterType,
} from "../api/result-center-api"

const PAGE_SIZE = 50

function messageFor(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
        return "O centro foi alterado por outra operação ou conflita com a estrutura atual. Atualize a lista e tente novamente."
    }

    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para administrar centros de resultado."
    }

    return "Não foi possível concluir a operação."
}

interface EditorProps {
    center: ResultCenter
    busy: boolean
    onSave: (input: {
        name: string
        active: boolean
        type: ResultCenterType
    }) => Promise<void>
}

function ResultCenterEditor({
    center,
    busy,
    onSave,
}: EditorProps) {
    const [name, setName] = useState(center.name)
    const [active, setActive] = useState(center.active)
    const [type, setType] = useState<ResultCenterType>(center.type)

    return (
        <div style={{ display: "grid", gap: 8 }}>
            <div className="fl">
                <label htmlFor="center-edit-name">Nome</label>
                <input
                    id="center-edit-name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                />
            </div>

            <div className="fl">
                <label htmlFor="center-edit-type">Tipo</label>
                <select
                    id="center-edit-type"
                    value={type}
                    onChange={(event) =>
                        setType(event.target.value as ResultCenterType)
                    }
                >
                    <option value="ANALYTICAL">Analítico</option>
                    <option value="GROUP">Grupo</option>
                </select>
            </div>

            <label>
                <input
                    type="checkbox"
                    checked={active}
                    onChange={(event) => setActive(event.target.checked)}
                />{" "}
                Ativo
            </label>

            <button
                className="btn"
                type="button"
                disabled={busy || !name.trim()}
                onClick={() =>
                    void onSave({
                        name: name.trim(),
                        active,
                        type,
                    })
                }
            >
                Salvar alterações
            </button>
        </div>
    )
}

function ResultCentersForCompany({
    companyId,
    canManage,
}: {
    companyId: string
    canManage: boolean
}) {
    const queryClient = useQueryClient()
    const [query, setQuery] = useState("")
    const [search, setSearch] = useState("")
    const [page, setPage] = useState(0)
    const [selectedId, setSelectedId] = useState("")
    const [newCode, setNewCode] = useState("")
    const [newName, setNewName] = useState("")
    const [newType, setNewType] =
        useState<ResultCenterType>("ANALYTICAL")
    const [mutationBusy, setMutationBusy] = useState(false)
    const [mutationError, setMutationError] = useState("")
    const [message, setMessage] = useState("")

    const centersQuery = useQuery({
        queryKey: ["result-centers", companyId, search, page] as const,
        queryFn: () =>
            listResultCenters(
                companyId,
                search,
                page,
                PAGE_SIZE,
            ),
        retry: false,
    })

    const result = centersQuery.data
    const selected =
        result?.items.find((center) => center.id === selectedId) ??
        result?.items[0] ??
        null
    const queryError = centersQuery.error
        ? messageFor(centersQuery.error)
        : ""
    const busy = centersQuery.isFetching || mutationBusy

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setPage(0)
        setSelectedId("")
        setSearch(query.trim())
    }

    async function create(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!canManage) return

        setMutationBusy(true)
        setMutationError("")
        setMessage("")

        try {
            await createResultCenter(companyId, {
                code: newCode.trim(),
                name: newName.trim(),
                type: newType,
            })
            setNewCode("")
            setNewName("")
            setNewType("ANALYTICAL")
            setPage(0)
            setSelectedId("")
            setMessage("Centro de resultado criado.")
            await queryClient.invalidateQueries({
                queryKey: ["result-centers", companyId],
            })
        } catch (caught) {
            setMutationError(messageFor(caught))
        } finally {
            setMutationBusy(false)
        }
    }

    async function save(
        center: ResultCenter,
        input: {
            name: string
            active: boolean
            type: ResultCenterType
        },
    ) {
        if (!canManage) return

        setMutationBusy(true)
        setMutationError("")
        setMessage("")

        try {
            await updateResultCenter(
                companyId,
                center.id,
                {
                    ...input,
                    expectedVersion: center.version,
                },
            )
            setMessage("Centro de resultado atualizado.")
            await queryClient.invalidateQueries({
                queryKey: ["result-centers", companyId],
            })
        } catch (caught) {
            setMutationError(messageFor(caught))
        } finally {
            setMutationBusy(false)
        }
    }

    return (
        <div>
            <section className="mbox">
                <h1>Centros de resultado</h1>
                <p className="help">
                    Estrutura usada para segmentar a operação financeira.
                    Código e hierarquia seguem o contrato do backend; o
                    frontend não infere parentesco nem regras financeiras.
                </p>

                <form
                    onSubmit={submitSearch}
                    style={{
                        display: "flex",
                        gap: 8,
                        flexWrap: "wrap",
                    }}
                >
                    <input
                        value={query}
                        onChange={(event) =>
                            setQuery(event.target.value)
                        }
                        placeholder="Buscar por código ou nome"
                    />
                    <button
                        className="btn sec"
                        type="submit"
                        disabled={busy}
                    >
                        Buscar
                    </button>
                </form>

                {message && (
                    <div className="alert i">{message}</div>
                )}
                {(queryError || mutationError) && (
                    <div className="alert e">
                        {mutationError || queryError}
                    </div>
                )}
            </section>

            {canManage && (
                <section className="mbox">
                    <h2>Novo centro</h2>
                    <form
                        onSubmit={create}
                        style={{ display: "grid", gap: 8 }}
                    >
                        <div className="fl">
                            <label htmlFor="center-code">
                                Código
                            </label>
                            <input
                                id="center-code"
                                value={newCode}
                                onChange={(event) =>
                                    setNewCode(event.target.value)
                                }
                                placeholder="Ex.: 1 ou 1.1"
                                required
                            />
                        </div>

                        <div className="fl">
                            <label htmlFor="center-name">
                                Nome
                            </label>
                            <input
                                id="center-name"
                                value={newName}
                                onChange={(event) =>
                                    setNewName(event.target.value)
                                }
                                required
                            />
                        </div>

                        <div className="fl">
                            <label htmlFor="center-type">
                                Tipo
                            </label>
                            <select
                                id="center-type"
                                value={newType}
                                onChange={(event) =>
                                    setNewType(
                                        event.target.value as ResultCenterType,
                                    )
                                }
                            >
                                <option value="ANALYTICAL">
                                    Analítico
                                </option>
                                <option value="GROUP">
                                    Grupo
                                </option>
                            </select>
                        </div>

                        <button
                            className="btn"
                            type="submit"
                            disabled={
                                busy ||
                                !newCode.trim() ||
                                !newName.trim()
                            }
                        >
                            Criar centro
                        </button>
                    </form>
                </section>
            )}

            <section className="mbox">
                <h2>Estrutura</h2>

                {centersQuery.isLoading && (
                    <p>Carregando...</p>
                )}

                {!centersQuery.isLoading &&
                    result?.items.length === 0 && (
                        <p>Nenhum centro encontrado.</p>
                    )}

                <div style={{ display: "grid", gap: 8 }}>
                    {result?.items.map((center) => (
                        <button
                            key={center.id}
                            type="button"
                            className="card"
                            aria-pressed={
                                selected?.id === center.id
                            }
                            onClick={() =>
                                setSelectedId(center.id)
                            }
                            style={{
                                textAlign: "left",
                                width: "100%",
                                cursor: "pointer",
                            }}
                        >
                            <strong>
                                {center.code} — {center.name}
                            </strong>
                            <div>
                                {center.type === "GROUP"
                                    ? "Grupo"
                                    : "Analítico"}
                            </div>
                            <small>
                                {center.active
                                    ? "Ativo"
                                    : "Inativo"}
                            </small>
                        </button>
                    ))}
                </div>

                {result && result.totalPages > 1 && (
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            marginTop: 12,
                        }}
                    >
                        <button
                            className="btn sec"
                            type="button"
                            disabled={
                                busy || result.page === 0
                            }
                            onClick={() => {
                                setSelectedId("")
                                setPage((current) =>
                                    Math.max(0, current - 1),
                                )
                            }}
                        >
                            Anterior
                        </button>

                        <small>
                            Página {result.page + 1} de{" "}
                            {result.totalPages}
                        </small>

                        <button
                            className="btn sec"
                            type="button"
                            disabled={
                                busy ||
                                result.page + 1 >=
                                    result.totalPages
                            }
                            onClick={() => {
                                setSelectedId("")
                                setPage((current) =>
                                    current + 1,
                                )
                            }}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>

            {selected && (
                <section className="mbox">
                    <h2>
                        {selected.code} — {selected.name}
                    </h2>
                    <p className="help">
                        Versão atual: {selected.version}. O código
                        não é alterado nesta tela porque define a
                        posição hierárquica.
                    </p>

                    {canManage ? (
                        <ResultCenterEditor
                            key={`${selected.id}:${selected.version}`}
                            center={selected}
                            busy={busy}
                            onSave={(input) =>
                                save(selected, input)
                            }
                        />
                    ) : (
                        <p>
                            Seu acesso é somente de consulta.
                        </p>
                    )}
                </section>
            )}
        </div>
    )
}

export function ResultCentersPage() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <div className="mbox">
                Selecione uma empresa para administrar centros
                de resultado.
            </div>
        )
    }

    return (
        <ResultCentersForCompany
            key={selectedCompany.id}
            companyId={selectedCompany.id}
            canManage={hasPermission(
                "RESULT_CENTER_MANAGE",
            )}
        />
    )
}
