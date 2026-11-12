const tutorials = [
    {
        title: "Preparar uma empresa para o primeiro lançamento",
        steps: [
            "Confirme que existe pelo menos uma conta bancária.",
            "Abra Plano de contas e aplique/crie as categorias necessárias.",
            "Abra Lançamentos e registre a receita ou despesa com competência e vencimento.",
        ],
    },
    {
        title: "Registrar pagamento ou recebimento",
        steps: [
            "Abra Pagar e receber ou o lançamento desejado.",
            "Escolha a ação de baixa.",
            "Informe os dados solicitados e confirme.",
            "Confira Painel, Contas e saldos e os relatórios após a operação.",
        ],
    },
    {
        title: "Estornar uma baixa",
        steps: [
            "Abra o lançamento já realizado.",
            "Use a ação de estorno.",
            "Confirme que o período está aberto e finalize o comando.",
            "Confira se o título voltou ao estado anterior e se os saldos foram revertidos.",
        ],
    },
    {
        title: "Conciliar um extrato",
        steps: [
            "Importe ou abra os movimentos em Extratos e conciliação.",
            "Para um movimento com título existente, escolha Conciliar.",
            "Para um fato ainda não registrado, escolha Criar lançamento.",
            "Para um movimento que não deve virar financeiro, use Ignorar.",
        ],
    },
]

export function TutorialsPage() {
    return (
        <section className="mbox">
            <h1>Tutoriais guiados</h1>
            <p className="help">
                Passos curtos para executar os fluxos atualmente disponíveis.
            </p>
            <div style={{ display: "grid", gap: 12 }}>
                {tutorials.map((tutorial) => (
                    <article className="card" key={tutorial.title}>
                        <h2>{tutorial.title}</h2>
                        <ol>
                            {tutorial.steps.map((step) => (
                                <li key={step}>{step}</li>
                            ))}
                        </ol>
                    </article>
                ))}
            </div>
        </section>
    )
}
