import { useEffect, useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ConfirmDialog } from "../../../components/finemp/confirm-dialog"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useCompanyAccess } from "../../companies/company-access-context"
import { BusinessPartnerDialog } from "../components/business-partner-dialog"
import {
    useBusinessPartnerMutations,
    useBusinessPartners,
} from "../hooks/use-business-partners"
import { canManageBusinessPartners } from "../lib/business-partner-access"
import { describeBusinessPartnerError } from "../lib/business-partner-errors"
import {
    PARTNER_ROLE_LABELS,
    formatPartnerDocument,
} from "../lib/business-partner-form"
import {
    defaultBusinessPartnerFilter,
    type BusinessPartner,
    type BusinessPartnerFilter,
} from "../types/business-partner.types"

const listErrorMessages = {
    400: "Confira a busca.",
    403: "Você não tem acesso aos clientes e fornecedores desta empresa.",
    404: "Clientes e fornecedores não estão disponíveis neste ambiente.",
}

const roleBadge = { CUSTOMER: "b-ok", SUPPLIER: "b-bad", BOTH: "b-gold" } as const

type DialogState =
    | { mode: "form", partner?: BusinessPartner }
    | { mode: "delete", partner: BusinessPartner }

/**
 * Cadastros › Clientes e fornecedores (renderParceiros do protótipo):
 * parceiros comerciais da Company. Leitura com BUSINESS_PARTNER_VIEW ou
 * _MANAGE; incluir, editar e excluir com _MANAGE. Totais recebidos/pagos
 * por parceiro, ficha comercial, consulta à Receita e pendentes de
 * documento não fazem parte desta tela.
 */
