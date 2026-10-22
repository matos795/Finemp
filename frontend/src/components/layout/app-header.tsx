import { LogOut, UserRound } from "lucide-react"
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
        <header className="flex h-20 items-center justify-between border-b bg-white px-6 lg:px-8">
            <CompanySwitcher />

            <DropdownMenu>
                <DropdownMenuTrigger asChild>
                    <Button
                        variant="ghost"
                        className="h-11 gap-3 px-2"
                    >
                        <div className="hidden text-right md:block">
                            <p className="text-sm font-medium">
                                {user.name}
                            </p>

                            <p className="text-xs text-muted-foreground">
                                {user.email}
                            </p>
                        </div>

                        <Avatar className="size-9">
                            <AvatarFallback className="bg-[#102238] text-xs text-white">
                                {initials}
                            </AvatarFallback>
                        </Avatar>
                    </Button>
                </DropdownMenuTrigger>

                <DropdownMenuContent
                    align="end"
                    className="w-56"
                >
                    <DropdownMenuLabel>
                        Minha conta
                    </DropdownMenuLabel>

                    <DropdownMenuSeparator />

                    <DropdownMenuItem disabled>
                        <UserRound />
                        {user.email}
                    </DropdownMenuItem>

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
        </header>
    )
}