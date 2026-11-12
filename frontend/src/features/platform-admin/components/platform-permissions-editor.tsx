import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import type { PlatformPermission } from "../../auth/types/auth.types"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import {
    getUserPlatformPermissions,
    replaceUserPlatformPermissions,
} from "../api/platform-admin-api"
import {
    canEditPlatformPermission,
    isSystemAdmin,
    PLATFORM_PERMISSION_OPTIONS,
} from "../lib/platform-user-access"

interface Props {
    id: string
    actorPermissions: readonly PlatformPermission[]
}

export function PlatformPermissionsEditor({
    id,
    actorPermissions,
}: Props) {
    const cache = useQueryClient()
    const [assigned, setAssigned] = useState<PlatformPermission[] | null>(null)
    const [draft, setDraft] = useState<PlatformPermission[]>([])
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState("")
    const targetIsSystemAdmin = assigned ? isSystemAdmin(assigned) : false
    const actorIsSystemAdmin = isSystemAdmin(actorPermissions)
    const protectedTarget = targetIsSystemAdmin && !actorIsSystemAdmin

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
        setMessage("")

        try {
            await replaceUserPlatformPermissions(id, draft)
            const detail = await getUserPlatformPermissions(id)
            setAssigned(detail.platformPermissions)
            setDraft(detail.platformPermissions)
            setMessage("Permissões salvas.")
            await cache.invalidateQueries({ queryKey: currentUserQueryKey })
        } catch (error) {
            if (error instanceof ApiError && error.status === 403) {
                setMessage("Esta alteração exige autorização de outro administrador do sistema.")
            } else if (error instanceof ApiError && error.status === 409) {
                setMessage("A operação deve preservar um administrador ativo.")
            } else {
                setMessage("Não foi possível alterar as permissões.")
            }
        } finally {
            setBusy(false)
        }
    }

    function toggle(permission: PlatformPermission, checked: boolean) {
        setDraft((current) =>
            checked
                ? [...current, permission]
                : current.filter((value) => value !== permission),
        )
    }

    if (assigned === null) {
        return (
            <div>
                <button
                    className="btn sec"
                    type="button"
                    disabled={busy}
                    onClick={() => void load()}
                >
                    {busy ? "Consultando..." : "Permissões de plataforma"}
                </button>
                {message && <p role="status">{message}</p>}
            </div>
        )
    }

    return (
        <div>
            <fieldset disabled={busy}>
                <legend>Permissões de plataforma</legend>

                {targetIsSystemAdmin && (
                    <p>
                        <strong>Administrador do sistema.</strong>{" "}
                        Esta capability concede acesso global efetivo ao FINEMP.
                    </p>
                )}

                {PLATFORM_PERMISSION_OPTIONS.map((option) => {
                    const editable = canEditPlatformPermission(
                        actorPermissions,
                        assigned,
                        option.value,
                    )

                    return (
                        <label
                            key={option.value}
                            style={{
                                display: "grid",
                                gridTemplateColumns: "auto 1fr",
                                gap: 8,
                                marginBottom: 10,
                            }}
                        >
                            <input
                                type="checkbox"
                                checked={draft.includes(option.value)}
                                disabled={!editable}
                                onChange={(event) =>
                                    toggle(option.value, event.target.checked)
                                }
                            />
                            <span>
                                <strong>{option.label}</strong>
                                <br />
                                <small>{option.description}</small>
                            </span>
                        </label>
                    )
                })}

                {protectedTarget && (
                    <p>
                        Apenas um administrador do sistema pode alterar as
                        permissões deste usuário.
                    </p>
                )}

                {!actorIsSystemAdmin && (
                    <p>
                        A permissão Administrador do sistema exige outro
                        SYSTEM_ADMIN e não pode ser concedida por
                        PLATFORM_USER_MANAGE sozinho.
                    </p>
                )}

                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <button
                        type="button"
                        className="btn"
                        disabled={protectedTarget}
                        onClick={() => void save()}
                    >
                        {busy ? "Salvando..." : "Salvar"}
                    </button>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => setAssigned(null)}
                    >
                        Fechar
                    </button>
                </div>
            </fieldset>

            {message && <p role="status">{message}</p>}
        </div>
    )
}
