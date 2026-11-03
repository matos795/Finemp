import { useCompanyAccess } from "../../companies/company-access-context"

/**
 * Página inicial. O Painel homologado (saldos, entradas, saídas,
 * projeção e indicadores) depende de dados que a API ainda não fornece;
 * até lá esta tela mostra só o acesso do usuário, no visual do protótipo.
 */
export function DashboardPage() {
    const { selectedCompany } =
        useCompanyAccess()

    return (
        <>
            <div className="cards">
                <div className="card">
                    <div className="lb">Empresa</div>
                    <div className="vl" style={{ fontSize: 20 }}>
                        {selectedCompany?.name ?? "Nenhuma"}
                    </div>
                </div>

                <div className="card">
                    <div className="lb">Perfil</div>
                    <div className="vl" style={{ fontSize: 20 }}>
                        {selectedCompany?.role ?? "—"}
                    </div>
                </div>

                <div className="card">
                    <div className="lb">Funcionalidades</div>
                    <div className="vl" style={{ fontSize: 20 }}>
                        {selectedCompany?.permissions.length ?? 0} liberadas
                    </div>
                </div>
            </div>

            <div className="alert i">
                Os quadros do Painel (saldo disponível, entradas, saídas,
                projeção e indicadores) serão exibidos quando os dados
                estiverem disponíveis na API.
            </div>
        </>
    )
}
