# Contas financeiras operacionais — Issue #118 / #38

## FATO DA REFERÊNCIA HOMOLOGADA

Consulta pontual à cópia privada já extraída, sem refazer inventário/ZIP: `saldoConta` (529–534) e cadastro/addConta/delConta (5444–5502) usam id, nome, tipo, saldoIni, dataIni e ativa. Tipos: Conta corrente, Poupança, Caixa, Aplicação, Conta digital e Outra. Exclusão com uso vira desativação; sem uso, a última linha não é removida. A contagem inclui inativas. `saldoConta` usa saldo inicial e movimentos realizados, sem aplicar dataIni como filtro; este módulo não cria cálculo de saldo nem deduz uma regra de vigência dessa data.

BankAccount é operacional. Não é AccountingAccount, AccountingMovement, AccountingSource ou plano gerencial/PlanoQ. Não foram inventados número/agência, credenciais bancárias ou dados financeiros novos.

## DECISÃO TÉCNICA

Tabela própria por UUID/Empresa, chave composta (company_id,id) para referências futuras e @Version inicial zero. Campos explícitos no contrato: name, type, openingBalance, openingDate e active. Date entre anos1–9999 é limite técnico do contrato ISO. O frontend fornece os padrões visuais já observados (conta ativa, saldo0, primeiro dia do mês); o servidor não presume fuso/data de uma operação omitida.

Saldo inicial é BigDecimal em centavos exatos, NUMERIC(19,2). Negativo permitido; não há arredondamento automático. Centesimais equivalentes com zeros finais são normalizados. Frações menores que centavo e valores fora da representação são recusados antes de expansão de expoentes; zero tem caminho limitado. Trata-se de validação de representação na API, não nova fórmula financeira. A referência UI já normaliza via r2 antes de gravar. Cálculos/paridade monetária dos relatórios seguem suas próprias entregas. Resposta usa string decimal de duas casas para evitar perda de precisão no consumidor.

CRUD condicionado a FINEMP_FINANCE_CATALOG_ENABLED (default false). BANK_ACCOUNT_VIEW ou MANAGE permite leitura; escrita exige MANAGE. Ambas requerem grant por Empresa e escopo ALL. Papel ADMIN/Contabilidade/Plataforma não concede acesso financeiro automaticamente; permissões novas não são inseridas em grants existentes. RESTRICTED permanece negado enquanto não houver filtro financeiro efetivo. A autorização atual é consultada no banco, novamente após esperar a trava em mutações.

Criação/alteração/exclusão usam trava da Empresa em READ_COMMITTED e timeout de lock5s; transação ambiente incompatível é recusada. Isso serializa a contagem da última conta. Versão divergente retorna409, sem alterar valores; update responde a versão após flush. Busca/paginação/filtros são no servidor, tamanho1–100 e ordem nome/UUID. Percentual na busca é literal, não wildcard.

## DEPENDÊNCIA ISOLADA

BankAccountReferenceGuard é a porta de uso por módulos operacionais. Se qualquer guarda encontra referência, DELETE desativa e preserva a linha, inclusive quando é a última. Sem referência, DELETE recusa a última linha. O módulo #39 deve fornecer guardas reais de FinancialEntry/BankTransfer e FKs compostas; a integração com esses dados ainda não existe nesta entrega. Teste sintético da porta não é afirmação de paridade integral dessa integração. Não consultar AccountingMovement como substituto. Constraint física impede exclusão de referência que já exista mesmo se um adapter estiver ausente, retornando conflito seguro.

Contas bancárias, categorias, parceiros e centros são incrementos separados da #38; esta entrega não a encerra. Relatórios, transferências, baixa, extratos e saldos calculados continuam nos módulos dependentes. Nenhum dado operacional real foi alterado.

## VALIDAÇÃO

TDD de centavos/expoentes/identidade cadastral e de permissões/escopo/versão/última conta. Contratos PostgreSQL/HTTP cobrem CSRF, ausência de bypass administrativo, RESTRICTED, revogação, busca e leitura/escrita entre empresas, versão após flush, exclusão simultânea, rejeição de REPEATABLE_READ e FK composta para referências futuras. PG/Testcontainers executa na CI se Docker local estiver indisponível; compilação local não é execução integrada.
