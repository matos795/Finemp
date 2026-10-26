import { Badge } from "../../../components/ui/badge"
import { Button } from "../../../components/ui/button"
import {
    Sheet,
    SheetContent,
    SheetDescription,
    SheetFooter,
    SheetHeader,
    SheetTitle,
} from "../../../components/ui/sheet"

import type {
    ClassificationImportPreview,
    ClassificationImportStatus,
} from "../types/classification.types"

interface ClassificationImportPreviewSheetProps {
    open: boolean
    preview: ClassificationImportPreview | undefined
    isApplying: boolean
    onOpenChange: (open: boolean) => void
    onConfirm: () => void
}

export function ClassificationImportPreviewSheet({
    open,
    preview,
    isApplying,
    onOpenChange,
    onConfirm,
}: ClassificationImportPreviewSheetProps) {

    if (!preview) {
        return null
    }

    return (
        <Sheet
            open={open}
            onOpenChange={onOpenChange}
        >
            <SheetContent
                side="right"
                className="gap-0 overflow-hidden p-0"
                style={{
                    width: "min(92vw, 1120px)",
                    maxWidth: "none",
                }}
            >
                <SheetHeader className="border-b px-6 py-5">
                    <SheetTitle className="text-lg">
                        Prévia da importação
                    </SheetTitle>

                    <SheetDescription>
                        Revise o resultado do PlanoQ antes de alterar
                        as classificações da empresa.
                    </SheetDescription>
                </SheetHeader>

                <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">

                        <PreviewMetric
                            label="Prontas"
                            value={preview.ready}
                        />

                        <PreviewMetric
                            label="Já classificadas"
                            value={preview.alreadyClassified}
                        />

                        <PreviewMetric
                            label="Sem mapeamento"
                            value={preview.mappingNotFound}
                        />

                        <PreviewMetric
                            label="Categoria ausente"
                            value={preview.classificationNotFound}
                        />

                        <PreviewMetric
                            label="Conflitos"
                            value={preview.conflicts}
                        />

                        <PreviewMetric
                            label="Novas categorias"
                            value={preview.catalogAdditions}
                        />
                    </div>

                    {preview.catalogAdditions > 0 && (
                        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
                            <p className="text-sm font-medium text-amber-900">
                                Alteração no catálogo global
                            </p>

                            <p className="mt-1 text-sm text-amber-800">
                                {preview.catalogAdditions} novas categorias
                                gerenciais serão adicionadas ao catálogo global
                                FINEMP ao confirmar.
                            </p>
                        </div>
                    )}

                    <div className="mt-5 overflow-hidden rounded-xl border">
                        <div className="max-h-[calc(100vh-330px)] overflow-auto">
                            <table className="w-full table-fixed text-sm">
                                <colgroup>
                                    <col className="w-[20%]" />
                                    <col className="w-[27%]" />
                                    <col className="w-[16%]" />
                                    <col className="w-[37%]" />
                                </colgroup>
                                <thead className="sticky top-0 bg-muted/90 backdrop-blur">
                                    <tr>
                                        <th className="px-4 py-3 text-left font-medium">
                                            Conta
                                        </th>

                                        <th className="px-4 py-3 text-left font-medium">
                                            Descrição
                                        </th>

                                        <th className="px-4 py-3 text-left font-medium">
                                            Resultado
                                        </th>

                                        <th className="px-4 py-3 text-left font-medium">
                                            Classificação proposta
                                        </th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {preview.items.map((item) => (
                                        <tr
                                            key={item.accountId}
                                            className="border-t"
                                        >
                                            <td className="px-4 py-3 font-mono text-xs">
                                                {item.accountKey}
                                            </td>

                                            <td className="break-words px-4 py-3">
                                                {item.accountDescription}
                                            </td>

                                            <td className="px-4 py-3">
                                                <ImportStatusBadge
                                                    status={item.status}
                                                />
                                            </td>

                                            <td className="px-4 py-3">
                                                {item.dreClassification
                                                    ? (
                                                        <>
                                                            {item.dreClassification}
                                                            {" > "}
                                                            {item.dreOpenClassification}
                                                            {" > "}
                                                            {item.ebitdaClassification}
                                                        </>
                                                    )
                                                    : "—"}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <SheetFooter className="flex-row items-center justify-between border-t bg-white px-6 py-4">
                    <p className="self-center text-xs text-muted-foreground">
                        Nenhuma alteração ocorre até a confirmação.
                    </p>

                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            disabled={isApplying}
                            onClick={() =>
                                onOpenChange(false)
                            }
                        >
                            Cancelar
                        </Button>

                        <Button
                            disabled={isApplying}
                            onClick={onConfirm}
                        >
                            {isApplying
                                ? "Importando..."
                                : "Confirmar importação"}
                        </Button>
                    </div>
                </SheetFooter>
            </SheetContent>
        </Sheet>
    )
}

function PreviewMetric({
    label,
    value,
}: {
    label: string
    value: number
}) {
    return (
        <div className="rounded-xl border bg-white p-4">
            <p className="text-xs text-muted-foreground">
                {label}
            </p>

            <p className="mt-2 text-2xl font-semibold">
                {value}
            </p>
        </div>
    )
}

function ImportStatusBadge({
    status,
}: {
    status: ClassificationImportStatus
}) {
    const labels: Record<
        ClassificationImportStatus,
        string
    > = {
        READY: "Pronta",
        ALREADY_CLASSIFIED: "Já classificada",
        MAPPING_NOT_FOUND: "Sem mapeamento",
        CLASSIFICATION_NOT_FOUND: "Categoria ausente",
        CONFLICT: "Conflito",
    }

    return (
        <Badge variant="outline">
            {labels[status]}
        </Badge>
    )
}