import { useEffect, useRef } from "react"
import { useAccountingContext } from "../accounting-context"
import { SourcesSyncPanel } from "./sources-sync-panel"

interface AccountingContextModalProps {
    open: boolean
    onClose: () => void
}

/**
 * Modal de contexto no padrão #mFiltros do protótipo ("Empresa e período").
 * Na Área da Contabilidade a escolha é Contabilidade → Cliente → Empresa, só
 * entre as opções da carteira da sessão. A competência fica na barra de cada tela.
 */
export function AccountingContextModal({ open, onClose }: AccountingContextModalProps) {
    const { portfolio, firm, client, company, selectFirm, selectClient, selectCompany } = useAccountingContext()
    const firstField = useRef<HTMLSelectElement>(null)

    useEffect(() => {
        if (!open) {
            return
        }

        const timer = window.setTimeout(() => firstField.current?.focus(), 30)
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                onClose()
            }
        }

        window.addEventListener("keydown", onKey)

        return () => {
            window.clearTimeout(timer)
            window.removeEventListener("keydown", onKey)
        }
    }, [open, onClose])

    return (
        <div
            className="modal"
            id="mFiltros"
            hidden={!open}
            role="dialog"
            aria-modal="true"
            aria-labelledby="mFiltTit"
            onClick={(event) => {
                if (event.target === event.currentTarget) {
                    onClose()
                }
            }}
        >
            <div className="dlg">
                <h2 id="mFiltTit">Contabilidade, cliente e empresa</h2>
                <p className="sub" style={{ marginTop: 0 }}>
                    Escolha a contabilidade, o cliente e a empresa. Vale para todas as abas. A lista mostra só a
                    sua carteira; escolher uma empresa não libera acesso aos dados dela.
                </p>

                <div className="filtgrid ctb-ctxgrid" style={{ marginBottom: 18 }}>
                    <div className="f">
                        <label htmlFor="fFirma">Contabilidade</label>
                        <select
                            id="fFirma"
                            ref={firstField}
                            value={firm?.accountingFirmId ?? ""}
                            disabled={portfolio.accountingFirms.length < 2}
                            onChange={(event) => selectFirm(event.target.value)}
                        >
                            {portfolio.accountingFirms.map((item) => (
                                <option key={item.accountingFirmId} value={item.accountingFirmId}>
                                    {item.active ? item.name : `${item.name} (inativa)`}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="f">
                        <label htmlFor="fCliente">Cliente</label>
                        <select
                            id="fCliente"
                            value={client?.clientId ?? ""}
                            disabled={!firm || firm.clients.length === 0}
                            onChange={(event) => selectClient(event.target.value)}
                        >
                            {!client && <option value="">Nenhum cliente</option>}
                            {(firm?.clients ?? []).map((item) => (
                                <option key={item.clientId} value={item.clientId}>
                                    {item.active ? item.name : `${item.name} (inativo)`}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="f fe">
                        <label htmlFor="fEmp">Empresa</label>
                        <select
                            id="fEmp"
                            value={company?.companyId ?? ""}
                            disabled={!client || client.companies.length === 0}
                            onChange={(event) => selectCompany(event.target.value)}
                        >
                            {!company && <option value="">Nenhuma empresa</option>}
                            {(client?.companies ?? []).map((item) => (
                                <option key={item.companyId} value={item.companyId}>
                                    {item.legalName}
                                </option>
                            ))}
                        </select>
                    </div>
                </div>

                <SourcesSyncPanel />

                <div className="actions">
                    <button className="btn primary" id="btnFiltrosOk" type="button" onClick={onClose}>
                        Fechar
                    </button>
                </div>
            </div>
        </div>
    )
}
