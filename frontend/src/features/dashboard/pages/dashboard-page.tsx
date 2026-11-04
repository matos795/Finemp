import {
    BarChart3,
    Building2,
    ShieldCheck,
} from "lucide-react"
import { useCompanyAccess } from "../../companies/company-access-context"
import { Badge } from "../../../components/ui/badge"
import { Card, CardContent } from "../../../components/ui/card"

export function DashboardPage() {
    const { selectedCompany } =
        useCompanyAccess()

    return (
        <div className="space-y-8">
            <div>
                <div className="flex items-center gap-3">
                    <h1 className="text-3xl font-semibold tracking-tight text-[var(--tx)]">
                        Visão geral
                    </h1>

                    {selectedCompany && (
                        <Badge variant="outline">
                            {selectedCompany.role}
                        </Badge>
                    )}
                </div>

                <p className="mt-2 text-muted-foreground">
                    Acompanhe os principais dados financeiros
                    disponíveis para o seu perfil.
                </p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardContent className="flex items-center gap-4 p-6">
                        <Building2 className="size-5 text-[#c7a35a]" />

                        <div>
                            <p className="text-sm text-muted-foreground">
                                Empresa
                            </p>

                            <p className="font-medium">
                                {selectedCompany?.name ?? "Nenhuma"}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="flex items-center gap-4 p-6">
                        <ShieldCheck className="size-5 text-[#299d8f]" />

                        <div>
                            <p className="text-sm text-muted-foreground">
                                Perfil
                            </p>

                            <p className="font-medium">
                                {selectedCompany?.role ?? "-"}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="flex items-center gap-4 p-6">
                        <BarChart3 className="size-5 text-[#568fd2]" />

                        <div>
                            <p className="text-sm text-muted-foreground">
                                Funcionalidades
                            </p>

                            <p className="font-medium">
                                {selectedCompany?.permissions.length ?? 0}
                                {" "}liberadas
                            </p>
                        </div>
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}