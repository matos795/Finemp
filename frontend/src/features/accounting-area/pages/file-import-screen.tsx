import { useEffect, useReducer, useRef, useState, type ReactNode } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getReadableAccountingSources } from "../api/accounting-data-api"
import {
    confirmGeneralLedger,
    getFileImportHistory,
    prepareFileImportSource,
    previewGeneralLedger,
    type PreparedFileImportSource,
} from "../api/file-import-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { findAccountingScreenById, type AccountingScreen } from "../lib/accounting-screens"
import { formatDay, formatInstant } from "../lib/accounting-v2"
import { formatExactDecimal } from "../lib/exact-decimal"
import {
    confirmBlockedReason,
    confirmRequest,
    fileImportErrorMessage,
    fileImportReducer,
    fileImportSources,
    historyRows,
    importSteps,
    initialFileImportState,
    previewIsConfirmable,
    selectFileImportSource,
    type FileImportAction,
    type GeneralLedgerPreviewIssue,
} from "../lib/file-import"
import { accountingQueryKey } from "../lib/query-keys"
import { ScreenHeader } from "./accounting-screen-page"

interface FileImportScreenProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/** Endpoints do #519 ainda não publicados respondem 404/405: a tela diz isso, sem fingir. */
function unavailable(error: unknown) {
    const status = apiErrorStatus(error)
    return status === 404 || status === 405
}

function Issues({ title, issues, className }: { title: string; issues: GeneralLedgerPreviewIssue[]; className: string }) {
    if (issues.length === 0) {
        return null
    }

    return (
        <div className={className} role={className === "warnbar" ? "status" : "alert"}>
            <b>{title}</b>
            <ul className="ctb-issues">
                {issues.map((issue, index) => (
                    <li key={`${issue.code}-${issue.sourceRow ?? "x"}-${index}`}>
                        {issue.sourceRow !== null && <span className="mono">Linha {issue.sourceRow}: </span>}
                        {issue.message}
                    </li>
                ))}
            </ul>
        </div>
    )
}

/**
 * Importar Razão (F4 / #483): fonte FILE_IMPORT, arquivo .xls do Razão Analítico
 * (layout homologado), pré-visualização e confirmação com o MESMO arquivo. Os
 * dados ficam sempre como "importados"; contas do Razão são contas
 * observadas, não o plano de contas completo. Exige ACCOUNTING_IMPORT_MANAGE.
 */
