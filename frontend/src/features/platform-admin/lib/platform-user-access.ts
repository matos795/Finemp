import type { PlatformPermission } from "../../auth/types/auth.types"

export const PLATFORM_PERMISSION_OPTIONS: ReadonlyArray<{
    value: PlatformPermission
    label: string
    description: string
}> = [
    {
        value: "ACCOUNTING_FIRM_CREATE",
        label: "Criar escritórios",
        description: "Permite criar novos escritórios contábeis na plataforma.",
    },
    {
        value: "ACCOUNTING_FIRM_VIEW",
        label: "Consultar escritórios",
        description: "Permite consultar a estrutura de escritórios disponível ao administrador.",
    },
    {
        value: "UNASSIGNED_COMPANY_ASSIGN",
        label: "Vincular empresas",
        description: "Permite administrar o vínculo inicial de Companies ainda não atribuídas.",
    },
    {
        value: "PLATFORM_USER_MANAGE",
        label: "Administrar usuários",
        description: "Permite administrar usuários e permissões normais da plataforma.",
    },
    {
        value: "SYSTEM_ADMIN",
        label: "Administrador do sistema",
        description: "Acesso global efetivo a toda a plataforma FINEMP. Só outro SYSTEM_ADMIN pode conceder ou remover.",
    },
]

export function isSystemAdmin(
    permissions: readonly PlatformPermission[],
): boolean {
    return permissions.includes("SYSTEM_ADMIN")
}

export function canEditPlatformPermission(
    actorPermissions: readonly PlatformPermission[],
    targetPermissions: readonly PlatformPermission[],
    permission: PlatformPermission,
): boolean {
    const actorIsSystemAdmin = isSystemAdmin(actorPermissions)
    const targetIsSystemAdmin = isSystemAdmin(targetPermissions)

    if (targetIsSystemAdmin && !actorIsSystemAdmin) {
        return false
    }

    if (permission === "SYSTEM_ADMIN" && !actorIsSystemAdmin) {
        return false
    }

    return true
}
