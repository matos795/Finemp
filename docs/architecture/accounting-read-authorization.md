# Leitura contábil explícita — #75 / #37

## FATO CONFIRMADO

O guard genérico anterior aceitava qualquer grant de empresa com escopo ALL,
inclusive com conjunto de permissões vazio, para razão e lista de classificações.
As escritas já tinham guards específicos desde #68. Explicitar a leitura é
pré-requisito de segurança do gerenciamento de grants, não uma regra contábil.

## DECISÃO técnica implementada

| Operação GET | Permissão da empresa |
| --- | --- |
| `accounting-movements` | `ACCOUNTING_MOVEMENT_VIEW` |
| `accounting-accounts/classifications` | `ACCOUNT_CLASSIFICATION_VIEW` ou `ACCOUNT_CLASSIFICATION_MANAGE` |
| `dre` | `DRE_VIEW` (inalterada) |

Sessão, administração da plataforma/contabilidade e rótulo ADMIN não substituem
uma permissão financeira. Todas exigem escopo ALL. RESTRICTED continua negado,
inclusive com permissão explícita, até existir filtragem real por centro de custo.
Rotas de empresa não registradas explicitamente são negadas por padrão.

A migração V15 registra as duas capacidades de leitura que os vínculos legados
já possuíam. Não adiciona MANAGE, não cria vínculos e não muda escopo.
Vínculos criados depois da migração não recebem nada automaticamente.
A migração é testada do V14 para V15 em PostgreSQL descartável e isolado;
não executa limpeza nem consultas no ambiente operacional.

## PONTO ABERTO / próxima entrega

Drill-down de uma linha da DRE não equivale a consultar todo o razão. O contrato
de drill-down filtrado será tratado em reporting, sem ampliar DRE_VIEW para o
endpoint bruto. Paginação do razão permanece no roadmap, não é alegada aqui.
