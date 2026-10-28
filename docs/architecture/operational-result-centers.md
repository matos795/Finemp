# Centros de resultado operacionais — Issue122 /38

## FATO CONFIRMADO

Consulta pontual às funções1969–2003 da referência já inventariada: o plano de centros pertence à Empresa. Códigos numéricos hierárquicos são únicos nesse plano, o pai exato deve existir e nomes têm pelo menos dois caracteres. O formulário limita código a20 e nome a80. Cadastro remove espaços do código, preserva zeros e cria ativo. Código não é editado; nome/ativo podem mudar.

Adicionar filho a um pai com lançamentos é recusado. Tipo grupo/analítico deriva da existência de filhos; subárvore considera o código exato e o prefixo seguido de ponto. Excluir centro usado por lançamento ou contrato o inativa antes de considerar filhos. Sem uso, grupo com filhos não pode ser excluído; folha pode, sem quantidade mínima. Não há veto observado ao pai inativo.

Exigência de centro aplica-se a lançamentos novos; histórico sem centro continua como tal. Plano sugerido é ação explícita de plano vazio, com sete centros observados na referência. Não é carga automática por Empresa.

## DECISÃO

ResultCenter operacional é separado de CostCenter contábil e das classificações do PlanoQ. UUID e parent_id são internos; código imutável define o pai, impedindo ciclos pela API. FK composta protege parent/company e referências futuras; unicidade company/code. Campo técnico sort_key usa segmentos numéricos exatos, desempate textual para zeros e collation C, preservando branches sem depender de ordenação lexicográfica simples. Não é promessa de collation idêntica à locale do navegador.

RESULT_CENTER_VIEW ou MANAGE ALL permite leitura; escrita exige MANAGE explícito. Autorização atual é verificada novamente após a trava de Empresa. Nenhum acesso é inferido de papel administrativo ou de grant de outra Empresa/Cliente/Contabilidade. Código do pai é procurado apenas na Empresa autorizada. Paginação1–100 (padrão50), busca literal sem acentos e filtro rootId de subárvore no servidor. hasChildren é consultado em lote para a página, independentemente dos filtros de ativos/busca.

Mutações READ_COMMITTED, lock de Empresa e timeout5s serializam criação/exclusão e plano sugerido. Transação ambiente incompatível é recusada. Versão inicial zero; atualização compara expectedVersion e responde após flush. Settings não escritos têm requireResultCenter=false/version=null; primeiro PUT exige expectedVersion omitido/null, depois a versão persistida exata. Essa configuração não altera centros nem lança/reclassifica fatos financeiros. Plano sugerido inteiro é transacional e recusa repetição em plano não vazio.

Feature FINEMP_FINANCE_CATALOG_ENABLED permanece desligada por padrão. Não há cálculo financeiro neste módulo nem dados reais/secrets. Erros fixos preservam privacidade e não expõem diagnósticos SQL.

## DEPENDÊNCIAS ISOLADAS

ResultCenterUsageGuard separa hasEntries (veto a transformar pai em grupo) de hasReferences (histórico de lançamento/contrato para exclusão). #39 e #42 devem fornecer adapters reais e FKs operacionais. Ausência desses módulos não é comprovante de integração; testes de porta usam referências sintéticas. Enforce de centro obrigatório/analítico/ativo nas respectivas escritas será implementado junto delas, não por consulta de AccountingMovement ou reclassificação do legado.

Categorias financeiras ainda são outro incremento da #38. Relatórios por subárvore pertencem à #41; este endpoint fornece o catálogo, sem antecipar cálculos.

## VALIDAÇÃO

TDD de código/ordenação (RED → GREEN) e serviço (cinco RED → GREEN), testes de acesso, pai, uso, versões e exclusão. Contratos PostgreSQL/HTTP cobrem hierarquia/ordem/páginas/subárvore, tenants, permissões/CSRF/revogação, settings, plano sugerido, referência física e corrida entre criação de filho/exclusão do pai. Docker local indisponível: executar PostgreSQL na CI; compilação não conta como execução integrada.
