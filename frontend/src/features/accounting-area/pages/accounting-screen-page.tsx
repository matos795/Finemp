import type { AccountingScreen } from "../lib/accounting-screens"

interface CompetenceBarProps {
    screenId: string
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/** cfBar do protótipo: competência em fechamento + "Base contábil até". */
export function CompetenceBar({ screenId, competence, baseText, onCompetenceChange }: CompetenceBarProps) {
    return (
        <div id={`${screenId}Bar`} className="cf-bar">
            <div className="cf-bar no-print">
                <label>
                    Competência em fechamento{" "}
                    <input
                        type="month"
                        className="search cf-comp"
                        value={competence}
                        data-cfbar={screenId}
                        onChange={(event) => {
                            if (event.target.value) {
                                onCompetenceChange(event.target.value)
                            }
                        }}
                    />
                </label>
                <span className="muted">{baseText}</span>
            </div>
        </div>
    )
}

/** Painel do protótipo sem contrato oficial: estrutura visível, sem dado. */
export function SoonPanel({ title }: { title: string }) {
    return (
        <div className="panel ctb-soon" aria-disabled="true">
            {title && <h3>{title}</h3>}
            <div className="empty">
                <b>Em breve.</b> Este painel ainda não está ligado à API oficial do FINEMP; nenhum dado é exibido.
            </div>
        </div>
    )
}

interface AccountingScreenPageProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/**
 * Tela da Área da Contabilidade na estrutura do protótipo: section#p-{código},
 * bloco de cabeçalho (.ind-bloco.emp com selo, título e barra de competência)
 * e os painéis na ordem e com os títulos do protótipo.
 */
export function AccountingScreenPage({ screen, competence, baseText, onCompetenceChange }: AccountingScreenPageProps) {
    return (
        <section id={`p-${screen.id}`} className="grid" style={{ gap: 18 }} data-handoff={screen.handoffSection}>
            {screen.tag !== null && (
                <div className="ind-bloco emp">
                    <div className="ind-head">
                        <span className="ind-tag">{screen.tag}</span>
                        <h3>{screen.title}</h3>
                        {screen.status === "soon" && <span className="stc info ctb-soon-tag">Em breve</span>}
                    </div>
                    <div className="sub is-info" id={`${screen.id}Sub`}>
                        {screen.objective}
                    </div>
                    {screen.competenceBar && (
                        <CompetenceBar
                            screenId={screen.id}
                            competence={competence}
                            baseText={baseText}
                            onCompetenceChange={onCompetenceChange}
                        />
                    )}
                </div>
            )}

            {screen.panels.map((title, index) => (
                <SoonPanel key={`${screen.id}-${index}`} title={title} />
            ))}
        </section>
    )
}
