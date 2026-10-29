import { useCompanyAccess } from "../../features/companies/company-access-context"

/** Seletor de empresa do topo (protótipo: .f > label + select). */
export function CompanySwitcher() {
    const {
        user,
        selectedCompany,
        selectCompany,
    } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <span className="mut">
                Nenhuma empresa disponível
            </span>
        )
    }

    return (
        <div className="f">
            <label htmlFor="selEmp">
                Empresa
            </label>

            <select
                id="selEmp"
                style={{ minWidth: 180 }}
                value={selectedCompany.id}
                onChange={(event) =>
                    selectCompany(event.target.value)
                }
            >
                {user.companies.map((company) => (
                    <option
                        key={company.id}
                        value={company.id}
                    >
                        {company.name}
                    </option>
                ))}
            </select>
        </div>
    )
}
