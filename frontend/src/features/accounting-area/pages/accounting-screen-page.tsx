import type { AccountingScreen } from "../lib/accounting-screens"
import { InfoButton } from "../components/accounting-info"

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

export interface AccountingScreenPageProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/** Bloco de cabeçalho da tela (.ind-bloco.emp): selo, título, objetivo e barra de competência. */
export function ScreenHeader({ screen, competence, baseText, onCompetenceChange }: AccountingScreenPageProps) {
    if (screen.tag === null) {
        return null
    }

    return (
        <div className="ind-bloco emp">
            <div className="ind-head">
                <span className="ind-tag">{screen.tag}</span>
                <h3>
                    {screen.title}
                    <InfoButton title={`${screen.tag} · ${screen.title}`}>
                        <p className="info-p">{screen.objective}</p>
                    </InfoButton>
                </h3>
                {screen.status === "soon" && <span className="stc info ctb-soon-tag">Em breve</span>}
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
    )
}

/**
 * Tela da Área da Contabilidade na estrutura do protótipo: section#p-{código},
 * bloco de cabeçalho e os painéis na ordem e com os títulos do protótipo.
 */
export function AccountingScreenPage(props: AccountingScreenPageProps) {
    const { screen } = props

    return (
        <section id={`p-${screen.id}`} className="grid" style={{ gap: 18 }} data-handoff={screen.handoffSection}>
            <ScreenHeader {...props} />

            {screen.panels.map((title, index) => (
                <SoonPanel key={`${screen.id}-${index}`} title={title} />
            ))}
        </section>
    )
}
