import { Outlet } from "react-router"
import { AppSidebar } from "./app-sidebar"
import { AppHeader } from "./app-header"

export function AppLayout() {
    return (
        <div className="min-h-screen bg-[#f6f7f9]">
            <AppSidebar />

            <div className="min-h-screen lg:pl-[260px]">
                <AppHeader />

                <main className="p-6 lg:p-8">
                    <Outlet />
                </main>
            </div>
        </div>
    )
}