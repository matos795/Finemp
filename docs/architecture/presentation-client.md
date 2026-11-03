# Client da Apresentação — #53 / #254

## FATO CONFIRMADO

O backend expõe snapshot parcial consistente e efeitos paginados. O client em `frontend/src/lib/api/presentation-client.ts` usa o transporte de sessão já existente; não calcula totais, indicadores ou autorização. Tipos são mantidos conforme OpenAPI, não são validação runtime da resposta.

```ts
import { getPresentationSnapshot, getOperationalDreEffects } from "./lib/api/presentation-client"

const controller = new AbortController()
const snapshot = await getPresentationSnapshot(companyId, {
  month: "2026-07", asOf: "2026-07-31", scope: "ALL",
}, controller.signal)
const effects = await getOperationalDreEffects(companyId, {
  month: "2026-07", line: "RFIN", page: 0, size: 20,
}, controller.signal)
```

`companyId` vem do contexto autorizado. O servidor continua verificando acesso; a existência de um id no consumidor não concede permissão. Cancelar requests anteriores ao trocar Company/período e descartar resultados de seleções antigas na integração visual.

## DECISÃO

Mês, data-base, escopo e paginação explícitos; sem padrão de período inventado. Dinheiro/percentuais permanecem strings. `percent` já usa unidade PERCENT: `87.1` significa 87,1%; não multiplicar novamente por 100. Base indisponível fica null, DRE desabilitada fica ausente. Não fabricar bloco zerado para erro/404 ou esconder `partial`/`coverage`.

Cookies são enviados pelo transporte existente. GET não exige uma chamada CSRF extra; login e mutações continuam usando o fluxo CSRF já implementado. Erros preservam `ApiError.status`, sem retry automático ou cache financeiro. `AbortSignal` é repassado. A aplicação deve distinguir 401 (sessão), 403 (acesso), 404 (rota/opt-in indisponível) e demais falhas, sem interpretar como saldo zero.

O detalhe é uma requisição posterior: não é a mesma transação do snapshot. Não combinar respostas de Companies/períodos diferentes. Use todas as páginas se precisar consultar todos os efeitos; cálculos de apresentação continuam no backend.

## Validação e próximos passos

Node 24 executa `node --test tests/*.test.mjs` sem dependência de runner adicional. CI frontend executa testes, lint e build; CI backend permanece independente. Testes de transporte simulam HTTP: não alegam acesso a staging nem paridade visual.

PROPOSTA: próxima etapa da #53 é preparar ambiente sintético descartável e conectar a Apresentação ao client, usando o [roteiro](presentation-core-demo.md). Registrar sessão real, Company/usuário autorizados, comandos de criação/baixa e conferência dos valores; nenhum seed automático em base real.

PONTO ABERTO operacional: staging publicado e paridade visual ainda não comprovados. #41/#53 não são encerradas por este client. #197, #128, NAO e fontes ausentes permanecem isolados.
