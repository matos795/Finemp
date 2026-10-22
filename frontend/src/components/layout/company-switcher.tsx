import { useCompanyAccess } from "../../features/companies/company-access-context"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../ui/select"


export function CompanySwitcher() {
    const {
        user,
        selectedCompany,
        selectCompany,
    } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <span className="text-sm text-muted-foreground">
                Nenhuma empresa disponível
            </span>
        )
    }

    return (
        <Select
            value={selectedCompany.id}
            onValueChange={selectCompany}
        >
            <SelectTrigger className="w-[260px] bg-white">
                <SelectValue />
            </SelectTrigger>

            <SelectContent>
                {user.companies.map((company) => (
                    <SelectItem
                        key={company.id}
                        value={company.id}
                    >
                        {company.name}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    )
}