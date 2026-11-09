import { useEffect, useMemo, useState } from "react"
import { ApiError } from "../../../lib/api/api-client"
import type {
    CompanyAccessRole,
    CompanyPermission,
} from "../../auth/types/auth.types"
import {
    inviteFirmUser,
    listFirmUsers,
    replaceCompanyAccess,
    type FirmPermission,
    type PlatformUser,
} from "../api/platform-admin-api"

const FIRM_PERMISSION_OPTIONS: Array<{
    value: FirmPermission
    label: string
}> = [
    { value: "HIERARCHY_MANAGE", label: "Gerenciar clientes e empresas" },
    { value: "USER_ACCESS_MANAGE", label: "Gerenciar usuários e acessos" },
    { value: "ACCOUNTING_SOURCE_MANAGE", label: "Gerenciar fontes contábeis" },
    { value: "CONNECTOR_MANAGE", label: "Gerenciar Connector" },
]

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
    { value: "MANUAL_ENTRY_MANAGE", label: "Lançamentos contábeis manuais" },
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

const ACCOUNTANT_SUGGESTION: CompanyPermission[] = [
    "DASHBOARD_VIEW",
    "DRE_VIEW",
    "BALANCE_SHEET_VIEW",
    "CASH_FLOW_VIEW",
    "INDICATORS_VIEW",
    "ACCOUNTING_MOVEMENT_VIEW",
    "ACCOUNT_CLASSIFICATION_VIEW",
    "ACCOUNT_CLASSIFICATION_MANAGE",
    "MANUAL_ENTRY_MANAGE",
    "BANK_ACCOUNT_VIEW",
    "BUSINESS_PARTNER_VIEW",
    "RESULT_CENTER_VIEW",
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_PERIOD_VIEW",
]

const CLIENT_FINANCE_SUGGESTION: CompanyPermission[] = [
    "DASHBOARD_VIEW",
    "CASH_FLOW_VIEW",
    "INDICATORS_VIEW",
    "BANK_ACCOUNT_VIEW",
    "BUSINESS_PARTNER_VIEW",
    "RESULT_CENTER_VIEW",
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
    "FINANCIAL_PERIOD_VIEW",
]

function describeError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 404) {
            return "Convites por e-mail não estão habilitados neste ambiente."
        }
        if (error.status === 403) {
            return "Seu usuário não possui permissão para administrar esses acessos."
        }
        if (error.status === 409) {
            return "O acesso solicitado conflita com o estado atual. Confira o vínculo com o escritório."
        }
        if (error.status === 400) {
            return "Dados inválidos. Confira nome, e-mail e permissões."
        }
    }

    return "Não foi possível concluir o onboarding do usuário."
}

interface Props {
    accountingFirmId: string
    clientId: string
    companyId: string
    firmAccessReady: boolean
}

