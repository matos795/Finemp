# Checkpoint do núcleo financeiro / Apresentação — checkpoint após #248

Escopo da evidência: requisitos confirmados neste Work, matriz homologada F01–F07, funções pontuais do protótipo já extraído e contratos atuais da main. Não afirma paridade visual integral com um deck/tela de Apresentação não encontrado nos artefatos locais consultados.

| Bloco | Estado do backend | Limite relevante |
| --- | --- | --- |
| DRE por competência, diferença de baixa e subtotais F07 | Parcial, API e snapshot consistentes | FINANCIAL_ENTRIES; bens/depreciação/alienação automáticos dependem #47; NAO segue ponto aberto |
| Conferência dos efeitos por linha | #249 integrado | Lista plana; não decide hierarquia; sem snapshot entre chamadas |
| Caixa diário/mensal/anual, atividade, períodos e títulos | Parcial | Realizado e aberto existentes; sem saldo/projeção/orçamento como se prontos |
| Pagar/receber, aging e subtotais por parceiro | Atendidos no recorte atual | Estado atual com data-base, sem reconstrução de alterações históricas; centros não cobertos |
| Apresentação de blocos agregados | Parcial e consistente | Uma transação, período explícito; DRE opt-in; sem persistência/compartilhamento/PDF oficial |
| Indicadores monetários DRE | Atendidos no recorte parcial F07 | Não representam fontes ausentes |
| Margens essenciais DRE, geração/margem de caixa e taxa de vencidos | Integrados na #251 / #250 | Fórmulas confirmadas e dados já disponíveis; precisão de apresentação deve ser explícita |
| Recebimentos por forma efetiva | Indisponível | Baixa não possui forma efetiva persistida; não usar forma prevista do título |
| Real × orçamento, saldo/projeção/autonomia, capital de giro | Indisponíveis no snapshot | Domínios/queries e decisões próprias; não derivar de totais parciais por hipótese |

## Decisões e dependências isoladas

#197 (baixa conciliada em período aberto), #128 (captura/parcelamento monetário), significado futuro de NAO, hierarquia oficial, fontes gerenciais adicionais, período padrão, compartilhamento/comentários/PowerPoint/consolidado não são decididos aqui. Fontes e módulos ainda não implementados não equivalem automaticamente a uma dúvida de negócio: bens #47 e orçamento #44 têm entregas próprias.

## Próximo maior bloco sustentado

DECISÃO #250: indicadores essenciais derivados dos blocos existentes, no MESMO snapshot: margens bruta/EBITDA/líquida sobre receita líquida (renderDRE_), geração de caixa realizado e sua margem (painel), vencidos a receber/total a receber (painel/aging). Esses dados já existem; o bloco melhora a leitura executiva sem depender de ativo, orçamento, saldo ou forma efetiva inexistente. Não abrir uma fila de detalhes auxiliares.

Evidência de formato: fmtPct na referência apresenta percentual com uma casa decimal. DECISÃO técnica #250: percentual textual, cálculo BigDecimal direto numerator*100/denominator com uma casa/HALF_UP, sem pré-arredondar a razão; base zero retorna indisponível, não zero. Margens DRE admitem RL negativa conforme abs(RL)>0.004; em centavos canônicos isto equivale a RL != 0. Caixa/recebíveis exigem base positiva. Preservar cobertura e contexto dos blocos, sem afirmar compatibilidade bit a bit com ponto flutuante em fronteiras.

## Menor conjunto para demonstração técnica ao João

1. Rastreabilidade e indicadores integrados (#249/#251).
2. Cenário de leitura sintético e roteiro documentado em [demonstração do núcleo](presentation-core-demo.md), com DRE, caixa, títulos e soma dos efeitos; teste PostgreSQL/HTTP. Setup direto não substitui testes dos comandos.
3. Exibir os valores do backend na Apresentação homologada; sem fórmulas locais. A conexão/validação visual do frontend é uma etapa distinta, não alegada pronta pelo backend headless.

Isto define uma demonstração do núcleo parcial, não encerramento da #41, homologação operacional completa ou liberação de produção. Não exigir completar todos os módulos para demonstrar com limites claros; tampouco ocultar fontes ausentes.


## Checkpoint obrigatório do MVP financeiro — 02/11/2026

### FATO CONFIRMADO
1. **Implementado no recorte atual:** DRE por competência e diferença de baixa, subtotais F07 e rastreabilidade por linha; caixa diário/mensal/anual realizado e aberto; pagar/receber, aging e agrupamento por parceiro; indicadores essenciais; snapshot consistente com Company, mês/data-base/escopo explícitos.
2. **Parcial:** DRE limitada a FINANCIAL_ENTRIES, caixa sem saldo/projeção completos, Apresentação sem interface integrada validada. Detalhes em requisições posteriores não são congelados no mesmo snapshot.
3. **Indisponível na Apresentação atual:** recebimentos por forma efetiva, orçamento, fontes automáticas de bens, balanço/NCG completos, PDF oficial, persistência/compartilhamento e apresentação consolidada. Não significa que cada ausência seja uma regra desconhecida; várias são entregas pendentes.
4. **Decisão de negócio pendente:** #197 (alteração/estorno de baixa conciliada em período aberto), #128, tratamento futuro de NAO, hierarquia oficial, fallback de período sem fechamento, regras/fontes gerenciais ainda não homologadas e colaboração/consolidação. Nenhuma foi resolvida por hipótese.
5. **Novos domínios relevantes:** bens/depreciação/alienação #47 e orçamento #44; forma efetiva precisa existir na baixa antes do relatório por forma (a prevista não a substitui). Balanço e capital de giro dependem também de fontes hoje ausentes. Não bloquear o recorte demonstrável com esses módulos.
6. **Demonstrável?** Sim, de ponta a ponta dos contratos de LEITURA PostgreSQL/HTTP com dados sintéticos, conforme #252/#253. Fixture direto não demonstra o fluxo interativo de criação/baixa, login real de navegador ou frontend integrado. O cenário fecha DRE, efeitos, caixa, títulos e indicadores; não equivale a homologação completa ou produção.

### DECISÃO
Não encerrar #41 e não abrir mais micro-relatórios. Priorizar #53: client fino para contratos implementados, validação de paridade e ambiente sintético de uso. Backend permanece a fonte dos cálculos, permissões e persistência.

### PROPOSTA — menor conjunto restante para chamar o recorte de MVP financeiro
7. Entregar consumo tipado do snapshot e efeitos com cookies, cancelamento e erros sem fallback inventado; preparar ambiente descartável acessível com usuário/Company sintéticos por fluxo autorizado; executar roteiro interativo autenticação → lançamento/baixa → Apresentação, conferir valores contra backend e validar cobertura/limites com João. Registrar evidência de isolamento e não acesso entre empresas. Confirmar o aceite deste recorte como MVP, sem exigir antes todos os domínios grandes nem presumir esse aceite.

### PONTO ABERTO operacional
Não há evidência nesta entrega de staging publicado/configurado nem integração visual homologada. O próximo incremento seguro da #53 é o client e seus testes/CI; preparação de staging e validação visual seguem como entregas explícitas, sem publicar dados reais ou alegar prontidão.
