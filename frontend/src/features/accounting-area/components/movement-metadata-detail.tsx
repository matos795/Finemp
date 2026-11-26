import { formatInstant, movementMetadataView, type MovementMetadataCarrier } from "../lib/accounting-v2"

interface MovementMetadataDetailProps {
    item: MovementMetadataCarrier
    /** Catálogo técnico de origem (código → descrição técnica), quando publicado. */
    originDescriptions?: Map<string, string>
}

/**
 * Metadados v2 de um lançamento (#509). SYNCHRONIZED: todos os campos crus da
 * origem, com "vazio na origem" para null. Caso contrário, diz que ainda não
 * foram importados. Nenhum código é interpretado.
 */
export function MovementMetadataDetail({ item, originDescriptions }: MovementMetadataDetailProps) {
    const view = movementMetadataView(item)

    if (!view.synchronized) {
        return <p className="sub ctb-meta-none">{view.message}</p>
    }

    return (
        <dl className="ctb-meta">
            {view.fields.map((field) => {
                const technical =
                    field.key === "originCode" && field.value ? originDescriptions?.get(field.value) : undefined

                return (
                    <div key={field.key}>
                        <dt>{field.label}</dt>
                        <dd className="mono">
                            {field.empty ? (
                                <span className="muted">vazio na origem</span>
                            ) : field.key === "sourceRecordedAt" ? (
                                formatInstant(field.value as string)
                            ) : (
                                field.value
                            )}
                            {technical && <span className="muted"> · {technical}</span>}
                        </dd>
                    </div>
                )
            })}
        </dl>
    )
}
