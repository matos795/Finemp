import { useEffect, useRef, useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    currentUserQueryKey,
} from "../../auth/hooks/use-current-user"
import { getCurrentUser } from "../../auth/api/auth-api"
import type {
    CompanyAccessRole,
    CompanyPermission,
} from "../../auth/types/auth.types"
import {
    createAccountingFirm,
    createClient,
    createCompany,
    deleteAccountingFirm,
    deleteClient,
    listAccountingFirms,
    listClients,
    listCompanies,
    listPlatformUsers,
    replaceCompanyAccess,
    replaceFirmAccess,
    type AccountingFirm,
    type Client,
    type Company,
    type PlatformUser,
} from "../api/platform-admin-api"

import { OfficialUserOnboardingSection } from "../components/official-user-onboarding-section"

const FIRM_ADMIN_PERMISSIONS = [
    "HIERARCHY_MANAGE",
    "USER_ACCESS_MANAGE",
    "ACCOUNTING_SOURCE_MANAGE",
    "CONNECTOR_MANAGE",
] as const

const COMPANY_PERMISSION_OPTIONS: Array<{
    value: CompanyPermission
    label: string
}> = [
    { value: "DASHBOARD_VIEW", label: "Painel" },
    { value: "DRE_VIEW", label: "DRE contábil" },
    { value: "BALANCE_SHEET_VIEW", label: "Balanço patrimonial" },
    { value: "CASH_FLOW_VIEW", label: "Fluxo de caixa" },
    { value: "INDICATORS_VIEW", label: "Indicadores" },
    { value: "ACCOUNTING_MOVEMENT_VIEW", label: "Movimentos contábeis" },
    { value: "ACCOUNT_CLASSIFICATION_VIEW", label: "Classificação contábil — visualizar" },
    { value: "ACCOUNT_CLASSIFICATION_MANAGE", label: "Classificação contábil — gerenciar" },
    { value: "MANUAL_ENTRY_MANAGE", label: "Lançamentos manuais" },
    { value: "QUESTOR_SYNC", label: "Sincronização Questor" },
    { value: "BANK_ACCOUNT_VIEW", label: "Contas bancárias — visualizar" },
    { value: "BANK_ACCOUNT_MANAGE", label: "Contas bancárias — gerenciar" },
    { value: "BUSINESS_PARTNER_VIEW", label: "Clientes/fornecedores — visualizar" },
    { value: "BUSINESS_PARTNER_MANAGE", label: "Clientes/fornecedores — gerenciar" },
    { value: "RESULT_CENTER_VIEW", label: "Centros de resultado — visualizar" },
    { value: "RESULT_CENTER_MANAGE", label: "Centros de resultado — gerenciar" },
    { value: "FINANCIAL_CATEGORY_VIEW", label: "Categorias financeiras — visualizar" },
    { value: "FINANCIAL_CATEGORY_MANAGE", label: "Categorias financeiras — gerenciar" },
    { value: "FINANCIAL_ENTRY_VIEW", label: "Lançamentos financeiros — visualizar" },
    { value: "FINANCIAL_ENTRY_MANAGE", label: "Lançamentos financeiros — gerenciar" },
    { value: "FINANCIAL_PERIOD_VIEW", label: "Períodos financeiros — visualizar" },
    { value: "FINANCIAL_PERIOD_CLOSE", label: "Fechar período financeiro" },
    { value: "FINANCIAL_PERIOD_REOPEN", label: "Reabrir período financeiro" },
]

const READ_ONLY_PERMISSIONS = COMPANY_PERMISSION_OPTIONS
    .map((option) => option.value)
    .filter((permission) => permission.endsWith("_VIEW"))

function describeError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 403) {
            return "Seu usuário não possui o grant necessário para esta operação."
        }
        if (error.status === 409) {
            return "A operação conflita com o estado atual. Confira a ordem dos grants."
        }
        if (error.status === 400) {
            return "Dados inválidos. Confira os campos informados."
        }
    }

    return "Não foi possível concluir a operação."
}

/**
 * Administração do plano SaaS. Não é uma tela financeira:
 * cria a hierarquia e concede grants explícitos antes de liberar Company.
 */
