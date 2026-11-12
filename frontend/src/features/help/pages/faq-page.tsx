const faq = [
    {
        question: "Por que algumas empresas não aparecem para mim?",
        answer: "O FINEMP aplica autorização por Company. Um usuário comum vê apenas as empresas concedidas a ele; SYSTEM_ADMIN possui acesso global efetivo.",
    },
    {
        question: "Qual a diferença entre lançamento e extrato?",
        answer: "Lançamento é o registro financeiro do FINEMP. Extrato é o movimento bancário importado. A conciliação liga os dois sem duplicar o fato financeiro.",
    },
    {
        question: "O que acontece ao dar baixa?",
        answer: "A baixa registra a realização do título usando o comando oficial do backend. Dashboard, fluxo e saldos passam a refletir o estado realizado.",
    },
    {
        question: "Posso desfazer uma baixa?",
        answer: "Sim, quando as regras do período permitem. Use o estorno; o backend valida versão, período e demais invariantes antes de reverter.",
    },
    {
        question: "Por que uma tela ainda aparece como Em breve?",
        answer: "SYSTEM_ADMIN enxerga o roadmap homologado. Em breve significa que a tela ainda não possui uma rota funcional publicada; não é uma restrição de permissão.",
    },
    {
        question: "O Questor é alterado pelo FINEMP?",
        answer: "Não no desenho atual. Questor/Firebird é fonte contábil externa somente leitura; o FINEMP mantém seus próprios dados no PostgreSQL.",
    },
]

export function FaqPage() {
    return (
        <section className="mbox">
            <h1>Perguntas frequentes</h1>
            <p className="help">
                Respostas sobre os fluxos já publicados no FINEMP.
            </p>
            <div style={{ display: "grid", gap: 12 }}>
                {faq.map((item) => (
                    <article className="card" key={item.question}>
                        <h2>{item.question}</h2>
                        <p>{item.answer}</p>
                    </article>
                ))}
            </div>
        </section>
    )
}
