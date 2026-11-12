import { useEffect, useState } from "react"
import { ApiError } from "../../../lib/api/api-client"
import {
    listAccountingFirms,
    listClients,
    listCompanies,
    type AccountingFirm,
    type Client,
    type Company,
} from "../api/platform-admin-api"

function errorMessage(error: unknown) {
    if (error instanceof ApiError && error.status === 403) {
        return "Seu acesso não permite consultar a hierarquia deste escritório."
    }

    return "Não foi possível carregar os clientes FINEMP."
}

export function PlatformClientsPage() {
    const [firms, setFirms] = useState<AccountingFirm[]>([])
    const [clients, setClients] = useState<Client[]>([])
    const [companies, setCompanies] = useState<Company[]>([])
    const [firmId, setFirmId] = useState("")
    const [clientId, setClientId] = useState("")
    const [busy, setBusy] = useState(true)
    const [error, setError] = useState("")

    useEffect(() => {
        let active = true

        void listAccountingFirms()
            .then((page) => {
                if (!active) return
                setFirms(page.items)
                setFirmId(page.items[0]?.id ?? "")
            })
            .catch((caught) => {
                if (active) setError(errorMessage(caught))
            })
            .finally(() => {
                if (active) setBusy(false)
            })

        return () => {
            active = false
        }
    }, [])

    useEffect(() => {
        let active = true
        setClients([])
        setCompanies([])
        setClientId("")
        setError("")

        if (!firmId) return () => { active = false }

        setBusy(true)
        void listClients(firmId)
            .then((page) => {
                if (!active) return
                setClients(page.items)
                setClientId(page.items[0]?.id ?? "")
            })
            .catch((caught) => {
                if (active) setError(errorMessage(caught))
            })
            .finally(() => {
                if (active) setBusy(false)
            })

        return () => {
            active = false
        }
    }, [firmId])

    useEffect(() => {
        let active = true
        setCompanies([])
        setError("")

        if (!firmId || !clientId) return () => { active = false }

        setBusy(true)
        void listCompanies(firmId, clientId)
            .then((page) => {
                if (active) setCompanies(page.items)
            })
            .catch((caught) => {
                if (active) setError(errorMessage(caught))
            })
            .finally(() => {
                if (active) setBusy(false)
            })

        return () => {
            active = false
        }
    }, [clientId, firmId])

    return (
        <div>
            <section className="mbox">
                <h1>Clientes FINEMP</h1>
                <p className="help">
                    Visão administrativa somente de consulta. Criação de
                    escritório, cliente, Company e concessão de acesso continuam
                    no Onboarding SaaS.
                </p>
                {error && <div className="alert e">{error}</div>}
            </section>

            <section className="mbox">
                <div className="fl">
                    <label htmlFor="firm-filter">Escritório contábil</label>
                    <select
                        id="firm-filter"
                        value={firmId}
                        onChange={(event) => setFirmId(event.target.value)}
                    >
                        <option value="">Selecione</option>
                        {firms.map((firm) => (
                            <option value={firm.id} key={firm.id}>
                                {firm.name}{firm.active ? "" : " (inativo)"}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="client-filter">Cliente</label>
                    <select
                        id="client-filter"
                        value={clientId}
                        disabled={!firmId}
                        onChange={(event) => setClientId(event.target.value)}
                    >
                        <option value="">Selecione</option>
                        {clients.map((client) => (
                            <option value={client.id} key={client.id}>
                                {client.name}{client.active ? "" : " (inativo)"}
                            </option>
                        ))}
                    </select>
                </div>

                {busy && <p>Carregando...</p>}
            </section>

            <section className="mbox">
                <h2>Companies do cliente</h2>
                {!busy && clientId && companies.length === 0 && (
                    <p className="help">Nenhuma Company cadastrada para este cliente.</p>
                )}
                <div style={{ display: "grid", gap: 8 }}>
                    {companies.map((company) => (
                        <div className="card" key={company.id}>
                            <strong>{company.legalName}</strong>
                            <small>ID interno: {company.id}</small>
                        </div>
                    ))}
                </div>
            </section>
        </div>
    )
}
