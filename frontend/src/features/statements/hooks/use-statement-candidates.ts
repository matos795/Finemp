import { useInfiniteQuery } from "@tanstack/react-query"
import { listStatementCandidates } from "../api/statements-api"

/** Sugestões exibidas por movimento, preservando a paginação do servidor. */
const CANDIDATE_PAGE_SIZE = 20

export function useStatementCandidates(
    companyId: string | undefined,
    statementId: string,
    enabled: boolean,
) {
    return useInfiniteQuery({
        queryKey: [
            "companies",
            companyId,
            "statement-items",
            statementId,
            "candidates",
        ],
        initialPageParam: 0,
        queryFn: ({ pageParam }) =>
            listStatementCandidates(companyId!, statementId, {
                page: pageParam,
                size: CANDIDATE_PAGE_SIZE,
            }),
        getNextPageParam: (lastPage) => {
            const nextPage = lastPage.page + 1

            return nextPage < lastPage.totalPages
                ? nextPage
                : undefined
        },
        enabled: enabled && Boolean(companyId),
    })
}