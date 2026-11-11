import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import type { PlatformPermission } from "../../auth/types/auth.types"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { getUserPlatformPermissions, replaceUserPlatformPermissions } from "../api/platform-admin-api"

const labels: Record<PlatformPermission, string> = {
    ACCOUNTING_FIRM_CREATE: "Criar escritórios",
    ACCOUNTING_FIRM_VIEW: "Consultar escritórios",
    UNASSIGNED_COMPANY_ASSIGN: "Vincular empresas",
    PLATFORM_USER_MANAGE: "Administrar usuários",
    SYSTEM_ADMIN: "Administrador do sistema",
}

export function PlatformPermissionsEditor({ id, globalActor }: { id: string; globalActor: boolean }) {
    const cache = useQueryClient()
    const [assigned, setAssigned] = useState<PlatformPermission[] | null>(null)
    const [draft, setDraft] = useState<PlatformPermission[]>([])
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState("")
    const protectedTarget = assigned?.includes("SYSTEM_ADMIN") && !globalActor

    async function load() {
        setBusy(true)
        setMessage("")
        try {
            const detail = await getUserPlatformPermissions(id)
            setAssigned(detail.platformPermissions)
            setDraft(detail.platformPermissions)
        } catch {
            setMessage("Não foi possível consultar as permissões.")
        } finally {
            setBusy(false)
        }
    }

    async function save() {
        setBusy(true)
        try {
            await replaceUserPlatformPermissions(id, draft)
            setAssigned(null)
            setMessage("Permissões salvas.")
            await cache.invalidateQueries({ queryKey: currentUserQueryKey })
        } catch (error) {
            setMessage(error instanceof ApiError && error.status === 409
                ? "A operação deve preservar um administrador ativo."
                : "Não foi possível alterar as permissões.")
        } finally {
            setBusy(false)
        }
    }

    return <div>
        {assigned === null ? <button className="btn sec" type="button" disabled={busy} onClick={load}>Permissões de plataforma</button> :
            <fieldset disabled={busy}>
                <legend>Permissões de plataforma</legend>
                {(Object.keys(labels) as PlatformPermission[]).map(permission => <label key={permission}>
                    <input type="checkbox" checked={draft.includes(permission)}
                        disabled={protectedTarget || (permission === "SYSTEM_ADMIN" && !globalActor)}
                        onChange={event => setDraft(event.target.checked
                            ? [...draft, permission] : draft.filter(value => value !== permission))} />
                    {labels[permission]}
                </label>)}
                {protectedTarget && <p>Apenas um administrador do sistema pode alterar estas permissões.</p>}
                <button type="button" className="btn" disabled={protectedTarget} onClick={save}>Salvar</button>
                <button type="button" className="btn sec" onClick={() => setAssigned(null)}>Fechar</button>
            </fieldset>}
        {message && <p role="status">{message}</p>}
    </div>
}
