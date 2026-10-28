# Persistência operacional de lançamentos — Issue131 /39

## FATO CONFIRMADO

FinancialEntry representa lançamento financeiro operacional; AccountingMovement continua fato contábil importado. A referência registra direção, descrição, nominal, datas de documento/lançamento/competência/vencimento/pagamento, referências de banco/categoria/parceiro/centro, override fixo/variável, forma/documento/observação. Valor efetivo usa vpago quando pago e presente; nos demais casos usa nominal. Estorno remove pagamento/valor efetivo. Não há restrição observada de ordem cronológica entre as datas.

Parcelas geradas podem ser zero quando o total é menor que a quantidade. Por isso, o armazenamento canônico não impõe nominal positivo universalmente; validação do valor bruto de captura é outra responsabilidade.

## DECISÃO TÉCNICA

Modelo relacional por Company/UUID e V28, com perfil imutável embutido. Valores já canônicos em centavos exatos BigDecimal/NUMERIC(19,2), zero ou positivos; subcentavos/negativos/fora da representação são recusados antes de expansão de expoentes. Zeros e casas finais equivalentes normalizam-se sem arredondamento. Isso é uma invariável do estado persistido e não decide como normalizar o valor bruto de um formulário nem o ponto monetário128.

LocalDate/YearMonth com representação ISO anos1–9999, sem cronologia deduzida. Competência VARCHAR7 tem converter explícito e validação física. Valor pago só pode existir com data de pagamento; pagamento com valor omitido usa o nominal. Não se inferem novas restrições de juros/desconto a partir da diferença.

FKs compostas company/id protegem banco, categoria, parceiro e centro mesmo fora dos serviços. @Version detecta gravação desatualizada. createdAt/updatedAt e createdBy/updatedBy são metadados de rastreabilidade com timestamps do servidor e referência de usuário, preservando criador. Eles não são a trilha completa de auditoria de eventos prevista na #51. Diagnósticos não incluem descrição, documento, notas ou valores em toString.

Quatro adapters independentes consultam uso real por FinancialEntry nos cadastros. Não compartilham uma implementação ambígua de hasReferences para UUIDs de tipos diferentes. Categoria inclui entradas de suas filhas; centro distingue hasEntries/hasReferences. Consultas são escalares e company-scoped. FKs continuam impedindo exclusões inseguras mesmo sem adapter.

Feature FINEMP_FINANCE_ENTRIES_ENABLED, default false, ativa esses adapters. Ela não habilita API de captura; esta entrega não cria controller nem novo writer. A barreira das rotas de Empresa permanece negando acesso a financial-entries. Não há gravação real, importação, comunicação externa ou relação implícita com AccountingSource/Questor.

## DEPENDÊNCIAS E PONTOS ABERTOS

Serviços de comando/consulta com autorização financeira, captura/normalização, semântica de parceiro/categoria/centro/banco, idempotência, baixa/estorno e transferência seguem incrementos da39. Existência de FK não é comprovação dessas regras prontas. Série/calendário é componente próprio; a decisão monetária128 continua isolada. Estoque, anexos, contratos e auditoria completa têm módulos dependentes.

Adapters de transferências e contratos ainda devem ser acrescentados aos respectivos módulos39/42. Os adapters atuais não afirmam encontrar referências que ainda não são persistidas. A escrita operacional futura deve usar o mesmo lock de Company dos cadastros, além das FKs, para manter consistência entre classificação e mutações.

## VALIDAÇÃO

TDD de centavos/expoentes, zero canônico, nominal/efetivo e coerência de pagamento (RED → GREEN). PostgreSQL verifica round-trip/versão/rastreabilidade, FKs nos quatro vínculos, duas Contabilidades/Clientes/Empresas, uso real pelos serviços de cadastro, veto de pai com lançamentos, UUID igual em tipos distintos e perda de atualização. HTTP comprova ausência de writer prematuro. Docker local indisponível: compilar contratos não significa executar PostgreSQL; CI executa os testes integrados.
