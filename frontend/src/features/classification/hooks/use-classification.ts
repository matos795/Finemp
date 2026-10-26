import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"

import {
    applyClassificationImport,
    classifyAccount,
    getAccountClassifications,
    getDreClassifications,
    previewClassificationImport,
    unclassifyAccount,
} from "../api/classification-api"

export function useAccountClassifications(
    companyId: string | undefined,
) {
    return useQuery({
        queryKey: [
            "account-classifications",
            companyId,
        ],

        queryFn: () =>
            getAccountClassifications(companyId!),

        enabled: Boolean(companyId),
    })
}

export function useDreClassifications(
    companyId: string | undefined,
) {
    return useQuery({
        queryKey: [
            "dre-classifications",
            companyId,
        ],

        queryFn: () =>
            getDreClassifications(companyId!),

        enabled: Boolean(companyId),
    })
}

export function useClassifyAccount(
    companyId: string,
) {
    const queryClient = useQueryClient()

    return useMutation({
        mutationFn: ({
            accountId,
            classificationId,
        }: {
            accountId: string
            classificationId: string
        }) =>
            classifyAccount(
                companyId,
                accountId,
                classificationId,
            ),

        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: [
                        "account-classifications",
                        companyId,
                    ],
                }),

                queryClient.invalidateQueries({
                    queryKey: [
                        "dre",
                        companyId,
                    ],
                }),
            ])
        },
    })
}

export function useUnclassifyAccount(
    companyId: string,
) {
    const queryClient = useQueryClient()

    return useMutation({
        mutationFn: (
            accountId: string,
        ) =>
            unclassifyAccount(
                companyId,
                accountId,
            ),

        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: [
                        "account-classifications",
                        companyId,
                    ],
                }),

                queryClient.invalidateQueries({
                    queryKey: [
                        "dre",
                        companyId,
                    ],
                }),
            ])
        },
    })
}

export function useClassificationImportPreview(
    companyId: string,
) {
    return useMutation({
        mutationFn: (file: File) =>
            previewClassificationImport(
                companyId,
                file,
            ),
    })
}

export function useApplyClassificationImport(
    companyId: string,
) {
    const queryClient =
        useQueryClient()

    return useMutation({
        mutationFn: (file: File) =>
            applyClassificationImport(
                companyId,
                file,
            ),

        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: [
                        "account-classifications",
                        companyId,
                    ],
                }),

                queryClient.invalidateQueries({
                    queryKey: [
                        "dre-classifications",
                        companyId,
                    ],
                }),

                queryClient.invalidateQueries({
                    queryKey: [
                        "dre",
                        companyId,
                    ],
                }),
            ])
        },
    })
}