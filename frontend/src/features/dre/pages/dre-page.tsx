import {
    AlertTriangle,
    CalendarRange,
    FileChartColumn,
} from "lucide-react"
import {
    useState,
} from "react"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useDre } from "../hooks/use-dre"
import { Input } from "../../../components/ui/input"
import { Label } from "../../../components/ui/label"
import { Card, CardContent } from "../../../components/ui/card"
import { Skeleton } from "../../../components/ui/skeleton"
import { DreTable } from "../components/dre-table"


function toIsoDate(
    date: Date,
) {
    return date
        .toISOString()
        .slice(0, 10)
}

function createInitialPeriod() {
    const today = new Date()

    const start =
        new Date(
            today.getFullYear(),
            0,
            1,
        )

    return {
        startDate: toIsoDate(start),
        endDate: toIsoDate(today),
    }
}

export function DrePage() {
    const { selectedCompany } =
        useCompanyAccess()

    const [period, setPeriod] =
        useState(createInitialPeriod)

    const dre =
        useDre(
            selectedCompany?.id,
            period,
        )

    return (
        <div className="space-y-6">
            <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
                <div>
                    <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[#b08d49]">
                        <FileChartColumn className="size-4" />
                        Demonstrativo financeiro
                    </div>

                    <h1 className="text-3xl font-semibold tracking-tight text-[var(--tx)]">
                        DRE
                    </h1>

                    <p className="mt-2 text-muted-foreground">
                        Demonstração do resultado da empresa
                        no período selecionado.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row">
                    <div className="space-y-1.5">
                        <Label
                            htmlFor="dre-start-date"
                            className="text-xs text-muted-foreground"
                        >
                            Início
                        </Label>

                        <Input
                            id="dre-start-date"
                            type="date"
                            value={period.startDate}
                            onChange={(event) =>
                                setPeriod(
                                    (current) => ({
                                        ...current,
                                        startDate:
                                            event.target.value,
                                    }),
                                )
                            }
                            className="bg-white"
                        />
                    </div>

                    <div className="space-y-1.5">
                        <Label
                            htmlFor="dre-end-date"
                            className="text-xs text-muted-foreground"
                        >
                            Fim
                        </Label>

                        <Input
                            id="dre-end-date"
                            type="date"
                            value={period.endDate}
                            onChange={(event) =>
                                setPeriod(
                                    (current) => ({
                                        ...current,
                                        endDate:
                                            event.target.value,
                                    }),
                                )
                            }
                            className="bg-white"
                        />
                    </div>
                </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
                <Card>
                    <CardContent className="flex items-center gap-4 p-5">
                        <CalendarRange className="size-5 text-[#568fd2]" />

                        <div>
                            <p className="text-xs text-muted-foreground">
                                Empresa
                            </p>

                            <p className="mt-1 text-sm font-medium">
                                {selectedCompany?.name ?? "-"}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="flex items-center gap-4 p-5">
                        <FileChartColumn className="size-5 text-[#299d8f]" />

                        <div>
                            <p className="text-xs text-muted-foreground">
                                Linhas classificadas
                            </p>

                            <p className="mt-1 text-xl font-semibold">
                                {dre.data?.lines.length ?? "-"}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="flex items-center gap-4 p-5">
                        <AlertTriangle className="size-5 text-[#d77c67]" />

                        <div>
                            <p className="text-xs text-muted-foreground">
                                Contas sem classificação
                            </p>

                            <p className="mt-1 text-xl font-semibold">
                                {
                                    dre.data
                                        ?.unclassifiedAccountCount ??
                                    "-"
                                }
                            </p>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {dre.isLoading && (
                <div className="space-y-3">
                    <Skeleton className="h-14 w-full" />
                    <Skeleton className="h-16 w-full" />
                    <Skeleton className="h-16 w-full" />
                    <Skeleton className="h-16 w-full" />
                </div>
            )}

            {dre.isError && (
                <Card className="border-destructive/30 bg-destructive/5">
                    <CardContent className="p-6 text-sm text-destructive">
                        Não foi possível carregar a DRE
                        para o período selecionado.
                    </CardContent>
                </Card>
            )}

            {dre.data && (
                <DreTable
                    lines={dre.data.lines}
                />
            )}
        </div>
    )
}