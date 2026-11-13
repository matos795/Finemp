import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getConsistencyDiagnostics } from "../api/reports-api"

export function ConsistencyDiagnosticsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <CompanyConsistencyDiagnostics
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function CompanyConsistencyDiagnostics() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id

    const report = useQuery({
        queryKey: ["consistency-diagnostics", companyId] as const,
        queryFn: () => getConsistencyDiagnostics(companyId!),
        enabled: Boolean(companyId),
        retry: false,
    })

    const data = report.isError ? undefined : report.data

    if (!selectedCompany) {
        return (
            <Panel
                title="Verificação de consistência"
                help="Diagnóstico somente leitura dos vínculos da empresa selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para executar as verificações.
                </div>
            </Panel>
        )
    }

    return (
        <>
            <Panel
                title="Verificação de consistência"
                help="Diagnóstico somente leitura. O FINEMP aponta inconsistências; esta tela não altera nem corrige dados automaticamente."
            >
                <div className="alert i">
                    <b>Cobertura parcial:</b> nesta etapa são verificados Contratos,
                    Notas fiscais de serviço e seus vínculos referenciais. NCG,
                    Balanço, Estoque, Bens e demais verificações homologadas
                    continuam fora desta cobertura.
                </div>
            </Panel>

            {report.isError ? (
                <QueryError
                    error={report.error}
                    messages={{
                        403: "Seu acesso precisa incluir leitura de Contratos e Notas fiscais desta empresa.",
                    }}
                    fallback="Não foi possível executar a verificação de consistência."
                    onRetry={() => report.refetch()}
                />
            ) : null}

            {report.isPending && report.fetchStatus !== "idle" ? (
                <div className="empty">Verificando…</div>
            ) : null}

            {data ? (
                <>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Ocorrências encontradas</div>
                            <div className="vl">{data.issueCount}</div>
                            <div className="sub">
                                {data.issueCount === 0
                                    ? "Nenhuma inconsistência nesta cobertura"
                                    : "Requer conferência; nada foi alterado"}
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Verificações executadas</div>
                            <div className="vl">{data.checks.length}</div>
                            <div className="sub">
                                Cobertura parcial e explícita
                            </div>
                        </div>
                    </div>

                    <Panel
                        title="Resultado das verificações"
                        help="Os IDs abaixo são identificadores técnicos para rastreio. Nenhum dado pessoal de parceiro é exposto por este relatório."
                    >
                        <div className="tblwrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Verificação</th>
                                        <th>Situação</th>
                                        <th className="n">Ocorrências</th>
                                        <th>IDs técnicos</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {data.checks.map((check) => (
                                        <tr key={check.code}>
                                            <td>{check.label}</td>
                                            <td>
                                                <span
                                                    className={
                                                        check.status === "OK"
                                                            ? "badge b-ok"
                                                            : "badge b-bad"
                                                    }
                                                >
                                                    {check.status === "OK"
                                                        ? "Consistente"
                                                        : "Revisar"}
                                                </span>
                                            </td>
                                            <td className="n">
                                                {check.occurrenceCount}
                                            </td>
                                            <td>
                                                {check.occurrenceIds.length === 0
                                                    ? "—"
                                                    : check.occurrenceIds.join(", ")}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Panel>
                </>
            ) : null}
        </>
    )
}
