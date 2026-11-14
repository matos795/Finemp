import { useEffect, useMemo, useState } from "react"
import { ApiError } from "../../../lib/api/api-client"
import {
    createAccountingSource,
    issueConnectorCredential,
    listAccountingSources,
    listConnectorCredentials,
    revokeConnectorCredential,
    rotateConnectorCredential,
    type AccountingSource,
    type ConnectorCredential,
} from "../api/platform-admin-api"

function describeConnectorError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 403) {
            return "Seu acesso precisa de CONNECTOR_MANAGE no escritório e QUESTOR_SYNC na empresa."
        }
        if (error.status === 409) {
            return "A fonte já possui credencial ativa ou existe sincronização em andamento."
        }
        if (error.status === 404) {
            return "A fonte contábil ou credencial não está mais disponível."
        }
        if (error.status === 400) {
            return "Confira os dados informados."
        }
    }

    return "Não foi possível concluir a operação do FINEMP Connector."
}

function credentialStatus(credential: ConnectorCredential) {
    return credential.revokedAt ? "Revogada" : "Ativa"
}

export function ConnectorAdministrationSection({
    accountingFirmId,
    clientId,
    companyId,
    disabled = false,
}: {
    accountingFirmId: string
    clientId: string
    companyId: string
    disabled?: boolean
}) {
    const [sources, setSources] = useState<AccountingSource[]>([])
    const [credentials, setCredentials] = useState<ConnectorCredential[]>([])
    const [selectedSourceId, setSelectedSourceId] = useState("")
    const [externalReference, setExternalReference] = useState("")
    const [credentialLabel, setCredentialLabel] = useState("Connector Questor")
    const [oneTimeSecret, setOneTimeSecret] = useState<string | null>(null)
    const [message, setMessage] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    const selectedSource = sources.find((source) => source.id === selectedSourceId)
    const sourceCredentials = useMemo(
        () => credentials.filter(
            (credential) => credential.accountingSourceId === selectedSourceId,
        ),
        [credentials, selectedSourceId],
    )

    async function refreshSources() {
        const page = await listAccountingSources(
            accountingFirmId,
            clientId,
            companyId,
        )
        setSources(page.items)
        setSelectedSourceId((current) =>
            page.items.some((source) => source.id === current)
                ? current
                : (page.items[0]?.id ?? ""),
        )
    }

    async function refreshCredentials() {
        const page = await listConnectorCredentials(accountingFirmId)
        setCredentials(page.items)
    }

    useEffect(() => {
        let active = true

        if (!accountingFirmId || !clientId || !companyId) return

        void Promise.all([
            listAccountingSources(accountingFirmId, clientId, companyId),
            listConnectorCredentials(accountingFirmId),
        ])
            .then(([sourcePage, credentialPage]) => {
                if (!active) return

                setSources(sourcePage.items)
                setCredentials(credentialPage.items)
                setSelectedSourceId(sourcePage.items[0]?.id ?? "")
            })
            .catch((caught) => {
                if (active) {
                    setError(describeConnectorError(caught))
                }
            })

        return () => {
            active = false
        }
    }, [accountingFirmId, clientId, companyId])

    async function run(action: () => Promise<void>) {
        setBusy(true)
        setOneTimeSecret(null)
        setMessage(null)
        setError(null)

        try {
            await action()
        } catch (caught) {
            setError(describeConnectorError(caught))
        } finally {
            setBusy(false)
        }
    }

    return (
        <section className="mbox">
            <h2>5. Fonte contábil / FINEMP Connector</h2>

            <p className="help">
                Administra a fonte Questor e a credencial de máquina usada pelo
                Connector. Esta área não recebe senha, usuário, caminho FDB ou DSN
                do Firebird.
            </p>

            {message ? <div className="alert i">{message}</div> : null}
            {error ? <div className="alert e">{error}</div> : null}

            {oneTimeSecret ? (
                <div className="alert w">
                    <b>Segredo exibido uma única vez.</b>
                    <p className="help">
                        Copie agora para a configuração local do Connector. Ao
                        fechar esta mensagem ou trocar a seleção, ele será
                        descartado desta tela.
                    </p>
                    <div className="fl">
                        <label htmlFor="connector-secret">Segredo do Connector</label>
                        <input
                            id="connector-secret"
                            value={oneTimeSecret}
                            readOnly
                            autoComplete="off"
                            spellCheck={false}
                        />
                    </div>
                    <button
                        type="button"
                        className="btn sec sm"
                        onClick={() => setOneTimeSecret(null)}
                    >
                        Ocultar segredo
                    </button>
                </div>
            ) : null}

            <div className="fl">
                <label htmlFor="accounting-source-select">Fonte contábil</label>
                <select
                    id="accounting-source-select"
                    value={selectedSourceId}
                    disabled={disabled || busy}
                    onChange={(event) => {
                        setOneTimeSecret(null)
                        setMessage(null)
                        setError(null)
                        setSelectedSourceId(event.target.value)
                    }}
                >
                    <option value="">Selecione</option>
                    {sources.map((source) => (
                        <option key={source.id} value={source.id}>
                            {source.type} · {source.externalReference || "sem referência"} · {source.connectionMode}
                        </option>
                    ))}
                </select>
            </div>

            <div className="fl">
                <label htmlFor="accounting-source-reference">
                    Referência externa da nova fonte
                </label>
                <input
                    id="accounting-source-reference"
                    maxLength={255}
                    value={externalReference}
                    disabled={disabled || busy}
                    onChange={(event) => setExternalReference(event.target.value)}
                    placeholder="Identificador operacional opcional"
                />
            </div>

            <button
                type="button"
                className="btn"
                disabled={disabled || busy}
                onClick={() => void run(async () => {
                    const created = await createAccountingSource(
                        accountingFirmId,
                        clientId,
                        companyId,
                        externalReference,
                    )
                    await refreshSources()
                    setSelectedSourceId(created.id)
                    setExternalReference("")
                    setMessage("Fonte Questor criada.")
                })}
            >
                Criar fonte Questor
            </button>

            {selectedSource ? (
                <>
                    <div className="cards" style={{ marginTop: 12 }}>
                        <div className="card">
                            <div className="lb">Fonte</div>
                            <div className="vl" style={{ fontSize: 18 }}>
                                {selectedSource.type}
                            </div>
                            <div className="sub">
                                {selectedSource.active ? "Ativa" : "Inativa"}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Conexão</div>
                            <div className="vl" style={{ fontSize: 18 }}>
                                {selectedSource.connectionMode}
                            </div>
                            <div className="sub">
                                {selectedSource.connectionMode === "CONNECTOR"
                                    ? "Reservada para FINEMP Connector"
                                    : "Leitura direta"}
                            </div>
                        </div>
                    </div>

                    <div className="fl">
                        <label htmlFor="connector-label">Identificação da credencial</label>
                        <input
                            id="connector-label"
                            maxLength={80}
                            value={credentialLabel}
                            disabled={disabled || busy}
                            onChange={(event) => setCredentialLabel(event.target.value)}
                        />
                    </div>

                    <button
                        type="button"
                        className="btn"
                        disabled={
                            disabled ||
                            busy ||
                            !credentialLabel.trim() ||
                            sourceCredentials.some((credential) => !credential.revokedAt)
                        }
                        onClick={() => void run(async () => {
                            const issued = await issueConnectorCredential(
                                accountingFirmId,
                                selectedSource.id,
                                credentialLabel,
                            )
                            setOneTimeSecret(issued.secret)
                            await Promise.all([
                                refreshSources(),
                                refreshCredentials(),
                            ])
                            setMessage(
                                "Credencial emitida. O segredo não poderá ser consultado novamente.",
                            )
                        })}
                    >
                        Emitir credencial Connector
                    </button>

                    <div className="tblwrap" style={{ marginTop: 12 }}>
                        <table>
                            <thead>
                                <tr>
                                    <th>Identificação</th>
                                    <th>Situação</th>
                                    <th>Validade</th>
                                    <th>Ações</th>
                                </tr>
                            </thead>
                            <tbody>
                                {sourceCredentials.map((credential) => (
                                    <tr key={credential.id}>
                                        <td>{credential.label}</td>
                                        <td>
                                            <span
                                                className={
                                                    credential.revokedAt
                                                        ? "badge b-neu"
                                                        : "badge b-ok"
                                                }
                                            >
                                                {credentialStatus(credential)}
                                            </span>
                                        </td>
                                        <td>
                                            {new Date(credential.expiresAt)
                                                .toLocaleString("pt-BR")}
                                        </td>
                                        <td>
                                            {!credential.revokedAt ? (
                                                <div
                                                    style={{
                                                        display: "flex",
                                                        gap: 6,
                                                        flexWrap: "wrap",
                                                    }}
                                                >
                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        disabled={disabled || busy}
                                                        onClick={() => void run(async () => {
                                                            const rotated =
                                                                await rotateConnectorCredential(
                                                                    accountingFirmId,
                                                                    credential.id,
                                                                )
                                                            setOneTimeSecret(rotated.secret)
                                                            await refreshCredentials()
                                                            setMessage(
                                                                "Credencial rotacionada. Substitua o segredo no Connector local.",
                                                            )
                                                        })}
                                                    >
                                                        Rotacionar
                                                    </button>

                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        disabled={disabled || busy}
                                                        onClick={() => {
                                                            if (!window.confirm(
                                                                "Revogar esta credencial do Connector? O agente deixará de autenticar com este segredo.",
                                                            )) {
                                                                return
                                                            }

                                                            void run(async () => {
                                                                await revokeConnectorCredential(
                                                                    accountingFirmId,
                                                                    credential.id,
                                                                )
                                                                await refreshCredentials()
                                                                setMessage("Credencial revogada.")
                                                            })
                                                        }}
                                                    >
                                                        Revogar
                                                    </button>
                                                </div>
                                            ) : (
                                                "—"
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {sourceCredentials.length === 0 ? (
                        <div className="empty">
                            Nenhuma credencial emitida para esta fonte.
                        </div>
                    ) : null}
                </>
            ) : (
                <div className="empty">
                    Crie ou selecione uma fonte Questor para administrar o Connector.
                </div>
            )}
        </section>
    )
}
