import {
    useMemo,
    useRef,
    useState,
} from "react"

import {
    Search,
    Tags,
    Undo2,
    Upload,
} from "lucide-react"

import { Badge } from "../../../components/ui/badge"
import { Button } from "../../../components/ui/button"
import {
    Card,
    CardContent,
    CardHeader,
    CardTitle,
} from "../../../components/ui/card"
import { Input } from "../../../components/ui/input"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "../../../components/ui/select"

import { useCompanyAccess } from "../../companies/company-access-context"

import {
    useAccountClassifications,
    useApplyClassificationImport,
    useClassificationImportPreview,
    useClassifyAccount,
    useDreClassifications,
    useUnclassifyAccount,
} from "../hooks/use-classification"
import { ClassificationImportPreviewSheet } from "../components/classification-import-preview-sheet"

type StatusFilter =
    | "ALL"
    | "PENDING"
    | "CLASSIFIED"

export function ClassificationPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId =
        selectedCompany?.id

    const [search, setSearch] =
        useState("")

    const [statusFilter, setStatusFilter] =
        useState<StatusFilter>("PENDING")

    const [selectedClassifications, setSelectedClassifications] =
        useState<Record<string, string>>({})

    const accountsQuery =
        useAccountClassifications(companyId)

    const catalogQuery =
        useDreClassifications(companyId)

    const classifyMutation =
        useClassifyAccount(companyId ?? "")

    const unclassifyMutation =
        useUnclassifyAccount(companyId ?? "")

    const fileInputRef =
        useRef<HTMLInputElement>(null)

    const importPreview =
        useClassificationImportPreview(
            companyId ?? "",
        )

    const [importFile, setImportFile] =
        useState<File | null>(null)

    const applyImport =
        useApplyClassificationImport(
            companyId ?? "",
        )

    const [previewOpen, setPreviewOpen] =
        useState(false)

    const [page, setPage] =
        useState(0)

    const accounts = useMemo(
        () => accountsQuery.data ?? [],
        [accountsQuery.data]
    )

    const catalog =
        catalogQuery.data ?? []

    const filteredAccounts =
        useMemo(() => {
            const normalizedSearch =
                search.trim().toLowerCase()

            return accounts.filter(
                (account) => {
                    const matchesStatus =
                        statusFilter === "ALL"
                        || account.status === statusFilter

                    const matchesSearch =
                        normalizedSearch === ""
                        || account.accountKey
                            .toLowerCase()
                            .includes(normalizedSearch)
                        || account.description
                            .toLowerCase()
                            .includes(normalizedSearch)

                    return (
                        matchesStatus
                        && matchesSearch
                    )
                },
            )
        }, [
            accounts,
            search,
            statusFilter,
        ])

    if (!selectedCompany) {
        return null
    }

    if (
        accountsQuery.isLoading
        || catalogQuery.isLoading
    ) {
        return (
            <div className="p-8">
                Carregando classificações...
            </div>
        )
    }

    if (
        accountsQuery.isError
        || catalogQuery.isError
    ) {
        return (
            <div className="p-8">
                Não foi possível carregar as classificações.
            </div>
        )
    }

    const pendingCount =
        accounts.filter(
            (account) =>
                account.status === "PENDING",
        ).length

    const classifiedCount =
        accounts.length - pendingCount

    function handleFileChange(
        event: React.ChangeEvent<HTMLInputElement>,
    ) {
        const file =
            event.target.files?.[0]

        if (!file) {
            return
        }

        setImportFile(file)

        importPreview.mutate(
            file,
            {
                onSuccess: () => {
                    setPreviewOpen(true)
                },
            },
        )

        event.target.value = ""
    }

    function handleApplyImport() {
        if (!importFile) {
            return
        }

        applyImport.mutate(
            importFile,
            {
                onSuccess: () => {
                    setPreviewOpen(false)
                    setImportFile(null)
                    importPreview.reset()
                    setSelectedClassifications({})
                },
            },
        )
    }

    function handlePreviewOpenChange(
        open: boolean,
    ) {
        if (applyImport.isPending) {
            return
        }

        setPreviewOpen(open)

        if (!open) {
            setImportFile(null)
            importPreview.reset()
        }
    }

    return (
        <div className="space-y-6">
            <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">

                <div>
                    <p className="text-sm text-muted-foreground">
                        Contabilidade
                    </p>

                    <h1 className="text-2xl font-semibold tracking-tight">
                        Classificação de contas
                    </h1>

                    <p className="mt-1 text-sm text-muted-foreground">
                        Vincule as contas contábeis do Questor
                        à estrutura gerencial da FINEMP.
                    </p>
                </div>

                <div>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".xlsx,.xlsm"
                        className="hidden"
                        onChange={handleFileChange}
                    />

                    <Button
                        variant="outline"
                        disabled={importPreview.isPending}
                        onClick={() =>
                            fileInputRef.current?.click()
                        }
                    >
                        <Upload className="size-4" />

                        {importPreview.isPending
                            ? "Analisando..."
                            : "Importar PlanoQ"}
                    </Button>
                </div>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium">
                            Total de contas
                        </CardTitle>
                    </CardHeader>

                    <CardContent>
                        <p className="text-2xl font-semibold">
                            {accounts.length}
                        </p>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium">
                            Pendentes
                        </CardTitle>
                    </CardHeader>

                    <CardContent>
                        <p className="text-2xl font-semibold">
                            {pendingCount}
                        </p>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium">
                            Classificadas
                        </CardTitle>
                    </CardHeader>

                    <CardContent>
                        <p className="text-2xl font-semibold">
                            {classifiedCount}
                        </p>
                    </CardContent>
                </Card>
            </div>

            {importPreview.isError && (
                <Card className="border-destructive/30 bg-destructive/5">
                    <CardContent className="p-5 text-sm text-destructive">
                        <p className="font-medium">
                            Não foi possível analisar o arquivo.
                        </p>

                        <p className="mt-1">
                            {importPreview.error.message}
                        </p>
                    </CardContent>
                </Card>
            )}

            <ClassificationImportPreviewSheet
                open={previewOpen}
                preview={importPreview.data}
                isApplying={applyImport.isPending}
                onOpenChange={handlePreviewOpenChange}
                onConfirm={handleApplyImport}
            />

            {applyImport.data && (
                <Card className="border-emerald-200 bg-emerald-50">
                    <CardContent className="p-5">
                        <p className="font-medium text-emerald-900">
                            Importação concluída
                        </p>

                        <p className="mt-2 text-sm text-emerald-800">
                            {applyImport.data.classifiedAccounts}
                            {" "}contas classificadas e{" "}
                            {applyImport.data.createdCatalogClassifications}
                            {" "}categorias adicionadas ao catálogo global.
                        </p>

                        {applyImport.data.conflicts > 0 && (
                            <p className="mt-1 text-sm text-emerald-800">
                                {applyImport.data.conflicts}
                                {" "}conflitos permaneceram inalterados para revisão manual.
                            </p>
                        )}
                    </CardContent>
                </Card>
            )}

            <Card>
                <CardContent className="space-y-5 pt-6">
                    <div className="flex flex-col gap-3 lg:flex-row">
                        <div className="relative flex-1">
                            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />

                            <Input
                                value={search}
                                onChange={(event) => {
                                    setSearch(
                                        event.target.value,
                                    )
                                    setPage(0);
                                }
                                }
                                placeholder="Buscar por código ou descrição..."
                                className="pl-9"
                            />
                        </div>

                        <Select
                            value={statusFilter}
                            onValueChange={(value) => {
                                setStatusFilter(
                                    value as StatusFilter,
                                );
                                setPage(0);
                            }
                            }
                        >
                            <SelectTrigger className="w-full lg:w-48">
                                <SelectValue />
                            </SelectTrigger>

                            <SelectContent>
                                <SelectItem value="ALL">
                                    Todas
                                </SelectItem>

                                <SelectItem value="PENDING">
                                    Pendentes
                                </SelectItem>

                                <SelectItem value="CLASSIFIED">
                                    Classificadas
                                </SelectItem>
                            </SelectContent>
                        </Select>
                    </div>

                    <div className="overflow-x-auto rounded-xl border">
                        <table className="w-full min-w-[880px] table-fixed text-sm">

                            <colgroup>
                                <col className="w-[18%]" />
                                <col className="w-[23%]" />
                                <col className="w-[12%]" />
                                <col className="w-[32%]" />
                                <col className="w-[15%]" />
                            </colgroup>

                            <thead className="bg-muted/40 text-left">
                                <tr>
                                    <th className="px-4 py-3 font-medium">
                                        Conta
                                    </th>

                                    <th className="px-4 py-3 font-medium">
                                        Descrição
                                    </th>

                                    <th className="px-4 py-3 font-medium">
                                        Status
                                    </th>

                                    <th className="px-4 py-3 font-medium">
                                        Classificação
                                    </th>

                                    <th className="px-4 py-3 text-right font-medium">
                                        Ações
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                {filteredAccounts.map(
                                    (account) => {
                                        const selected =
                                            selectedClassifications[
                                            account.accountId
                                            ]
                                            ?? account.classificationId
                                            ?? ""

                                        return (
                                            <tr
                                                key={account.accountId}
                                                className="border-t"
                                            >
                                                <td className="px-4 py-4 align-top font-mono text-xs">
                                                    {account.accountKey}
                                                </td>

                                                <td className="px-4 py-4 align-top">
                                                    {account.description}
                                                </td>

                                                <td className="px-4 py-4 align-top">
                                                    <Badge
                                                        variant={
                                                            account.status === "CLASSIFIED"
                                                                ? "secondary"
                                                                : "outline"
                                                        }
                                                    >
                                                        {account.status === "CLASSIFIED"
                                                            ? "Classificada"
                                                            : "Pendente"}
                                                    </Badge>
                                                </td>

                                                <td className="px-4 py-4">
                                                    <Select
                                                        value={selected}
                                                        onValueChange={(value) =>
                                                            setSelectedClassifications(
                                                                (current) => ({
                                                                    ...current,
                                                                    [account.accountId]:
                                                                        value,
                                                                }),
                                                            )
                                                        }
                                                    >
                                                        <SelectTrigger className="w-full">
                                                            <SelectValue placeholder="Selecionar classificação" />
                                                        </SelectTrigger>

                                                        <SelectContent>
                                                            {catalog.map(
                                                                (classification) => (
                                                                    <SelectItem
                                                                        key={classification.id}
                                                                        value={classification.id}
                                                                    >
                                                                        {
                                                                            classification.dreClassification
                                                                        }
                                                                        {" > "}
                                                                        {
                                                                            classification.dreOpenClassification
                                                                        }
                                                                        {" > "}
                                                                        {
                                                                            classification.ebitdaClassification
                                                                        }
                                                                    </SelectItem>
                                                                ),
                                                            )}
                                                        </SelectContent>
                                                    </Select>
                                                </td>

                                                <td className="px-4 py-4">
                                                    <div className="flex justify-end gap-2">
                                                        {account.status === "CLASSIFIED" && (
                                                            <Button
                                                                variant="outline"
                                                                size="sm"
                                                                onClick={() =>
                                                                    unclassifyMutation.mutate(
                                                                        account.accountId,
                                                                    )
                                                                }
                                                                disabled={
                                                                    unclassifyMutation.isPending
                                                                }
                                                            >
                                                                <Undo2 className="size-4" />
                                                                Desclassificar
                                                            </Button>
                                                        )}

                                                        <Button
                                                            size="sm"
                                                            className="whitespace-nowrap"
                                                            disabled={
                                                                !selected
                                                                || classifyMutation.isPending
                                                            }
                                                            onClick={() =>
                                                                classifyMutation.mutate({
                                                                    accountId: account.accountId,
                                                                    classificationId: selected,
                                                                })
                                                            }
                                                        >
                                                            <Tags className="size-4" />
                                                            Salvar
                                                        </Button>
                                                    </div>
                                                </td>
                                            </tr>
                                        )
                                    },
                                )}
                            </tbody>
                        </table>

                        <div className="flex items-center justify-between border-t pt-4">
                            <p className="text-sm text-muted-foreground">
                                {accountsQuery.data?.totalElements ?? 0} contas encontradas
                            </p>

                            <div className="flex items-center gap-3">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={page === 0}
                                    onClick={() =>
                                        setPage((current) => current - 1)
                                    }
                                >
                                    Anterior
                                </Button>

                                <span className="text-sm text-muted-foreground">
                                    Página {page + 1} de{" "}
                                    {accountsQuery.data?.totalPages ?? 1}
                                </span>

                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={
                                        page + 1
                                        >= (accountsQuery.data?.totalPages ?? 1)
                                    }
                                    onClick={() =>
                                        setPage((current) => current + 1)
                                    }
                                >
                                    Próxima
                                </Button>
                            </div>
                        </div>
                    </div>

                    {filteredAccounts.length === 0 && (
                        <div className="py-8 text-center text-sm text-muted-foreground">
                            Nenhuma conta encontrada.
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    )
}