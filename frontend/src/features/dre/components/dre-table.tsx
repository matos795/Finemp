import {
    ChevronRight,
} from "lucide-react"
import { Card, CardContent } from "../../../components/ui/card"
import type { DreLine } from "../types/dre.types"
import { groupDreLines } from "../lib/group-dre-lines"
import { formatCurrency } from "../../../lib/formatters/currency"

interface DreTableProps {
    lines: DreLine[]
}

export function DreTable({
    lines,
}: DreTableProps) {
    const groups =
        groupDreLines(lines)

    return (
        <Card className="overflow-hidden border-slate-200 shadow-sm">
            <CardContent className="p-0">
                <div className="grid grid-cols-[1fr_auto] border-b bg-slate-50 px-6 py-3 text-xs font-medium uppercase tracking-wider text-slate-500">
                    <span>Estrutura DRE</span>
                    <span>Valor</span>
                </div>

                <div>
                    {groups.map((dreGroup) => (
                        <div
                            key={dreGroup.name}
                            className="border-b last:border-b-0"
                        >
                            <div className="grid grid-cols-[1fr_auto] items-center bg-white px-6 py-4">
                                <div className="flex items-center gap-2">
                                    <div className="h-5 w-1 rounded-full bg-[#c7a35a]" />

                                    <span className="font-semibold text-[#102238]">
                                        {dreGroup.name}
                                    </span>
                                </div>

                                <span className="font-mono font-semibold text-[#102238]">
                                    {formatCurrency(
                                        dreGroup.total,
                                    )}
                                </span>
                            </div>

                            {dreGroup.groups.map(
                                (openGroup) => (
                                    <div
                                        key={openGroup.name}
                                        className="border-t bg-slate-50/40"
                                    >
                                        <div className="grid grid-cols-[1fr_auto] px-8 py-3">
                                            <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
                                                <ChevronRight className="size-3.5 text-slate-400" />

                                                {openGroup.name}
                                            </div>

                                            <span className="font-mono text-sm font-medium text-slate-700">
                                                {formatCurrency(
                                                    openGroup.total,
                                                )}
                                            </span>
                                        </div>

                                        {openGroup.lines.map(
                                            (line, index) => (
                                                <div
                                                    key={`${line.ebitdaClassification}-${index}`}
                                                    className="grid grid-cols-[1fr_auto] border-t border-slate-100 bg-white px-12 py-3 text-sm"
                                                >
                                                    <span className="text-slate-500">
                                                        {
                                                            line.ebitdaClassification
                                                        }
                                                    </span>

                                                    <span className="font-mono text-slate-600">
                                                        {formatCurrency(
                                                            line.total,
                                                        )}
                                                    </span>
                                                </div>
                                            ),
                                        )}
                                    </div>
                                ),
                            )}
                        </div>
                    ))}
                </div>
            </CardContent>
        </Card>
    )
}