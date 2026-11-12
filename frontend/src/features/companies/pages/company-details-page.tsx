import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyAccess } from "../company-access-context"
import { getCompanyProfile } from "../api/company-profile-api"

function formatCnpj(value: string) {
    if (!/^\d{14}$/.test(value)) return value

    return value.replace(
        /^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/,
        "$1.$2.$3/$4-$5",
    )
}

export function CompanyDetailsPage() {
    const { selectedCompany } = useCompanyAccess()

    const query = useQuery({
        queryKey: ["company-profile", selectedCompany?.id] as const,
        queryFn: () => getCompanyProfile(selectedCompany!.id),
        enabled: selectedCompany !== null,
        retry: false,
    })

    if (!selectedCompany) {
        return (
            <Panel
                title="Dados da empresa"
                help="Perfil cadastral da Company selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para consultar seus dados cadastrais.
                </div>
            </Panel>
        )
    }

    return (
        <>
            <Panel
                title="Dados da empresa"
                help="Perfil cadastral da Company selecionada. Esta primeira entrega é somente leitura."
            >
                <p className="help">
                    Os dados abaixo identificam a empresa no FINEMP. Alterações
                    cadastrais não estão disponíveis nesta etapa.
                </p>
            </Panel>

            {query.isError ? (
                <QueryError
                    error={query.error}
                    messages={{
                        403: "Seu acesso não permite consultar esta empresa.",
                        404: "A empresa selecionada não foi encontrada.",
                    }}
                    fallback="Não foi possível consultar os dados da empresa."
                    onRetry={() => query.refetch()}
                />
            ) : null}

            {query.isPending ? (
                <div className="empty">Carregando dados da empresa…</div>
            ) : null}

            {query.data ? (
                <div className="cards">
                    <div className="card">
                        <div className="lb">Razão social</div>
                        <div className="vl">
                            {query.data.legalName}
                        </div>
                        <div className="sub">
                            Nome jurídico cadastrado
                        </div>
                    </div>

                    <div className="card">
                        <div className="lb">CNPJ</div>
                        <div className="vl">
                            {formatCnpj(query.data.cnpj)}
                        </div>
                        <div className="sub">
                            Documento da Company
                        </div>
                    </div>

                    <div className="card">
                        <div className="lb">Identificador</div>
                        <div className="sub">
                            <code>{query.data.id}</code>
                        </div>
                    </div>
                </div>
            ) : null}
        </>
    )
}
