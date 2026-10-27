# Identidade Questor e snapshot verificado — Issue #108

## FATO IMPLEMENTADO

Adapter lê código/CNPJ efetivamente retornados pelo banco. Não usa o CNPJ esperado como resultado de leitura. Mapeamento local de tabela/colunas é obrigatório e conferido em metadados JDBC; não há nomes fictícios de schema como defaults. Identificadores são restritos/quotados e código da empresa é parâmetro, nunca SQL vindo do browser ou do trabalho.

Tabela precisa ser identificável sem ambiguidade, coluna de código precisa ser integral e CNPJ textual limitado. Resultado precisa ter exatamente uma linha. CNPJ é derivado do texto retornado, preservando zeros iniciais e normalizando somente pontuação/espaços convencionais conforme o contrato numérico atual. Ausência/ambiguidade/mismatch impede movimentos; configuração não comprovada falha fechado.

Na comparação de referências Questor, códigos inteiros positivos equivalentes (por exemplo, 0057 e 57) identificam o mesmo código consultado pelo adapter. A identidade observada continua contendo o valor retornado pelo banco, sem substituir pelo esperado; CNPJ continua obrigatório. Essa equivalência não é aplicada a outras origens.

`VerifiedQuestorSnapshotReader` obtém identidade e movimentos na MESMA conexão/transação solicitada como somente leitura, REPEATABLE_READ. Compara identidade antes da consulta de movimentos. O SQL Razão sem CC, parâmetros de empresa/período e formato canônico foram preservados. Sessão pertence ao adapter, termina em rollback/close e não expõe caminho de commit. Limites de linhas e timeout de statement continuam explícitos; exceções descartam causas/suppressed do driver que poderiam conter detalhes privados.

Com identidade habilitada, há um único reader canônico verificado e também a porta de snapshot reutilizável pelo agente. O executor direto revalida identidade antes do I/O e trata divergência como IDENTITY_MISMATCH; mapeamento não comprovado é SOURCE_NOT_AVAILABLE, sem retry como se fosse erro de rede. Snapshot volta a conferir na mesma transação da extração, evitando janela entre prova e movimentos.

## DECISÃO TÉCNICA

`FINEMP_QUESTOR_IDENTITY_ENABLED` é false por padrão. Habilitar exige os identificadores exatos em FINEMP_QUESTOR_IDENTITY_TABLE, FINEMP_QUESTOR_IDENTITY_CODE_COLUMN e FINEMP_QUESTOR_IDENTITY_CNPJ_COLUMN, obtidos de metadados locais. Não há campo de SQL/schema na API de trabalho. Sem mapeamento, o reader de identidade não é inventado e o executor controlado continua dependendo de uma prova configurada.

Timeout padrão de statement 30s, configurável entre 1–120s. Metadados, conexão e driver precisam de seus próprios limites de rede na implantação. O código não promete matar uma chamada nativa bloqueada. Nomes de objetos são case-sensitive conforme o metadado e precisam ser únicos; schemas qualificados/mapeamentos ambíguos não são adivinhados.

Tipos suportados para código: INTEGER/SMALLINT/BIGINT ou NUMERIC/DECIMAL de escala zero; CNPJ: CHAR/VARCHAR/NCHAR/NVARCHAR, tamanho conhecido até 255. Formatos não suportados pedem verificação técnica do schema, sem conversão numérica/padding presumidos. São restrições do adapter atual, não uma regra contábil universal.

## PONTO ABERTO / HOMOLOGAÇÃO OPERACIONAL

Testes sintéticos de mocks e de contrato JDBC em PostgreSQL demonstram comportamento/isolamento/recursos; NÃO equivalem à homologação do Firebird Questor real. Identificadores reais, versão/capacidades do Jaybird/Firebird, permissões somente leitura, deadlines e conexão do ambiente de destino ainda precisam de evidência operacional. Nenhuma tabela/coluna de CNPJ específica é declarada confirmada por este código.

Agente outbound HTTPS e custódia de seus segredos seguem em incremento separado. Nenhuma credencial foi registrada e nenhuma alteração foi executada em banco operacional. Questor permanece sistema contábil somente leitura para o FINEMP; as regras gerenciais abertas continuam abertas.
