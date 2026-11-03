import { useQuery } from "@tanstack/react-query"
import { listStatementCandidates } from "../api/statements-api"

/** Sugestões exibidas por movimento, como o select do protótipo. */
const CANDIDATE_PAGE_SIZE = 20

export function useStatementCandidates(
    companyId: string | undefined,
    statementId: string,
    enabled: boolean,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "statement-items",
            statementId,
            "candidates",
        ],
        queryFn: () =>
            listStatementCandidates(companyId!, statementId, {
                page: 0,
                size: CANDIDATE_PAGE_SIZE,
            }),
        enabled: enabled && Boolean(companyId),
    })
}