export function BusinessPartnersPage() {
    const { selectedCompany } = useCompanyAccess()

    // O estado da tela pertence à Company ativa.
    return <BusinessPartnersCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function BusinessPartnersCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = canManageBusinessPartners(hasPermission)
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<BusinessPartnerFilter>(defaultBusinessPartnerFilter)
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState | null>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const partners = useBusinessPartners(companyId, filter, page)
    const { remove } = useBusinessPartnerMutations(companyId ?? "")

    useEffect(() => {
        if (searchText === filter.search) {
            return
        }

        const timer = window.setTimeout(() => setFilter((current) => ({ ...current, search: searchText })), 350)

        return () => window.clearTimeout(timer)
    }, [searchText, filter.search])

    // Uma mutação pode tirar a última linha da página atual (ex.: inativar
    // ou mudar o papel de um parceiro). Quando o total de páginas encolher,
    // volta para a última página ainda válida em vez de prender a tela vazia.
    useEffect(() => {
        const totalPages = partners.data?.totalPages

        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [partners.data?.totalPages, page, setPage])

    async function confirmDelete(partner: BusinessPartner) {
        try {
            const result = await remove.mutateAsync(partner)
            showToast(
                result.action === "DEACTIVATED"
                    ? `${partner.profile.name} tem lançamentos: foi inativado (o histórico continua).`
                    : "Cadastro excluído.",
            )
        } catch (error) {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            showToast(describeBusinessPartnerError(error, "delete"), true)
        } finally {
            setDialog(null)
        }
    }

    const data = partners.data
    const items = data?.items ?? []
    const filtered = filter.role !== "" || filter.active !== "true" || filter.search !== ""

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="pfPapel">Mostrar</label>
                    <select
                        id="pfPapel"
                        value={filter.role}
                        onChange={(event) => setFilter({ ...filter, role: event.target.value as BusinessPartnerFilter["role"] })}
                    >
                        <option value="">Todos</option>
                        <option value="CUSTOMER">Clientes</option>
                        <option value="SUPPLIER">Fornecedores</option>
                        <option value="BOTH">Só clientes e fornecedores</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="pfSit">Situação</label>
                    <select
                        id="pfSit"
                        value={filter.active}
                        onChange={(event) => setFilter({ ...filter, active: event.target.value as BusinessPartnerFilter["active"] })}
                    >
                        <option value="true">Ativos</option>
                        <option value="false">Inativos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="pfBusca">Buscar</label>
                    <input
                        type="search"
                        id="pfBusca"
                        maxLength={100}
                        placeholder="Nome, CNPJ, cidade…"
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                    />
                </div>

                {canManage ? (
                    <div className="f">
                        <label>&nbsp;</label>
                        <button
                            type="button"
                            className="btn"
                            onClick={() => setDialog({ mode: "form" })}
                        >
                            + Cliente / fornecedor
                        </button>
                    </div>
                ) : null}
            </div>

            {partners.isError ? (
                <QueryError
                    error={partners.error}
                    messages={listErrorMessages}
                    fallback={describeBusinessPartnerError(partners.error, "list")}
                    onRetry={() => partners.refetch()}
                />
            ) : (
                <div
                    className="panel"
                    style={{ padding: 0 }}
                >
                    <div
                        className="tblwrap"
                        style={partners.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>CNPJ / CPF</th>
                                    <th>Nome</th>
                                    <th>Papel</th>
                                    <th>Contato</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((partner) => {
                                    const profile = partner.profile
                                    const contact = [profile.phone, profile.email].filter(Boolean)
                                    const place = [profile.city, profile.stateCode].filter(Boolean).join("/")

                                    return (
                                        <tr key={partner.id}>
                                            <td style={{ whiteSpace: "nowrap" }}>{formatPartnerDocument(profile.document)}</td>
                                            <td>
                                                {profile.name}
                                                {profile.tradeName ? (
                                                    <div className="mut" style={{ fontSize: 12 }}>{profile.tradeName}</div>
                                                ) : null}
                                                {profile.active ? null : (
                                                    <>
                                                        {" "}
                                                        <span className="badge b-neu">inativo</span>
                                                    </>
                                                )}
                                            </td>
                                            <td>
                                                <span className={`badge ${roleBadge[profile.role]}`}>
                                                    {PARTNER_ROLE_LABELS[profile.role]}
                                                </span>
                                            </td>
                                            <td style={{ fontSize: 13 }}>
                                                {contact.map((item) => <div key={item}>{item}</div>)}
                                                {place ? <div className="mut">{place}</div> : null}
                                            </td>
                                            {canManage ? (
                                                <td style={{ whiteSpace: "nowrap" }}>
                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        onClick={() => setDialog({ mode: "form", partner })}
                                                    >
                                                        Editar
                                                    </button>{" "}
                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        aria-label={`Excluir ${profile.name}`}
                                                        disabled={remove.isPending}
                                                        onClick={() => setDialog({ mode: "delete", partner })}
                                                    >
                                                        ✕
                                                    </button>
                                                </td>
                                            ) : null}
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>
                    </div>

                    {partners.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            {filtered
                                ? "Nenhum cadastro com esses filtros."
                                : canManage
                                    ? "Nenhum cliente ou fornecedor cadastrado. Clique em \"+ Cliente / fornecedor\"."
                                    : "Nenhum cliente ou fornecedor cadastrado."}
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={data?.totalPages ?? 0}
                        disabled={partners.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog?.mode === "form" && companyId ? (
                <BusinessPartnerDialog
                    key={dialog.partner ? `${dialog.partner.id}-${dialog.partner.version}` : "new"}
                    companyId={companyId}
                    partner={dialog.partner}
                    onClose={() => setDialog(null)}
                />
            ) : null}

            {dialog?.mode === "delete" ? (
                <ConfirmDialog
                    title="Excluir cadastro"
                    message={`"${dialog.partner.profile.name}" será removido. Se tiver lançamentos, será só inativado e o histórico continua.`}
                    confirmLabel="Excluir"
                    busy={remove.isPending}
                    onConfirm={() => confirmDelete(dialog.partner)}
                    onCancel={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}
