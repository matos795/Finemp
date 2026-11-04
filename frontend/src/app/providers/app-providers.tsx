import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { BrowserRouter } from "react-router"
import type { PropsWithChildren } from "react"
import { ToastProvider } from "../../components/finemp/toast-provider"

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: false,
            refetchOnWindowFocus: false,
        },
    },
})

export function AppProviders({
    children,
}: PropsWithChildren) {
    return (
        <QueryClientProvider client={queryClient}>
            <ToastProvider>
                <BrowserRouter>
                    {children}
                </BrowserRouter>
            </ToastProvider>
        </QueryClientProvider>
    )
}
