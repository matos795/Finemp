import type { DreLine } from "../types/dre.types"

export interface DreOpenGroup {
    name: string
    total: number
    lines: DreLine[]
}

export interface DreGroup {
    name: string
    total: number
    groups: DreOpenGroup[]
}

export function groupDreLines(
    lines: DreLine[],
): DreGroup[] {
    const dreGroups =
        new Map<
            string,
            Map<string, DreLine[]>
        >()

    for (const line of lines) {
        const openGroups =
            dreGroups.get(
                line.dreClassification,
            ) ?? new Map<string, DreLine[]>()

        const groupLines =
            openGroups.get(
                line.dreOpenClassification,
            ) ?? []

        groupLines.push(line)

        openGroups.set(
            line.dreOpenClassification,
            groupLines,
        )

        dreGroups.set(
            line.dreClassification,
            openGroups,
        )
    }

    return Array.from(
        dreGroups.entries(),
    ).map(
        ([dreName, openGroups]) => {
            const groups =
                Array.from(
                    openGroups.entries(),
                ).map(
                    ([name, groupLines]) => ({
                        name,
                        lines: groupLines,
                        total: groupLines.reduce(
                            (sum, line) =>
                                sum + line.total,
                            0,
                        ),
                    }),
                )

            return {
                name: dreName,
                groups,
                total: groups.reduce(
                    (sum, group) =>
                        sum + group.total,
                    0,
                ),
            }
        },
    )
}