import { useCompanyAccess } from "../company-access-context"

const roleLabels = {
    CUSTOM: "Personalizado",
    ADMIN: "Administrador",
    ACCOUNTANT: "Contador",
    CLIENT: "Cliente",
    VIEWER: "Consulta",
} as const

export function CompaniesPage() {
    const {
        user,
        selectedCompany,
        selectCompany,
    } = useCompanyAccess()

    return (
        <div>
            <section className="mbox">
                <h1>Empresas</h1>
                <p className="help">
                    Companies disponíveis para sua conta. Esta tela mostra
                    acesso já concedido; ela não cria empresas nem altera
                    permissões.
                </p>
            </section>

            <section className="mbox">
                {user.companies.length === 0 ? (
                    <div className="alert i">
                        Nenhuma empresa está disponível para esta conta.
                        Um administrador precisa conceder acesso, ou a
                        estrutura precisa ser criada no Onboarding SaaS.
                    </div>
                ) : (
                    <div style={{ display: "grid", gap: 10 }}>
                        {user.companies.map((company) => {
                            const active =
                                selectedCompany?.id === company.id

                            return (
                                <article
                                    className="card"
                                    key={company.id}
                                >
                                    <strong>{company.name}</strong>

                                    <div>
                                        Perfil:{" "}
                                        {roleLabels[company.role]}
                                    </div>

                                    <small>
                                        Escopo de centros:{" "}
                                        {company.costCenterScope === "ALL"
                                            ? "Todos"
                                            : "Restrito"}
                                        {" · "}
                                        {company.permissions.length}{" "}
                                        permissões explícitas
                                    </small>

                                    <div style={{ marginTop: 8 }}>
                                        <button
                                            className={
                                                active
                                                    ? "btn sec"
                                                    : "btn"
                                            }
                                            type="button"
                                            disabled={active}
                                            onClick={() =>
                                                selectCompany(
                                                    company.id,
                                                )
                                            }
                                        >
                                            {active
                                                ? "Empresa ativa"
                                                : "Usar esta empresa"}
                                        </button>
                                    </div>
                                </article>
                            )
                        })}
                    </div>
                )}
            </section>
        </div>
    )
}