export function OfficialUserOnboardingSection({
    accountingFirmId,
    clientId,
    companyId,
    firmAccessReady,
}: Props) {
    const [users, setUsers] = useState<PlatformUser[]>([])
    const [selectedUserId, setSelectedUserId] = useState("")
    const [inviteName, setInviteName] = useState("")
    const [inviteEmail, setInviteEmail] = useState("")
    const [firmPermissions, setFirmPermissions] =
        useState<FirmPermission[]>([])

    const [role, setRole] =
        useState<CompanyAccessRole>("ACCOUNTANT")
    const [permissions, setPermissions] =
        useState<CompanyPermission[]>(ACCOUNTANT_SUGGESTION)

    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)

    const selectedUser = useMemo(
        () => users.find((user) => user.id === selectedUserId),
        [selectedUserId, users],
    )

    useEffect(() => {
        let active = true

        if (!accountingFirmId || !firmAccessReady) {
            return () => {
                active = false
            }
        }

        void listFirmUsers(accountingFirmId)
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

        return () => {
            active = false
        }
    }, [accountingFirmId, firmAccessReady])

    function toggleFirmPermission(permission: FirmPermission) {
        setFirmPermissions((current) =>
            current.includes(permission)
                ? current.filter((item) => item !== permission)
                : [...current, permission],
        )
    }

    function toggleCompanyPermission(permission: CompanyPermission) {
        setPermissions((current) =>
            current.includes(permission)
                ? current.filter((item) => item !== permission)
                : [...current, permission],
        )
    }

    function applySuggestion(
        nextRole: CompanyAccessRole,
        nextPermissions: CompanyPermission[],
    ) {
        setRole(nextRole)
        setPermissions([...nextPermissions])
    }

    async function run(action: () => Promise<void>) {
        setBusy(true)
        setMessage(null)
        setError(null)

        try {
            await action()
        } catch (caught) {
            setError(describeError(caught))
        } finally {
            setBusy(false)
        }
    }

    return (
        <section className="mbox">
            <h2>5. Usuários oficiais do escritório e da empresa</h2>

            <p className="help">
                O convite cria a identidade e o vínculo com o escritório.
                Permissões do escritório e da empresa são independentes.
                Um cliente pode ser convidado sem nenhuma permissão administrativa
                do escritório e receber somente o acesso necessário à empresa.
            </p>

            {message && <div className="alert i">{message}</div>}
            {error && <div className="alert e">{error}</div>}

            <h3>Convidar usuário</h3>

            <div className="fl">
                <label htmlFor="official-user-name">Nome</label>
                <input
                    id="official-user-name"
                    value={inviteName}
                    onChange={(event) => setInviteName(event.target.value)}
                    disabled={!firmAccessReady || busy}
                />
            </div>

            <div className="fl">
                <label htmlFor="official-user-email">E-mail</label>
                <input
                    id="official-user-email"
                    type="email"
                    value={inviteEmail}
                    onChange={(event) => setInviteEmail(event.target.value)}
                    disabled={!firmAccessReady || busy}
                />
            </div>

            <p className="help">
                Permissões administrativas no escritório. Para usuário cliente,
                mantenha vazio salvo decisão explícita em contrário.
            </p>

            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
                    gap: 8,
                    marginBottom: 12,
                }}
            >
                {FIRM_PERMISSION_OPTIONS.map((option) => (
                    <label key={option.value} style={{ display: "flex", gap: 8 }}>
                        <input
                            type="checkbox"
                            checked={firmPermissions.includes(option.value)}
                            onChange={() => toggleFirmPermission(option.value)}
                            disabled={!firmAccessReady || busy}
                        />
                        <span>{option.label}</span>
                    </label>
                ))}
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() => setFirmPermissions([])}
                >
                    Sem administração do escritório
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() =>
                        setFirmPermissions(
                            FIRM_PERMISSION_OPTIONS.map((option) => option.value),
                        )
                    }
                >
                    Administrador do escritório
                </button>

                <button
                    type="button"
                    className="btn"
                    disabled={
                        busy ||
                        !firmAccessReady ||
                        !accountingFirmId ||
                        !inviteName.trim() ||
                        !inviteEmail.trim()
                    }
                    onClick={() => void run(async () => {
                        const normalizedEmail = inviteEmail.trim().toLowerCase()

                        await inviteFirmUser(
                            accountingFirmId,
                            inviteName.trim(),
                            normalizedEmail,
                            firmPermissions,
                        )

                        const page = await listFirmUsers(accountingFirmId)
                        setUsers(page.items)

                        const invited = page.items.find(
                            (user) =>
                                user.email.trim().toLowerCase() === normalizedEmail,
                        )

                        setSelectedUserId(invited?.id ?? "")
                        setInviteName("")
                        setInviteEmail("")
                        setMessage(
                            "Convite emitido. O usuário define a própria senha pelo link recebido.",
                        )
                    })}
                >
                    Enviar convite
                </button>
            </div>

            <hr style={{ margin: "20px 0" }} />

            <h3>Conceder acesso à empresa</h3>

            <div className="fl">
                <label htmlFor="official-user-select">Usuário do escritório</label>
                <select
                    id="official-user-select"
                    value={selectedUserId}
                    onChange={(event) => setSelectedUserId(event.target.value)}
                    disabled={!firmAccessReady || busy || users.length === 0}
                >
                    <option value="">Selecione</option>
                    {users.map((user) => (
                        <option key={user.id} value={user.id}>
                            {user.name} · {user.email}
                            {user.invitationPending ? " · convite pendente" : ""}
                        </option>
                    ))}
                </select>
            </div>

            {selectedUser && (
                <div className="alert i">
                    {selectedUser.invitationPending
                        ? "Convite pendente: o grant pode ser preparado agora, mas o usuário só entra após ativar a conta."
                        : selectedUser.active
                          ? "Usuário ativo."
                          : "Usuário ainda não está ativo."}
                </div>
            )}

            <div className="fl">
                <label htmlFor="official-user-role">Perfil descritivo</label>
                <select
                    id="official-user-role"
                    value={role}
                    onChange={(event) =>
                        setRole(event.target.value as CompanyAccessRole)
                    }
                    disabled={busy}
                >
                    <option value="ACCOUNTANT">Contador</option>
                    <option value="CLIENT">Cliente</option>
                    <option value="VIEWER">Visualizador</option>
                    <option value="ADMIN">Administrador</option>
                    <option value="CUSTOM">Personalizado</option>
                </select>
            </div>

            <p className="help">
                Os atalhos abaixo são sugestões editáveis de UX. O backend continua
                autorizando cada permissão explicitamente; escolher o perfil não
                concede nada sozinho.
            </p>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() =>
                        applySuggestion("ACCOUNTANT", ACCOUNTANT_SUGGESTION)
                    }
                >
                    Sugestão: contador
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() =>
                        applySuggestion("CLIENT", CLIENT_FINANCE_SUGGESTION)
                    }
                >
                    Sugestão: cliente financeiro
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() =>
                        applySuggestion("VIEWER", READ_ONLY_PERMISSIONS)
                    }
                >
                    Somente leitura
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={() =>
                        applySuggestion(
                            "ADMIN",
                            COMPANY_PERMISSION_OPTIONS.map((option) => option.value),
                        )
                    }
                >
                    Administrador operacional
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
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
                            onChange={() => toggleCompanyPermission(option.value)}
                            disabled={busy}
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
                    !accountingFirmId ||
                    !clientId ||
                    !companyId ||
                    !selectedUserId ||
                    permissions.length === 0
                }
                onClick={() => void run(async () => {
                    await replaceCompanyAccess(
                        accountingFirmId,
                        clientId,
                        companyId,
                        selectedUserId,
                        role,
                        permissions,
                    )

                    setMessage(
                        "Acesso da empresa concedido ao usuário selecionado.",
                    )
                })}
            >
                Conceder acesso à empresa
            </button>
        </section>
    )
}
