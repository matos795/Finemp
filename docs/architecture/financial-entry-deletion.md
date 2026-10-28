# Exclusão de lançamento financeiro individual

Referências: #39 e #151. Comando interno; não expõe DELETE HTTP neste incremento.

## FATO CONFIRMADO

pedirExcluir820–828 do FINEMP_web_1 permite excluir o alvo mesmo se pago. Havendo série, inclui parcelas seguintes ainda abertas com vencimento estritamente maior. Compras com itens validam o estoque. excluirSelecionados829 é outra operação, com seleção explícita e validação agregada. Essas diferenças não foram transformadas em uma operação única presumida.

## DECISÃO técnica

Este comando atende os lançamentos individuais já persistidos, que ainda não possuem metadados de séries, itens ou origens dos módulos posteriores. Não afirma cobrir série/lote/estoque. A integração dessas origens deverá ser acrescentada antes de disponibilizá-las; suas dependências não podem ser silenciosamente ignoradas.

FINANCIAL_ENTRY_MANAGE ALL vigente é exigido antes e depois do bloqueio por Empresa. READ COMMITTED, espera de bloqueio limitada e comparação entre versão esperada, versão atual sob FOR UPDATE e versão JPA seguem os comandos existentes. Ausência/UUID de outra Empresa é not-found sob Empresa autorizada; outra Empresa sem permissão é negada. Não revalida a classificação atual nem inventa veto geral a título pago.

A chave UUID compartilha o namespace de comandos do lançamento com criar, alterar, baixar e estornar. O fingerprint de exclusão possui esquema próprio versionado, com contexto, ação, alvo e versão; hashes integrados não mudam. Reenvio exige permissão atual e devolve recibo original mesmo após excluir. Mesmo UUID com outra ação/conteúdo conflita.

Exclusão, evento e recibo são uma transação. O evento mantém o estado/versão anteriores; estado e versão posteriores são ausentes porque o recurso foi removido. O recibo DELETED informa a última versão persistida. Migrations novas permitem essa transição e continuam exigindo estado/versão posteriores nos eventos das ações anteriores. O diário não tem FK para o lançamento: a exclusão não apaga o histórico. Consulta/retenção da auditoria permanece na #51; nenhuma garantia de imutabilidade contra administrador é presumida.

## PRÓXIMO PASSO / PONTO ABERTO

Exposição HTTP, metadados/remoção de séries e exclusão em lote terão incrementos próprios. Estoque e origens vinculadas serão integrados nos módulos dependentes; a referência de exclusão deve ser preservada. A regra monetária bruta/parcelas continua aberta na #128 e não é decidida por este comando.