export function PlatformAdminPage() {
    const { user } = useCompanyAccess()
    const queryClient = useQueryClient()

    const [firms, setFirms] = useState<AccountingFirm[]>([])
    const [clients, setClients] = useState<Client[]>([])
    const [companies, setCompanies] = useState<Company[]>([])
    const [users, setUsers] = useState<PlatformUser[]>([])

    const [selectedFirmId, setSelectedFirmId] = useState("")
    const [selectedClientId, setSelectedClientId] = useState("")
    const [selectedCompanyId, setSelectedCompanyId] = useState("")
    const [firmAccessReady, setFirmAccessReady] = useState(false)

    const [firmName, setFirmName] = useState("")
    const [clientName, setClientName] = useState("")
    const [companyName, setCompanyName] = useState("")
    const [companyCnpj, setCompanyCnpj] = useState("")

    const [role, setRole] = useState<CompanyAccessRole>("ADMIN")
    const [permissions, setPermissions] =
        useState<CompanyPermission[]>(READ_ONLY_PERMISSIONS)

    const [message, setMessage] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    const firmSelectionRequest = useRef(0)
    const clientSelectionRequest = useRef(0)

    const platformPermissions = user.platformPermissions ?? []
    const canViewFirms =
        platformPermissions.includes("ACCOUNTING_FIRM_VIEW")
    const canCreateFirm =
        platformPermissions.includes("ACCOUNTING_FIRM_CREATE")
    const canManagePlatformUsers =
        platformPermissions.includes("PLATFORM_USER_MANAGE")

    useEffect(() => {
        let active = true

        if (canViewFirms) {
            void listAccountingFirms()
                .then((page) => {
                    if (active) {
                        setFirms(page.items)
                    }
                })
                .catch((caught) => {
                    if (active) {
                        setError(describeError(caught))
                    }
                })
        }

        if (canManagePlatformUsers) {
            void listPlatformUsers()
                .then((page) => {
                    if (active) {
                        setUsers(page.items)
                    }
                })
                .catch((caught) => {
                    if (active) {
                        setError(describeError(caught))
                    }
                })
        }

        return () => {
            active = false
        }
    }, [canManagePlatformUsers, canViewFirms])

    async function run(action: () => Promise<void>) {
        setBusy(true)
        setError(null)
        setMessage(null)

        try {
            await action()
        } catch (caught) {
            setError(describeError(caught))
        } finally {
            setBusy(false)
        }
    }

    async function selectFirm(firmId: string) {
        const requestId = ++firmSelectionRequest.current
        clientSelectionRequest.current += 1

        setSelectedFirmId(firmId)
        setSelectedClientId("")
        setSelectedCompanyId("")
        setClients([])
        setCompanies([])
        setFirmAccessReady(false)
        setError(null)
        setMessage(null)

        if (!firmId) return

        try {
            const page = await listClients(firmId)

            if (requestId !== firmSelectionRequest.current) {
                return
            }

            setClients(page.items)
            setFirmAccessReady(true)
        } catch (caught) {
            if (requestId !== firmSelectionRequest.current) {
                return
            }

            if (caught instanceof ApiError && caught.status === 403) {
                setMessage(
                    "Escritório selecionado. Conceda explicitamente seu acesso administrativo antes de criar clientes.",
                )
                return
            }
            setError(describeError(caught))
        }
    }

    async function selectClient(clientId: string) {
        const requestId = ++clientSelectionRequest.current

        setSelectedClientId(clientId)
        setSelectedCompanyId("")
        setCompanies([])
        setError(null)
        setMessage(null)

        if (!clientId || !selectedFirmId) return

        try {
            const page = await listCompanies(selectedFirmId, clientId)

            if (requestId !== clientSelectionRequest.current) {
                return
            }

            setCompanies(page.items)
        } catch (caught) {
            if (requestId !== clientSelectionRequest.current) {
                return
            }

            setError(describeError(caught))
        }
    }

    function togglePermission(permission: CompanyPermission) {
        setPermissions((current) =>
            current.includes(permission)
                ? current.filter((item) => item !== permission)
                : [...current, permission],
        )
    }

    return (
        <div style={{ display: "grid", gap: 16 }}>
            <div className="alert i">
                Esta área administra o SaaS. Seu acesso de plataforma não concede
                automaticamente acesso financeiro a nenhuma empresa.
            </div>

            {message && <div className="alert i">{message}</div>}
            {error && <div className="alert e">{error}</div>}

            <section className="mbox">
                <h2>1. Escritório contábil</h2>

                <div className="fl">
                    <label htmlFor="firm-select">Escritório existente</label>
                    <select
                        id="firm-select"
                        value={selectedFirmId}
                        onChange={(event) => void selectFirm(event.target.value)}
                        disabled={busy}
                    >
                        <option value="">Selecione</option>
                        {firms.map((firm) => (
                            <option key={firm.id} value={firm.id}>
                                {firm.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="firm-name">Novo escritório</label>
                    <input
                        id="firm-name"
                        value={firmName}
                        onChange={(event) => setFirmName(event.target.value)}
                        placeholder="Nome do escritório contábil"
                    />
                </div>

                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <button
                        type="button"
                        className="btn"
                        disabled={busy || !canCreateFirm || !firmName.trim()}
                        onClick={() => void run(async () => {
                            const created = await createAccountingFirm(firmName.trim())

                            if (canViewFirms) {
                                const page = await listAccountingFirms()
                                setFirms(page.items)
                            } else {
                                setFirms((current) => [
                                    ...current.filter((firm) => firm.id !== created.id),
                                    created,
                                ])
                            }

                            setFirmName("")
                            await selectFirm(created.id)
                            setMessage(
                                canManagePlatformUsers
                                    ? "Escritório criado. Agora conceda seu acesso administrativo."
                                    : "Escritório criado. Seu perfil não possui permissão para administrar os acessos deste escritório.",
                            )
                        })}
                    >
                        Criar escritório
                    </button>

                    <button
                        type="button"
                        className="btn sec"
                        disabled={
                            busy ||
                            !canManagePlatformUsers ||
                            !selectedFirmId ||
                            firmAccessReady
                        }
                        onClick={() => void run(async () => {
                            await replaceFirmAccess(
                                selectedFirmId,
                                user.id,
                                [...FIRM_ADMIN_PERMISSIONS],
                            )
                            const page = await listClients(selectedFirmId)
                            setClients(page.items)
                            setFirmAccessReady(true)
                            setMessage("Seu grant administrativo do escritório foi concedido.")
                        })}
                    >
                        Conceder meu acesso ao escritório
                    </button>

                    <button
                        type="button"
                        className="btn sec"
                        disabled={
                            busy ||
                            !canCreateFirm ||
                            !canManagePlatformUsers ||
                            !selectedFirmId ||
                            clients.length > 0
                        }
                        onClick={() => {
                            if (!window.confirm(
                                "Excluir o escritório selecionado? A operação só é permitida quando ele está vazio.",
                            )) {
                                return
                            }

                            void run(async () => {
                                const deletedFirmId = selectedFirmId
                                await deleteAccountingFirm(deletedFirmId)

                                if (canViewFirms) {
                                    const page = await listAccountingFirms()
                                    setFirms(page.items)
                                } else {
                                    setFirms((current) =>
                                        current.filter(
                                            (firm) => firm.id !== deletedFirmId,
                                        ),
                                    )
                                }

                                setSelectedFirmId("")
                                setSelectedClientId("")
                                setSelectedCompanyId("")
                                setClients([])
                                setCompanies([])
                                setFirmAccessReady(false)
                                setMessage("Escritório vazio excluído.")
                            })
                        }}
                    >
                        Excluir escritório vazio
                    </button>
                </div>
            </section>

            <section className="mbox">
                <h2>2. Cliente</h2>

                <div className="fl">
                    <label htmlFor="client-select">Cliente existente</label>
                    <select
                        id="client-select"
                        value={selectedClientId}
                        onChange={(event) => void selectClient(event.target.value)}
                        disabled={busy || !firmAccessReady}
                    >
                        <option value="">Selecione</option>
                        {clients.map((client) => (
                            <option key={client.id} value={client.id}>
                                {client.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="client-name">Novo cliente</label>
                    <input
                        id="client-name"
                        value={clientName}
                        onChange={(event) => setClientName(event.target.value)}
                        placeholder="Nome do cliente"
                        disabled={!firmAccessReady}
                    />
                </div>

                <button
                    type="button"
                    className="btn"
                    disabled={busy || !selectedFirmId || !firmAccessReady || !clientName.trim()}
                    onClick={() => void run(async () => {
                        const created = await createClient(selectedFirmId, clientName.trim())
                        const page = await listClients(selectedFirmId)
                        setClients(page.items)
                        setClientName("")
                        await selectClient(created.id)
                        setMessage("Cliente criado.")
                    })}
                >
                    Criar cliente
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={
                        busy ||
                        !selectedFirmId ||
                        !selectedClientId ||
                        !firmAccessReady ||
                        companies.length > 0
                    }
                    onClick={() => {
                        if (!window.confirm(
                            "Excluir o cliente selecionado? A operação só é permitida quando ele não possui empresas.",
                        )) {
                            return
                        }

                        void run(async () => {
                            const deletedClientId = selectedClientId
                            await deleteClient(
                                selectedFirmId,
                                deletedClientId,
                            )

                            const page = await listClients(selectedFirmId)
                            setClients(page.items)
                            setSelectedClientId("")
                            setSelectedCompanyId("")
                            setCompanies([])
                            setMessage("Cliente vazio excluído.")
                        })
                    }}
                >
                    Excluir cliente vazio
                </button>
            </section>

            <section className="mbox">
                <h2>3. Empresa</h2>

                <div className="fl">
                    <label htmlFor="company-select">Empresa existente</label>
                    <select
                        id="company-select"
                        value={selectedCompanyId}
                        onChange={(event) => setSelectedCompanyId(event.target.value)}
                        disabled={busy || !selectedClientId}
                    >
                        <option value="">Selecione</option>
                        {companies.map((company) => (
                            <option key={company.id} value={company.id}>
                                {company.legalName}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="company-name">Razão social</label>
                    <input
                        id="company-name"
                        value={companyName}
                        onChange={(event) => setCompanyName(event.target.value)}
                        disabled={!selectedClientId}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="company-cnpj">CNPJ — 14 dígitos</label>
                    <input
                        id="company-cnpj"
                        inputMode="numeric"
                        value={companyCnpj}
                        onChange={(event) =>
                            setCompanyCnpj(
                                event.target.value.replace(/\D/g, "").slice(0, 14),
                            )
                        }
                        disabled={!selectedClientId}
                    />
                </div>

                <button
                    type="button"
                    className="btn"
                    disabled={
                        busy ||
                        !selectedFirmId ||
                        !selectedClientId ||
                        !companyName.trim() ||
                        companyCnpj.length !== 14
                    }
                    onClick={() => void run(async () => {
                        const created = await createCompany(
                            selectedFirmId,
                            selectedClientId,
                            companyName.trim(),
                            companyCnpj,
                        )
                        const page = await listCompanies(selectedFirmId, selectedClientId)
                        setCompanies(page.items)
                        setSelectedCompanyId(created.id)
                        setCompanyName("")
                        setCompanyCnpj("")
                        setMessage("Empresa criada. Falta conceder o grant operacional.")
                    })}
                >
                    Criar empresa
                </button>
            </section>

            <section className="mbox">
                <h2>4. Meu acesso operacional à empresa</h2>

                <div className="fl">
                    <label htmlFor="company-role">Perfil</label>
                    <select
                        id="company-role"
                        value={role}
                        onChange={(event) =>
                            setRole(event.target.value as CompanyAccessRole)
                        }
                    >
                        <option value="ADMIN">Administrador</option>
                        <option value="ACCOUNTANT">Contador</option>
                        <option value="CLIENT">Cliente</option>
                        <option value="VIEWER">Visualizador</option>
                        <option value="CUSTOM">Personalizado</option>
                    </select>
                </div>

                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 10 }}>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => setPermissions(READ_ONLY_PERMISSIONS)}
                    >
                        Selecionar leitura
                    </button>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() =>
                            setPermissions(
                                COMPANY_PERMISSION_OPTIONS.map((option) => option.value),
                            )
                        }
                    >
                        Selecionar administrador operacional
                    </button>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => setPermissions([])}
                    >
                        Limpar
                    </button>
                </div>

                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns: "repeat(auto-fit, minmax(250px, 1fr))",
                        gap: 8,
                        marginBottom: 12,
                    }}
                >
                    {COMPANY_PERMISSION_OPTIONS.map((option) => (
                        <label key={option.value} style={{ display: "flex", gap: 8 }}>
                            <input
                                type="checkbox"
                                checked={permissions.includes(option.value)}
                                onChange={() => togglePermission(option.value)}
                            />
                            <span>{option.label}</span>
                        </label>
                    ))}
                </div>

                <button
                    type="button"
                    className="btn"
                    disabled={
                        busy ||
                        !firmAccessReady ||
                        !selectedFirmId ||
                        !selectedClientId ||
                        !selectedCompanyId ||
                        permissions.length === 0
                    }
                    onClick={() => void run(async () => {
                        await replaceCompanyAccess(
                            selectedFirmId,
                            selectedClientId,
                            selectedCompanyId,
                            user.id,
                            role,
                            permissions,
                        )

                        const freshUser = await getCurrentUser()
                        queryClient.setQueryData(currentUserQueryKey, freshUser)
                        setMessage(
                            "Grant de Company concedido. A sidebar operacional foi atualizada.",
                        )
                    })}
                >
                    Conceder meu acesso à empresa
                </button>
            </section>

            <OfficialUserOnboardingSection
                key={selectedFirmId || "no-firm"}
                accountingFirmId={selectedFirmId}
                clientId={selectedClientId}
                companyId={selectedCompanyId}
                firmAccessReady={firmAccessReady}
            />

            <section className="mbox">
                <h2>Usuários da plataforma</h2>
                <p className="help">
                    Esta lista é administrativa; visualizar um usuário não concede
                    acesso a escritório ou empresa.
                </p>

                <div style={{ display: "grid", gap: 8 }}>
                    {users.map((platformUser) => (
                        <div className="card" key={platformUser.id}>
                            <b>{platformUser.name}</b>
                            <div>{platformUser.email}</div>
                            <small>
                                {platformUser.invitationPending
                                    ? "Convite pendente"
                                    : platformUser.active
                                      ? "Ativo"
                                      : "Inativo"}
                            </small>
                        </div>
                    ))}
                </div>
            </section>
        </div>
    )
}
