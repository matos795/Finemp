import { Outlet } from "react-router"
import { AppSidebar } from "./app-sidebar"
import { AppHeader } from "./app-header"

export function AppLayout() {
    return (
        <div className="flex min-h-screen bg-[#f6f7f9]">
            <AppSidebar />

            <div className="min-w-0 flex-1">
                <AppHeader />

                <main className="p-6 lg:p-8">
                    <Outlet />
                </main>
            </div>
        </div>
    )
} 