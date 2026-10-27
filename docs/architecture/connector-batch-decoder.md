# Decoder e moeda do lote — Issue #105 / #103

## FATO IMPLEMENTADO

Componente streaming para `observedIdentity` e `movements`, sem endpoint de lote neste incremento. Quantidade de registros é conferida antes de ler/materializar a linha excedente. Dados monetários usam BigDecimal desde o token JSON (número ou string curta); nenhum double, arredondamento ou conversão de sinal.

Root e linhas têm campos explícitos. JSON duplicado, campos desconhecidos, formato incorreto e tokens após o objeto são recusados. Falha de origem só admite READ_FAILED, RESOURCE_LIMIT, IDENTITY_MISMATCH ou INVALID_PAYLOAD, sem mensagem livre, SQL, sucesso ou counters. Labels como 0, N/A e (-) Verificar permanecem texto literal.

Parser é NEVER transacional: não deve prender conexão/trava PostgreSQL enquanto lê. Exceções de JSON são convertidas em erros constantes sem causa, e a localização não inclui o corpo de origem. O orçamento de bytes da #102 continua obrigatório no transporte; este componente também restringe registros e estrutura antes do binding completo.

## DECISÃO TÉCNICA

Registro usa o mesmo limite configurado para publicação, padrão 25.000, com configuração entre 1–100.000. Outros limites de recurso: profundidade 8, número 64 caracteres, string 4.096, nome 64, documento 16MiB e orçamento proporcional de tokens. São proteções técnicas reversíveis, não regras contábeis ou sentinelas de classificação.

NUMERIC(19,4) já é o contrato da persistência contábil. O validador agora checa escala/dígitos antes de expandir BigDecimal. Zero normaliza diretamente; valores não zero removem apenas zeros de representação e precisam caber em 15 dígitos inteiros/4 fracionários antes do setScale exato. Expoentes extremos não causam expansão arbitrária de BigInteger. Não há truncamento nem arredondamento.

## PRÓXIMO PASSO / PONTO ABERTO

A #103 ligará o parser ao recebimento, prova atual de autoridade/contexto, publicação atômica e recibo. Identidade recebida ainda é declarada pelo agente; o adapter deverá ler a identidade real no ERP. Este componente não homologa operação nem habilita acesso produtivo. Nenhum dado real ou migração foi usado.
