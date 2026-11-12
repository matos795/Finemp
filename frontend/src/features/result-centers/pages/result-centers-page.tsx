import { useEffect, useState } from "react"
import type { FormEvent } from "react"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createResultCenter,
    listResultCenters,
    updateResultCenter,
    type ResultCenter,
    type ResultCenterPage,
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

export function ResultCentersPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id ?? ""
    const canManage = hasPermission("RESULT_CENTER_MANAGE")

    const [query, setQuery] = useState("")
    const [search, setSearch] = useState("")
    const [page, setPage] = useState(0)
    const [result, setResult] = useState<ResultCenterPage | null>(null)
    const [selectedId, setSelectedId] = useState("")
    const [newCode, setNewCode] = useState("")
    const [newName, setNewName] = useState("")
    const [newType, setNewType] = useState<ResultCenterType>("ANALYTICAL")
    const [editName, setEditName] = useState("")
    const [editActive, setEditActive] = useState(true)
    const [editType, setEditType] = useState<ResultCenterType>("ANALYTICAL")
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState("")
    const [message, setMessage] = useState("")

    const selected =
        result?.items.find((center) => center.id === selectedId) ?? null

    async function load(targetPage = page, targetSearch = search) {
        if (!companyId) {
            setResult(null)
            return
        }

        setBusy(true)
        setError("")

        try {
            const next = await listResultCenters(
                companyId,
                targetSearch,
                targetPage,
                PAGE_SIZE,
            )
            setResult(next)
            setSelectedId((current) =>
                next.items.some((center) => center.id === current)
                    ? current
                    : next.items[0]?.id ?? "",
            )
        } catch (caught) {
            setError(messageFor(caught))
            setResult(null)
        } finally {
            setBusy(false)
        }
    }

    useEffect(() => {
        setPage(0)
        setSearch("")
        setQuery("")
        setSelectedId("")
        void load(0, "")
        // A troca de Company deve sempre reiniciar o estado visual.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [companyId])

    useEffect(() => {
        if (!selected) return

        setEditName(selected.name)
        setEditActive(selected.active)
        setEditType(selected.type)
    }, [selected])

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        const normalized = query.trim()
        setPage(0)
        setSearch(normalized)
        void load(0, normalized)
    }

    async function create(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!companyId || !canManage) return

        setBusy(true)
        setError("")
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
            setMessage("Centro de resultado criado.")
            setPage(0)
            await load(0, search)
        } catch (caught) {
            setError(messageFor(caught))
        } finally {
            setBusy(false)
        }
    }

    async function save() {
        if (!companyId || !selected || !canManage) return

        setBusy(true)
        setError("")
        setMessage("")

        try {
            await updateResultCenter(companyId, selected.id, {
                name: editName.trim(),
                active: editActive,
                expectedVersion: selected.version,
                type: editType,
            })
            setMessage("Centro de resultado atualizado.")
            await load(page, search)
        } catch (caught) {
            setError(messageFor(caught))
        } finally {
            setBusy(false)
        }
    }

    if (!selectedCompany) {
        return <div className="mbox">Selecione uma empresa para administrar centros de resultado.</div>
    }

    return (
        <div>
            <section className="mbox">
                <h1>Centros de resultado</h1>
                <p className="help">
                    Estrutura usada para segmentar a operação financeira. Código e
                    hierarquia seguem o contrato do backend; o frontend não infere
                    parentesco nem regras financeiras.
                </p>

                <form onSubmit={submitSearch} style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <input
                        value={query}
                        onChange={(event) => setQuery(event.target.value)}
                        placeholder="Buscar por código ou nome"
                    />
                    <button className="btn sec" type="submit" disabled={busy}>
                        Buscar
                    </button>
                </form>

                {message && <div className="alert i">{message}</div>}
                {error && <div className="alert e">{error}</div>}
            </section>

            {canManage && (
                <section className="mbox">
                    <h2>Novo centro</h2>
                    <form onSubmit={create} style={{ display: "grid", gap: 8 }}>
                        <div className="fl">
                            <label htmlFor="center-code">Código</label>
                            <input
                                id="center-code"
                                value={newCode}
                                onChange={(event) => setNewCode(event.target.value)}
                                placeholder="Ex.: 1 ou 1.1"
                                required
                            />
                        </div>
                        <div className="fl">
                            <label htmlFor="center-name">Nome</label>
                            <input
                                id="center-name"
                                value={newName}
                                onChange={(event) => setNewName(event.target.value)}
                                required
                            />
                        </div>
                        <div className="fl">
                            <label htmlFor="center-type">Tipo</label>
                            <select
                                id="center-type"
                                value={newType}
                                onChange={(event) =>
                                    setNewType(event.target.value as ResultCenterType)
                                }
                            >
                                <option value="ANALYTICAL">Analítico</option>
                                <option value="GROUP">Grupo</option>
                            </select>
                        </div>
                        <button className="btn" type="submit" disabled={busy}>
                            Criar centro
                        </button>
                    </form>
                </section>
            )}

            <section className="mbox">
                <h2>Estrutura</h2>
                {busy && !result && <p>Carregando...</p>}
                {!busy && result?.items.length === 0 && <p>Nenhum centro encontrado.</p>}

                <div style={{ display: "grid", gap: 8 }}>
                    {result?.items.map((center) => (
                        <button
                            key={center.id}
                            type="button"
                            className="card"
                            aria-pressed={selectedId === center.id}
                            onClick={() => setSelectedId(center.id)}
                            style={{ textAlign: "left", width: "100%", cursor: "pointer" }}
                        >
                            <strong>{center.code} — {center.name}</strong>
                            <div>{center.type === "GROUP" ? "Grupo" : "Analítico"}</div>
                            <small>{center.active ? "Ativo" : "Inativo"}</small>
                        </button>
                    ))}
                </div>

                {result && result.totalPages > 1 && (
                    <div style={{ display: "flex", justifyContent: "space-between", marginTop: 12 }}>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={busy || result.page === 0}
                            onClick={() => {
                                const next = Math.max(0, page - 1)
                                setPage(next)
                                void load(next, search)
                            }}
                        >
                            Anterior
                        </button>
                        <small>Página {result.page + 1} de {result.totalPages}</small>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={busy || result.page + 1 >= result.totalPages}
                            onClick={() => {
                                const next = page + 1
                                setPage(next)
                                void load(next, search)
                            }}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>

            {selected && (
                <section className="mbox">
                    <h2>{selected.code} — {selected.name}</h2>
                    <p className="help">
                        Versão atual: {selected.version}. O código não é alterado
                        nesta tela porque define a posição hierárquica.
                    </p>

                    {canManage ? (
                        <div style={{ display: "grid", gap: 8 }}>
                            <div className="fl">
                                <label htmlFor="center-edit-name">Nome</label>
                                <input
                                    id="center-edit-name"
                                    value={editName}
                                    onChange={(event) => setEditName(event.target.value)}
                                />
                            </div>
                            <div className="fl">
                                <label htmlFor="center-edit-type">Tipo</label>
                                <select
                                    id="center-edit-type"
                                    value={editType}
                                    onChange={(event) =>
                                        setEditType(event.target.value as ResultCenterType)
                                    }
                                >
                                    <option value="ANALYTICAL">Analítico</option>
                                    <option value="GROUP">Grupo</option>
                                </select>
                            </div>
                            <label>
                                <input
                                    type="checkbox"
                                    checked={editActive}
                                    onChange={(event) => setEditActive(event.target.checked)}
                                />{" "}
                                Ativo
                            </label>
                            <button className="btn" type="button" disabled={busy} onClick={() => void save()}>
                                Salvar alterações
                            </button>
                        </div>
                    ) : (
                        <p>Seu acesso é somente de consulta.</p>
                    )}
                </section>
            )}
        </div>
    )
}
