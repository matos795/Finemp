import { useQuery } from "@tanstack/react-query"
import { getCurrentUser } from "../api/auth-api.ts"

export const currentUserQueryKey = ["current-user"] as const

export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    staleTime: 30_000,
    retry: false,
  })
}