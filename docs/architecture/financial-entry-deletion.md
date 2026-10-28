# Exclusão de lançamento financeiro individual

Referências: #39, #151, #153 e #162. O comando e o contrato HTTP preservam o alvo individual e incluem as ocorrências relacionadas homologadas.

## FATO CONFIRMADO

pedirExcluir820–828 do FINEMP_web_1 permite excluir o alvo mesmo se pago. Havendo série, inclui parcelas seguintes ainda abertas com vencimento estritamente maior. Compras com itens validam o estoque. excluirSelecionados829 é outra operação, com seleção explícita e validação agregada. Essas diferenças não foram transformadas em uma operação única presumida.

## DECISÃO técnica

O alvo é sempre removido, pago ou aberto. Havendo série, o conjunto também inclui as outras ocorrências ainda sem pagamento cujo vencimento atual é estritamente maior que o do alvo. Vencimento igual/anterior, pagas futuras e outras séries/Companies são preservadas. A posição original não decide a seleção e não é renumerada. Lotes e estoque continuam em contratos próprios; não são origens aceitas silenciosamente por esta captura.

FINANCIAL_ENTRY_MANAGE ALL vigente é exigido antes e depois do bloqueio por Empresa. READ COMMITTED, espera de bloqueio limitada e comparação entre versão esperada, versão atual sob FOR UPDATE e versão JPA seguem os comandos existentes. Ausência/UUID de outra Empresa é not-found sob Empresa autorizada; outra Empresa sem permissão é negada. Não revalida a classificação atual nem inventa veto geral a título pago.

A chave UUID compartilha o namespace de comandos do lançamento com criar, alterar, baixar e estornar. O fingerprint de exclusão possui esquema próprio versionado, com contexto, ação, alvo e versão; hashes integrados não mudam. Reenvio exige permissão atual e devolve recibo original mesmo após excluir. Mesmo UUID com outra ação/conteúdo conflita.

O PostgreSQL seleciona e bloqueia o conjunto atual por UUID; versões atuais e JPA de todos os afetados devem coincidir. Vínculos são removidos com versão esperada antes dos registros, na mesma transação de eventos e recibo. Cada evento mantém estado/versão anteriores; posteriores são ausentes. O grupo original permanece como metadado histórico. O recibo DELETED mantém os campos da raiz e acrescenta deletedEntries imutável: raiz primeiro, demais por posição original. Recibos legados sem a lista são lidos como exclusão somente da raiz. Hashes anteriores não mudam.

O diário financeiro oficial é ampliado, sem audit trail paralelo. Falha no recibo restaura todos os vínculos, lançamentos, eventos e chave. Migrations continuam exigindo estado/versão posteriores nos eventos das outras ações. O diário não tem FK para o lançamento: a exclusão não apaga histórico. Consulta/retenção permanece na #51; nenhuma garantia de imutabilidade contra administrador é presumida.

## DECISÃO técnica — contrato HTTP individual

DELETE no item financeiro recebe expectedVersion da raiz pela query e Idempotency-Key UUID pelo header, sem corpo. Exige os dois opt-ins existentes de lançamentos, sessão/CSRF e FINANCIAL_ENTRY_MANAGE ALL vigente antes do serviço. Responde200 com recibo DELETED e conjunto efetivamente removido; GET posterior é404. Ausência/invalidez de versão/header é400 com código fixo. Nenhum estado privado de auditoria ou ator é exposto.

O mesmo gerenciador de autorização limpa a marca de corpo para DELETE, mantendo a proteção de POST/PUT/baixa/estorno. Não lê nem exige JSON para excluir; uma URL codificada segue a mesma autorização normalizada. Reenvio mantém a chave/versão originais, inclusive após exclusão. A resposta é recibo de comando e não recurso atual.

FormContentFilter padrão do Spring Boot foi desabilitado explicitamente nesta API JSON (spring.mvc.formcontent.filter.enabled=false). Esse filtro lê formulários de PUT/PATCH/DELETE antes do gate financeiro e poderia transformar expectedVersion do corpo em parâmetro de exclusão. Nenhuma rota implementada depende desse parsing. Os contratos PostgreSQL verificam sua ausência, rejeitam versão apenas no formulário e preservam o registro/auditoria; versão na query continua prevalecendo sobre texto do corpo. Essa configuração deve permanecer desabilitada.

As duas rotas de exclusão também obtêm a versão diretamente de getQueryString, sem acessar o mapa de parâmetros do servlet. O leitor compartilhado limita a query e exige uma única expectedVersion decimal não negativa, sem duplicatas ou ambiguidade de codificação. Isso mantém a origem exclusivamente na URL mesmo se um container ou harness de teste já tiver preenchido parâmetros a partir do corpo. Os testes usam queryParam para expressar a URL real, em vez de injetar parâmetros genéricos.

## PRÓXIMO PASSO / PONTO ABERTO

Exclusão em lote e estoque terão incrementos próprios. O fechamento #158 deverá verificar os períodos efetivamente afetados de todo o conjunto, não somente da raiz. A regra monetária bruta/parcelas continua aberta na #128 e não é decidida por este comando.
