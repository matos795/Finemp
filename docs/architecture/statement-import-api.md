# Confirmação de extrato pela API

## DECISÃO

A prévia continua temporária e devolve o SHA-256 do arquivo no cabeçalho `X-Statement-SHA256`. A confirmação é uma operação separada: o usuário reapresenta o arquivo, informa esse hash, escolhe linhas e confirma conta, moeda em reais e leitura dos alertas. O servidor verifica o hash, reprocessa o arquivo e utiliza apenas movimentos canônicos das linhas selecionadas, nunca valores calculados pelo frontend. O comando interno mantém autorização, fechamento, auditoria, atomicidade e idempotência. A repetição exige corpo idêntico e a mesma chave; o conteúdo do arquivo não é salvo nem registrado em logs.

Como o leitor OFX não associa cada linha à conta de origem, arquivos com múltiplas contas, bancos ou moedas são recusados na confirmação; moeda explicitamente diferente de BRL também é recusada. CSV e OFX sem moeda exigem a confirmação explícita de reais. A conta financeira selecionada não é verificada automaticamente contra identificadores bancários do arquivo. Similaridade sem FITID não impede a importação, mas permanece visível na prévia.

Os limites técnicos são 16 MiB para prévia, 8 MiB de arquivo e 12 MiB de corpo para confirmação, e até 5.000 linhas selecionadas; uma única operação de arquivo por instância evita processamento simultâneo de grandes arquivos. Esses limites não são regras contábeis. A confirmação não faz baixa nem conciliação.

## PONTO ABERTO

Correspondência automática entre conta bancária do arquivo e cadastro operacional e tratamento de FITID reutilizado continuam abertos. Arquivos acima do limite da confirmação precisam de uma solução futura que preserve a atomicidade e os mesmos controles.
