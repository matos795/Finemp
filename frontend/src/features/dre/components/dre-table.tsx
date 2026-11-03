import type { DreGroup } from "../lib/group-dre-lines"
import { formatAmountCell } from "../../../lib/formatters/decimal"

interface DreTableProps {
    groups: DreGroup[]
    /** Chaves abertas: "g:<grupo>" e "o:<grupo>/<abertura>". */
    expanded: ReadonlySet<string>
    onToggle: (key: string) => void
}

function amountClass(value: number) {
    return value < -0.004 ? "n neg" : "n"
}

/**
 * DRE contábil no formato de matriz do protótipo (.mtx): linhas de grupo
 * com seta (.tog) para abrir a DRE aberta e, nela, as linhas EBITDA.
 */
export function DreTable({
    groups,
    expanded,
    onToggle,
}: DreTableProps) {
    return (
        <div className="mtx">
            <table>
                <thead>
                    <tr>
                        <th>DRE</th>
                        <th className="n">Valor</th>
                    </tr>
                </thead>

                <tbody>
                    {groups.map((dreGroup) => {
                        const groupKey = `g:${dreGroup.name}`
                        const groupOpen = expanded.has(groupKey)

                        return [
                            <tr
                                key={groupKey}
                                className="grp"
                            >
                                <td>
                                    <span
                                        className="tog"
                                        role="button"
                                        tabIndex={0}
                                        aria-expanded={groupOpen}
                                        onClick={() => onToggle(groupKey)}
                                        onKeyDown={(event) => {
                                            if (event.key === "Enter" || event.key === " ") {
                                                event.preventDefault()
                                                onToggle(groupKey)
                                            }
                                        }}
                                    >
                                        {groupOpen ? "▾" : "▸"}
                                    </span>
                                    {dreGroup.name}
                                </td>
                                <td className={amountClass(dreGroup.total)}>
                                    {formatAmountCell(dreGroup.total)}
                                </td>
                            </tr>,

                            ...(groupOpen
                                ? dreGroup.groups.flatMap((openGroup) => {
                                    const openKey = `o:${dreGroup.name}/${openGroup.name}`
                                    const open = expanded.has(openKey)

                                    return [
                                        <tr
                                            key={openKey}
                                            className="sub"
                                        >
                                            <td style={{ paddingLeft: 28 }}>
                                                <span
                                                    className="tog"
                                                    role="button"
                                                    tabIndex={0}
                                                    aria-expanded={open}
                                                    onClick={() => onToggle(openKey)}
                                                    onKeyDown={(event) => {
                                                        if (event.key === "Enter" || event.key === " ") {
                                                            event.preventDefault()
                                                            onToggle(openKey)
                                                        }
                                                    }}
                                                >
                                                    {open ? "▾" : "▸"}
                                                </span>
                                                {openGroup.name}
                                            </td>
                                            <td className={amountClass(openGroup.total)}>
                                                {formatAmountCell(openGroup.total)}
                                            </td>
                                        </tr>,

                                        ...(open
                                            ? openGroup.lines.map((line, index) => (
                                                <tr
                                                    key={`${openKey}/${line.ebitdaClassification}/${index}`}
                                                    className="sub"
                                                >
                                                    <td style={{ paddingLeft: 52, fontSize: 13 }}>
                                                        {line.ebitdaClassification}
                                                    </td>
                                                    <td className={amountClass(line.total)}>
                                                        {formatAmountCell(line.total)}
                                                    </td>
                                                </tr>
                                            ))
                                            : []),
                                    ]
                                })
                                : []),
                        ]
                    })}
                </tbody>
            </table>
        </div>
    )
}
