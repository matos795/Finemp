import { LogOut } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { useCompanyAccess } from "../../features/companies/company-access-context"
import { logout } from "../../features/auth/api/auth-api"
import { currentUserQueryKey } from "../../features/auth/hooks/use-current-user"
import { CompanySwitcher } from "./company-switcher"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "../ui/dropdown-menu"
import { Button } from "../ui/button"
import { Avatar, AvatarFallback } from "../ui/avatar"

export function AppHeader() {
    const { user } = useCompanyAccess()

    const queryClient = useQueryClient()
    const navigate = useNavigate()

    const initials = user.name
        .split(" ")
        .slice(0, 2)
        .map((part) => part[0])
        .join("")
        .toUpperCase()

    async function handleLogout() {
        await logout()

        queryClient.setQueryData(
            currentUserQueryKey,
            null,
        )

        navigate("/login", {
            replace: true,
        })
    }

    return (
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/95 backdrop-blur">
            <div className="flex h-[72px] items-center justify-between gap-6 px-6 lg:px-8">

                <div className="flex min-w-0 items-center gap-4">
                    <div className="hidden sm:block">
                        <p className="mb-1 text-[10px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
                            Empresa ativa
                        </p>

                        <CompanySwitcher />
                    </div>
                </div>

                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button
                            variant="ghost"
                            className="h-12 rounded-xl px-2 hover:bg-slate-100"
                        >
                            <div className="hidden min-w-0 text-right md:block">
                                <p className="truncate text-sm font-medium text-[#102238]">
                                    {user.name}
                                </p>

                                <p className="truncate text-xs text-muted-foreground">
                                    {user.email}
                                </p>
                            </div>

                            <Avatar className="ml-2 size-9">
                                <AvatarFallback className="bg-[#102238] text-xs font-medium text-white">
                                    {initials}
                                </AvatarFallback>
                            </Avatar>
                        </Button>
                    </DropdownMenuTrigger>

                    <DropdownMenuContent
                        align="end"
                        className="w-64"
                    >
                        <DropdownMenuLabel>
                            <div>
                                <p className="font-medium">
                                    {user.name}
                                </p>

                                <p className="text-xs font-normal text-muted-foreground">
                                    {user.email}
                                </p>
                            </div>
                        </DropdownMenuLabel>

                        <DropdownMenuSeparator />

                        <DropdownMenuItem
                            onClick={handleLogout}
                            className="text-destructive"
                        >
                            <LogOut />
                            Sair
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </header>
    )
}