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
| Margens essenciais DRE, geração/margem de caixa e taxa de vencidos | Implementados na #250, sujeitos aos gates de integração | Fórmulas confirmadas e dados já disponíveis; precisão de apresentação deve ser explícita |
| Recebimentos por forma efetiva | Indisponível | Baixa não possui forma efetiva persistida; não usar forma prevista do título |
| Real × orçamento, saldo/projeção/autonomia, capital de giro | Indisponíveis no snapshot | Domínios/queries e decisões próprias; não derivar de totais parciais por hipótese |

## Decisões e dependências isoladas

#197 (baixa conciliada em período aberto), #128 (captura/parcelamento monetário), significado futuro de NAO, hierarquia oficial, fontes gerenciais adicionais, período padrão, compartilhamento/comentários/PowerPoint/consolidado não são decididos aqui. Fontes e módulos ainda não implementados não equivalem automaticamente a uma dúvida de negócio: bens #47 e orçamento #44 têm entregas próprias.

## Próximo maior bloco sustentado

DECISÃO #250: indicadores essenciais derivados dos blocos existentes, no MESMO snapshot: margens bruta/EBITDA/líquida sobre receita líquida (renderDRE_), geração de caixa realizado e sua margem (painel), vencidos a receber/total a receber (painel/aging). Esses dados já existem; o bloco melhora a leitura executiva sem depender de ativo, orçamento, saldo ou forma efetiva inexistente. Não abrir uma fila de detalhes auxiliares.

Evidência de formato: fmtPct na referência apresenta percentual com uma casa decimal. DECISÃO técnica #250: percentual textual, cálculo BigDecimal direto numerator*100/denominator com uma casa/HALF_UP, sem pré-arredondar a razão; base zero retorna indisponível, não zero. Margens DRE admitem RL negativa conforme abs(RL)>0.004; em centavos canônicos isto equivale a RL != 0. Caixa/recebíveis exigem base positiva. Preservar cobertura e contexto dos blocos, sem afirmar compatibilidade bit a bit com ponto flutuante em fronteiras.

## Menor conjunto para demonstração técnica ao João

1. Rastreabilidade integrada; estabilizar o bloco de indicadores sustentados no snapshot.
2. Um cenário sintético reproduzível e roteiro de consulta que mostre entrada, baixa, DRE, caixa, títulos e soma dos efeitos, identificando a cobertura parcial.
3. Exibir os valores do backend na Apresentação homologada; sem fórmulas locais. A conexão/validação visual do frontend é uma etapa distinta, não alegada pronta pelo backend headless.

Isto define uma demonstração do núcleo parcial, não encerramento da #41, homologação operacional completa ou liberação de produção. Não exigir completar todos os módulos para demonstrar com limites claros; tampouco ocultar fontes ausentes.