export function FileImportScreen(props: FileImportScreenProps) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const queryClient = useQueryClient()
    const navigate = useNavigate()
    const companyId = company?.companyId ?? null
    const canImport = companyGrant?.permissions.includes("ACCOUNTING_IMPORT_MANAGE") ?? false

    const [chosenSource, setChosenSource] = useState<string | null>(null)
    const [prepared, setPrepared] = useState<PreparedFileImportSource | null>(null)
    const [state, dispatch] = useReducer(fileImportReducer, initialFileImportState)
    const confirmingRef = useRef(false)
    const inputRef = useRef<HTMLInputElement>(null)

    const readable = useQuery({
        queryKey: accountingQueryKey(userId, "file-import", companyId, "sources"),
        queryFn: () => getReadableAccountingSources(companyId as string),
        enabled: companyId !== null && canImport,
        retry: false,
    })

    // A lista legível exige permissão de leitura contábil; sem ela (403), a fonte vem do preparo.
    const listed = readable.isSuccess ? readable.data : []
    const candidates = fileImportSources([
        ...listed,
        ...(prepared && !listed.some((source) => source.id === prepared.id) ? [prepared] : []),
    ])
    const sourceId = selectFileImportSource(candidates, chosenSource)

    // Qualquer troca da fonte efetiva (escolha ou refetch da lista) invalida o preview.
    useEffect(() => {
        dispatch({ type: "source-changed", sourceId })
        if (inputRef.current && sourceId === null) inputRef.current.value = ""
    }, [sourceId])

    const prepare = useMutation({
        mutationFn: () => prepareFileImportSource(companyId as string),
        onSuccess: async (source) => {
            setPrepared(source)
            setChosenSource(source.id)
            await queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, "file-import", companyId) })
        },
    })

    const history = useQuery({
        queryKey: accountingQueryKey(userId, "file-import", companyId, "history", sourceId),
        queryFn: () => getFileImportHistory(sourceId as string),
        enabled: sourceId !== null && canImport,
        retry: false,
    })

    function selectFile(file: File | null) {
        dispatch({ type: "select-file", file })
    }

    async function runPreview() {
        if (!sourceId || !state.file || state.status === "previewing" || state.status === "confirming") return

        const token = state.fileToken
        const file = state.file
        dispatch({ type: "preview-started", sourceId })

        try {
            const preview = await previewGeneralLedger(sourceId, file)
            dispatch({ type: "preview-succeeded", preview, fileToken: token })
        } catch (error) {
            dispatch({ type: "preview-failed", message: fileImportErrorMessage(error), fileToken: token })
        }
    }

    async function runConfirm() {
        // Duplo clique: a referência barra antes mesmo do novo render.
        if (!sourceId || confirmingRef.current || !previewIsConfirmable(state, sourceId)) return

        const action: FileImportAction = { type: "confirm-started", idempotencyKey: crypto.randomUUID(), sourceId }
        const next = fileImportReducer(state, action)
        if (next === state) return

        confirmingRef.current = true
        dispatch(action)

        try {
            const request = confirmRequest(next)
            // Sempre a fonte do preview, nunca a fonte "atual" da tela.
            const receipt = await confirmGeneralLedger(request.accountingSourceId as string, request)
            dispatch({ type: "confirm-succeeded", receipt })
            for (const kind of ["file-import", "movements", "accounts", "coverage", "chart", "dre", "totals"]) {
                void queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, kind, companyId) })
            }
        } catch (error) {
            dispatch({ type: "confirm-failed", message: fileImportErrorMessage(error) })
        } finally {
            confirmingRef.current = false
        }
    }

    function startOver() {
        dispatch({ type: "reset" })
        if (inputRef.current) inputRef.current.value = ""
    }

    const movements = findAccountingScreenById("ctbmov")
    const depara = findAccountingScreenById("depara")

    // ------------------------------------------------------------ source panel
    let sourceBody: ReactNode

    if (!company) {
        sourceBody = (
            <div className="empty">
                <b>Selecione uma empresa para importar o Razão.</b>
                <p className="sub" style={{ marginBottom: 0 }}>
                    Use o contexto da Contabilidade no cabeçalho. O arquivo só pode ser preparado, pré-visualizado e
                    confirmado dentro de uma empresa ativa.
                </p>
            </div>
        )
    } else if (!canImport) {
        sourceBody = (
            <div className="empty">
                Sem acesso à importação de dados contábeis por arquivo nesta empresa (permissão "Importar dados
                contábeis por arquivo"). Escolher a empresa não libera a importação.
            </div>
        )
    } else if (readable.isLoading) {
        sourceBody = <div className="empty">Carregando as fontes contábeis…</div>
    } else if (readable.isError && apiErrorStatus(readable.error) !== 403) {
        sourceBody = (
            <div className="empty">
                Não foi possível carregar as fontes contábeis.{" "}
                <button type="button" className="lnk" onClick={() => void readable.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else if (candidates.length === 0) {
        sourceBody = (
            <div className="empty">
                {prepare.isPending ? (
                    "Preparando a fonte por arquivo…"
                ) : (
                    <>
                        <p>
                            Nenhuma fonte por arquivo encontrada para esta empresa. A importação por arquivo não usa o
                            Questor nem o Connector. Se a empresa já tiver uma fonte por arquivo, ela é reaproveitada.
                        </p>
                        <button
                            type="button"
                            className="btn primary sq"
                            disabled={prepare.isPending}
                            onClick={() => prepare.mutate()}
                        >
                            Preparar fonte por arquivo
                        </button>
                        {prepare.isError && (
                            <p className="sub" role="alert">
                                {unavailable(prepare.error)
                                    ? "A preparação de fonte por arquivo ainda não está disponível neste ambiente."
                                    : fileImportErrorMessage(prepare.error)}
                            </p>
                        )}
                    </>
                )}
            </div>
        )
    } else if (candidates.length > 1) {
        sourceBody = (
            <div className="ctb-cov">
                <span className="ctb-cov-l">Fonte por arquivo</span>
                <span className="ctb-cov-v">
                    <select
                        className="search"
                        aria-label="Fonte por arquivo"
                        value={sourceId ?? ""}
                        disabled={state.status === "previewing" || state.status === "confirming"}
                        onChange={(event) => {
                            setChosenSource(event.target.value || null)
                            startOver()
                        }}
                    >
                        <option value="">Escolha a fonte…</option>
                        {candidates.map((source, index) => (
                            <option key={source.id} value={source.id}>
                                Fonte por arquivo {index + 1}
                            </option>
                        ))}
                    </select>
                </span>
            </div>
        )
    } else {
        sourceBody = (
            <div className="ctb-cov">
                <span className="ctb-cov-l">Fonte por arquivo</span>
                <span className="ctb-cov-v">
                    <span className="tag ok">Selecionada</span> única fonte por arquivo ativa desta empresa.
                </span>
            </div>
        )
    }

    const preview = state.preview
    const busy = state.status === "previewing" || state.status === "confirming"

    return (
        <section id="p-ctbimp" className="grid" style={{ gap: 18 }} data-handoff="F4">
            <ScreenHeader {...props} />

            <div className="panel">
                <h3>Fonte por arquivo</h3>
                {company && (
                    <div className="ctb-cov" role="status" style={{ marginBottom: 12 }}>
                        <span className="ctb-cov-l">Importando Razão para:</span>
                        <span className="ctb-cov-v">
                            <b>{company.legalName}</b> · esta empresa é o contexto ativo da Área da Contabilidade.
                        </span>
                    </div>
                )}
                {sourceBody}
            </div>

            {company && canImport && sourceId !== null && (
                <div className="ctb-cov ctb-steps" role="list" aria-label="Etapas da importação">
                    <span className="ctb-cov-l">Importação em 2 etapas</span>
                    {importSteps(state, sourceId).map((step, index) => (
                        <span key={step.key} role="listitem" className="ctb-step">
                            <span className={step.status === "current" ? "pill on" : "pill"} aria-current={step.status === "current" ? "step" : undefined}>
                                {index + 1} · {step.label}
                            </span>
                            {step.status === "done" && <span className="tag ok">concluída</span>}
                            {step.status === "blocked" && <span className="tag pend">bloqueada</span>}
                        </span>
                    ))}
                </div>
            )}

            {company && canImport && sourceId !== null && state.status !== "done" && (
                <div className="panel">
                    <div className="toolbar">
                        <div className="l">
                            <h3 style={{ margin: 0 }}>Etapa 1 de 2 · Arquivo do Razão</h3>
                            <span className="tag est">Razão Analítico · .xls (layout homologado)</span>
                        </div>
                    </div>
                    <p className="sub">
                        Escolha o arquivo e clique em <b>Pré-visualizar</b>. Nada será gravado nesta etapa: a prévia só
                        lê o arquivo para você conferir antes da etapa 2.
                    </p>
                    <div className="cf-bar">
                        <label>
                            Arquivo{" "}
                            <input
                                ref={inputRef}
                                type="file"
                                accept=".xls"
                                aria-label="Arquivo do Razão (.xls)"
                                disabled={busy}
                                onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
                            />
                        </label>
                        <button
                            type="button"
                            className="btn sq"
                            disabled={!state.file || busy}
                            onClick={() => void runPreview()}
                        >
                            Pré-visualizar
                        </button>
                    </div>

                    {state.status === "waiting-file" && (
                        <div className="empty">Escolha o arquivo do Razão Analítico exportado em .xls.</div>
                    )}
                    {state.status === "file-selected" && !state.error && (
                        <div className="empty">
                            Arquivo escolhido. Clique em Pré-visualizar: nada será gravado nesta etapa.
                        </div>
                    )}
                    {state.status === "previewing" && <div className="empty">Gerando a pré-visualização…</div>}
                    {state.error && state.status !== "preview-ready" && (
                        <div className="warnbar" role="alert">
                            {state.error}
                        </div>
                    )}
                </div>
            )}

            {preview && state.status !== "done" && (
                <div className="panel">
                    <h3>Pré-visualização</h3>
                    {confirmBlockedReason(state) === null && company ? (
                        <div className="ctb-next" role="region" aria-label="Etapa 2 de 2">
                            <h3 style={{ margin: 0 }}>Etapa 2 de 2 · Confirmar e importar</h3>
                            <p style={{ margin: "6px 0 12px" }}>
                                A prévia está válida. Ao confirmar, os <b>{preview.movementCount}</b> movimentos de{" "}
                                <b>
                                    {preview.periodStart ? formatDay(preview.periodStart) : "—"} a{" "}
                                    {preview.periodEnd ? formatDay(preview.periodEnd) : "—"}
                                </b>{" "}
                                serão gravados como importados em <b>{company.legalName}</b>. Até lá, nada foi gravado.
                            </p>
                            <div className="ctb-acts" style={{ justifyContent: "flex-start" }}>
                                <button
                                    type="button"
                                    className="btn primary"
                                    disabled={!previewIsConfirmable(state, sourceId) || state.status === "confirming"}
                                    onClick={() => void runConfirm()}
                                >
                                    {state.status === "confirming" ? "Confirmando a importação…" : "Confirmar e importar"}
                                </button>
                                <button
                                    type="button"
                                    className="btn sq"
                                    disabled={state.status === "confirming"}
                                    onClick={startOver}
                                >
                                    Escolher outro arquivo
                                </button>
                            </div>
                            {state.status === "preview-ready" && state.error && (
                                <div className="warnbar" role="alert" style={{ marginTop: 12, marginBottom: 0 }}>
                                    {state.error} Você pode confirmar de novo.
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="warnbar ctb-err" role="alert">
                            <div>{confirmBlockedReason(state)}</div>
                            <button
                                type="button"
                                className="btn sq"
                                style={{ marginTop: 10 }}
                                onClick={startOver}
                            >
                                Escolher outro arquivo
                            </button>
                        </div>
                    )}
                    {preview.errors.length === 0 ? (
                        <div className="ctb-cov" role="status">
                            <span className="ctb-cov-l">Pré-visualização válida</span>
                            <span className="ctb-cov-v">
                                <span className="tag ok">Pronto para importar</span> nada foi gravado ainda.
                            </span>
                        </div>
                    ) : (
                        <div className="ctb-cov" role="alert">
                            <span className="ctb-cov-l">A pré-visualização encontrou erros</span>
                            <span className="ctb-cov-v">
                                <span className="tag pend">Não importável</span> corrija o arquivo e escolha-o de novo.
                            </span>
                        </div>
                    )}

                    <div className="cx-grid g-big">
                        <div className="card cxc al-info">
                            <span className="lab">Período detectado</span>
                            <span className="val" style={{ fontSize: "1.1rem" }}>
                                {preview.periodStart && preview.periodEnd
                                    ? `${formatDay(preview.periodStart)} a ${formatDay(preview.periodEnd)}`
                                    : "—"}
                            </span>
                            <span className="note">datas dos lançamentos do arquivo</span>
                        </div>
                        <div className="card cxc al-info">
                            <span className="lab">Contas observadas</span>
                            <span className="val">{preview.observedAccountCount}</span>
                            <span className="note">contas que aparecem no Razão (não é o plano de contas completo)</span>
                        </div>
                        <div className="card cxc al-info">
                            <span className="lab">Movimentos</span>
                            <span className="val">{preview.movementCount}</span>
                            <span className="note">de {preview.totalRows} linhas lidas</span>
                        </div>
                        <div className={preview.invalidRowCount > 0 ? "card cxc al-bad" : "card cxc al-ok"}>
                            <span className="lab">Linhas inválidas</span>
                            <span className="val">{preview.invalidRowCount}</span>
                            <span className="note">detalhadas nos erros abaixo</span>
                        </div>
                    </div>

                    <Issues title="Erros" issues={preview.errors} className="warnbar ctb-err" />
                    <Issues title="Avisos" issues={preview.warnings} className="warnbar" />

                    {preview.sampleRows.length > 0 && (
                        <>
                            <h3 style={{ marginTop: 16 }}>Amostra das linhas</h3>
                            <div className="tbl-wrap">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Linha</th>
                                            <th>Data</th>
                                            <th>Conta</th>
                                            <th>Lançamento</th>
                                            <th>Histórico</th>
                                            <th className="r">Débito</th>
                                            <th className="r">Crédito</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {preview.sampleRows.map((row) => (
                                            <tr key={row.sourceRow}>
                                                <td className="mono">{row.sourceRow}</td>
                                                <td className="mono">{formatDay(row.accountingDate)}</td>
                                                <td className="wrapcell">
                                                    {row.accountDescription ?? ""}
                                                    <small className="dp-cod">{row.sourceAccountId}</small>
                                                </td>
                                                <td className="mono">{row.sourceEntryKey}</td>
                                                <td className="wrapcell">{row.history ?? ""}</td>
                                                <td className="n">
                                                    {row.side === "DEBIT" ? formatExactDecimal(String(row.amount)) : ""}
                                                </td>
                                                <td className="n">
                                                    {row.side === "CREDIT" ? formatExactDecimal(String(row.amount)) : ""}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}

                    <p className="sub" style={{ marginTop: 10 }}>
                        A confirmação reenvia este mesmo arquivo. Os lançamentos passam a constar como importados por
                        arquivo; contas e movimentos não são reclassificados.
                    </p>
                </div>
            )}

            {state.status === "done" && state.receipt && (
                <div className="panel">
                    <h3>Importação concluída</h3>
                    <div className="cx-grid g-big">
                        <div className="card cxc al-ok">
                            <span className="lab">Período importado</span>
                            <span className="val" style={{ fontSize: "1.1rem" }}>
                                {formatDay(state.receipt.coveredFrom)} a {formatDay(state.receipt.coveredTo)}
                            </span>
                            <span className="note">cobertura importada de movimentos</span>
                        </div>
                        <div className="card cxc al-info">
                            <span className="lab">Linhas</span>
                            <span className="val">{state.receipt.rowCount}</span>
                            <span className="note">lidas no arquivo</span>
                        </div>
                        <div className="card cxc al-ok">
                            <span className="lab">Publicadas</span>
                            <span className="val">{state.receipt.publishedCount}</span>
                            <span className="note">movimentos importados</span>
                        </div>
                        <div className="card cxc al-info">
                            <span className="lab">Ignoradas</span>
                            <span className="val">{state.receipt.skippedCount}</span>
                            <span className="note">linhas sem movimento importável</span>
                        </div>
                    </div>
                    <p className="sub" style={{ marginTop: 14 }}>
                        Importado em <b>{company?.legalName ?? "—"}</b> em {formatInstant(state.receipt.committedAt)}.
                    </p>
                    <div className="ctb-acts" style={{ justifyContent: "flex-start" }}>
                        {movements && (
                            <button type="button" className="btn primary sq" onClick={() => navigate(movements.path)}>
                                Ver Movimentos Contábeis
                            </button>
                        )}
                        {depara && (
                            <button type="button" className="btn sq" onClick={() => navigate(depara.path)}>
                                Classificar no De-Para
                            </button>
                        )}
                        <button type="button" className="btn sq" onClick={startOver}>
                            Importar outro arquivo
                        </button>
                    </div>
                </div>
            )}

            {company && canImport && sourceId !== null && (
                <div className="panel">
                    <h3>Histórico de importações</h3>
                    {history.isLoading ? (
                        <div className="empty">Carregando o histórico…</div>
                    ) : history.isError ? (
                        <div className="empty">
                            {unavailable(history.error)
                                ? "O histórico de importações ainda não está disponível neste ambiente."
                                : apiErrorStatus(history.error) === 403
                                  ? "Sem acesso ao histórico de importações desta fonte."
                                  : "Não foi possível carregar o histórico de importações."}
                        </div>
                    ) : (
                        <div className="tbl-wrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Importado em</th>
                                        <th>Período</th>
                                        <th className="r">Linhas</th>
                                        <th className="r">Publicadas</th>
                                        <th className="r">Ignoradas</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {historyRows(history.data).map((row) => (
                                        <tr key={`${row.committedAt}-${row.coveredFrom}`}>
                                            <td className="mono">{formatInstant(row.committedAt)}</td>
                                            <td className="mono">
                                                {formatDay(row.coveredFrom)} a {formatDay(row.coveredTo)}
                                            </td>
                                            <td className="n">{row.rowCount}</td>
                                            <td className="n">{row.publishedCount}</td>
                                            <td className="n">{row.skippedCount}</td>
                                        </tr>
                                    ))}
                                    {historyRows(history.data).length === 0 && (
                                        <tr>
                                            <td colSpan={5} className="empty">
                                                Nenhuma importação concluída nesta fonte.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}
        </section>
    )
}
