# Demonstração reproduzível do núcleo financeiro parcial

## FATO / escopo

O cenário automatizado `BankStatementPersistenceIntegrationTest.demonstrablePresentationCoreReconcilesDreCashTitlesAndEffects` verifica os contratos de leitura PostgreSQL e HTTP. Usa dados sintéticos isolados, criados diretamente no fixture do teste. Não carrega dados reais e não substitui testes dos comandos de criação/baixa. DRE operacional financeira não é a DRE contábil Questor homologada da PLP.

## Cenário e valores esperados

Company sintética, competência julho/2026, data-base 31/07/2026, escopo ALL. Todas as receitas classificadas em REC; a despesa em DADM.

| Título | Nominal | Vencimento | Baixa / efetivo |
| --- | ---: | --- | --- |
| Receita recebida | 3,00 | 01/07/2026 | 01/07/2026 / 3,10 |
| Receita vencida | 3,00 | 01/07/2026 | Em aberto |
| Receita futura | 1,00 | 10/08/2026 | Em aberto |
| Despesa administrativa | 1,00 | Julho/2026 | Em aberto |

Resultado esperado: REC 7,00; DADM −1,00; RFIN 0,10 pela diferença da baixa. Receita líquida/lucro bruto 7,00; EBITDA/EBIT 6,00; LAIR/resultado líquido 6,10. Outras linhas zero. Margens bruta 100,0%, EBITDA 85,7% e líquida 87,1%.

Caixa realizado: entrada 3,10, saída zero, geração 3,10 e margem 100,0%. A geração não é saldo bancário. A receber: total nominal 4,00, vencido 3,00, proporção vencida 75,0%. A pagar: nominal 1,00. A competência da receita futura já integra a DRE de julho; seu vencimento integra o caixa aberto de agosto.

## Reprodução técnica

Em ambiente de testes com Java 21 e Docker/PostgreSQL via Testcontainers, executar a partir de `backend`:

```sh
./mvnw -Dtest=BankStatementPersistenceIntegrationTest#demonstrablePresentationCoreReconcilesDreCashTitlesAndEffects test
```

O CI executa esse teste na suíte PostgreSQL. O fixture é descartado ao término; não cria uma Company de demonstração permanente. Para uma sessão interativa, usar um ambiente descartável e os comandos autorizados existentes para preparar dados equivalentes. Não executar scripts de seed em bases reais.

## Roteiro de conferência para a reunião

1. Explicar competência versus baixa com os quatro títulos acima.
2. Consultar `GET /api/companies/{companyId}/financial-reports/presentation-snapshot?month=2026-07&asOf=2026-07-31&scope=ALL` com sessão autorizada. Mostrar contexto, `partial`, `operationalDre`, caixa, títulos e `indicators` da mesma resposta consistente.
3. Mostrar que valores e percentuais são retornados pelo backend; a apresentação apenas formata. Percentual textual `87.1` significa 87,1%, não uma fração a multiplicar novamente.
4. Conferir `GET /api/companies/{companyId}/financial-reports/operational-dre/effects?month=2026-07&line=RFIN`: um efeito de 0,10, vinculado à receita recebida. O teste também soma os efeitos de cada linha e compara com a DRE.
5. Informar que chamadas de detalhe posteriores não compartilham o snapshot HTTP anterior. Em demonstração estável os dados são iguais; não alegar congelamento entre requisições.

## PONTOS ABERTOS e limites

Sem novas regras para NAO, bens/depreciação automática, imposto automático, #197 ou #128. Sem reconstrução histórica, orçamento, forma efetiva da baixa presumida, PDF oficial, compartilhamento ou consolidação. A conexão visual da Apresentação com o backend não é comprovada por este teste. O cenário demonstra o núcleo parcial e não encerra #41 nem homologa operação em produção.

Próximo passo de produto: validar com João a leitura do núcleo e suas fontes ausentes; avaliar o maior bloco sustentado restante usando o [checkpoint](presentation-mvp-checkpoint.md), sem fila de micro-relatórios.
