import { INFERRED_ACCOUNT_KIND_NOTE, inferredAccountKind } from "../lib/inferred-account-kind"

/** Sintética/analítica só como inferência documentada, sempre com o selo "Inferido". */
export function InferredKind({ accountType }: { accountType: string | null | undefined }) {
    const kind = inferredAccountKind(accountType)

    if (!kind) {
        return <span className="muted">—</span>
    }

    return (
        <span title={INFERRED_ACCOUNT_KIND_NOTE}>
            {kind.label} <span className="tag est">{kind.tag}</span>
        </span>
    )
}
