# Área da Contabilidade: integração M3–M6 nas telas — #454

Base: [shell do protótipo](accounting-area-shell.md) (#452). Mapa original de telas:
[accounting-area-map](../reference/accounting-area-map.md) (cópia fiel, não editada).

## Telas e contratos

| Tela | Situação | Contrato | O que é real | O que continua "Em breve" |
| --- | --- | --- | --- | --- |
| Contexto (filtro do cabeçalho) | funcional | `GET /api/auth/accounting-portfolio` (#435), `GET /api/companies/{id}/accounting-sources` (#440), `POST /api/accounting-sources/{id}/sync-runs` | Contabilidade → Cliente → Empresa da carteira; fontes, base sincronizada, última execução; solicitação INITIAL idempotente com `QUESTOR_SYNC` | Presença/versão do Connector (#432) |
| Barra de competência (14 telas da Central) | funcional | #440 | "Base contábil até" pela última SyncRun SUCCEEDED | — |
| `depara` De-Para do Plano de Contas | parcial | `GET .../accounting-accounts` (#442), `GET /api/dre-classifications`, `PUT .../classification` | Cards (importadas, pendentes, classificadas), filtros Pendentes/Todas, busca, páginas do servidor, editor inline na DRE | Contas novas, Divergentes, Com regra, regras por grupo, balanço/patrimonial, saldo 12m |
| Composição (`#mDd`) a partir da lupa da conta | parcial | `GET .../accounting-movements/search` (#444) | Lançamentos por período, débito/crédito e totais exatos | Contrapartida, documento, CC, encerramento, saldo inicial (`notSynchronized`) |
| `cfpre` Pré-fechamento | parcial | `GET /api/companies/{id}/dre` (#446) | DRE mês a mês (6 meses + competência) com os totais do backend; mês fora da base sinalizado; 422 de valor negativo exibido | Alertas, média e variação, flags de pré-fechamento, preparados, balancete |
| Demais 36 telas | Em breve | — | Estrutura do protótipo | Tudo (dependem de C1–C6 e O1–O21) |

## DECISÃO técnica

- Nada é recalculado no React: cada célula da DRE é um total devolvido pelo backend; valores
  monetários são strings decimais exatas formatadas sem arredondar (`100,0001`).
- Cache por usuário da sessão (`["accounting-area", userId, …]`) e removido no logout.
- Textos explicativos seguem o protótipo: saem da tela e ficam no botão (i) → `#mInfo`.
  Avisos que mudam a leitura do dado (base não sincronizada, erro 422, contas sem
  classificação) ficam visíveis em `.warnbar`.
- Permissões só ajustam a interface (lupa, editor, sincronizar); o backend autoriza tudo.
