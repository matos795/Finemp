import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import {
    listAccountingFirms,
    listClients,
    listCompanies,
} from "../api/platform-admin-api"

function errorMessage(error: unknown) {
    if (error instanceof ApiError && error.status === 403) {
        return "Seu acesso não permite consultar a hierarquia deste escritório."
    }

    return "Não foi possível carregar os clientes FINEMP."
}

export function PlatformClientsPage() {
    const [selectedFirmId, setSelectedFirmId] =
        useState("")
    const [selectedClientId, setSelectedClientId] =
        useState("")

    const firmsQuery = useQuery({
        queryKey: ["platform-accounting-firms"] as const,
        queryFn: listAccountingFirms,
        retry: false,
    })

    const firmId =
        selectedFirmId ||
        firmsQuery.data?.items[0]?.id ||
        ""

    const clientsQuery = useQuery({
        queryKey: [
            "platform-clients",
            firmId,
        ] as const,
        queryFn: () => listClients(firmId),
        enabled: Boolean(firmId),
        retry: false,
    })

    const clientId =
        selectedClientId &&
        clientsQuery.data?.items.some(
            (client) =>
                client.id === selectedClientId,
        )
            ? selectedClientId
            : clientsQuery.data?.items[0]?.id ?? ""

    const companiesQuery = useQuery({
        queryKey: [
            "platform-client-companies",
            firmId,
            clientId,
        ] as const,
        queryFn: () =>
            listCompanies(firmId, clientId),
        enabled: Boolean(firmId && clientId),
        retry: false,
    })

    const error =
        firmsQuery.error ||
        clientsQuery.error ||
        companiesQuery.error

    const busy =
        firmsQuery.isFetching ||
        clientsQuery.isFetching ||
        companiesQuery.isFetching

    return (
        <div>
            <section className="mbox">
                <h1>Clientes FINEMP</h1>
                <p className="help">
                    Visão administrativa somente de consulta.
                    Criação de escritório, cliente, Company e
                    concessão de acesso continuam no Onboarding
                    SaaS.
                </p>

                {error && (
                    <div className="alert e">
                        {errorMessage(error)}
                    </div>
                )}
            </section>

            <section className="mbox">
                <div className="fl">
                    <label htmlFor="firm-filter">
                        Escritório contábil
                    </label>
                    <select
                        id="firm-filter"
                        value={firmId}
                        onChange={(event) => {
                            setSelectedFirmId(
                                event.target.value,
                            )
                            setSelectedClientId("")
                        }}
                    >
                        <option value="">
                            Selecione
                        </option>
                        {firmsQuery.data?.items.map(
                            (firm) => (
                                <option
                                    value={firm.id}
                                    key={firm.id}
                                >
                                    {firm.name}
                                    {firm.active
                                        ? ""
                                        : " (inativo)"}
                                </option>
                            ),
                        )}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="client-filter">
                        Cliente
                    </label>
                    <select
                        id="client-filter"
                        value={clientId}
                        disabled={!firmId}
                        onChange={(event) =>
                            setSelectedClientId(
                                event.target.value,
                            )
                        }
                    >
                        <option value="">
                            Selecione
                        </option>
                        {clientsQuery.data?.items.map(
                            (client) => (
                                <option
                                    value={client.id}
                                    key={client.id}
                                >
                                    {client.name}
                                    {client.active
                                        ? ""
                                        : " (inativo)"}
                                </option>
                            ),
                        )}
                    </select>
                </div>

                {busy && <p>Carregando...</p>}
            </section>

            <section className="mbox">
                <h2>Companies do cliente</h2>

                {!busy &&
                    clientId &&
                    companiesQuery.data?.items
                        .length === 0 && (
                        <p className="help">
                            Nenhuma Company cadastrada para este
                            cliente.
                        </p>
                    )}

                <div style={{ display: "grid", gap: 8 }}>
                    {companiesQuery.data?.items.map(
                        (company) => (
                            <div
                                className="card"
                                key={company.id}
                            >
                                <strong>
                                    {company.legalName}
                                </strong>
                                <small>
                                    ID interno: {company.id}
                                </small>
                            </div>
                        ),
                    )}
                </div>
            </section>
        </div>
    )
}
