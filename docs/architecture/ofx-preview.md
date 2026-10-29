# Prévia OFX — #40 / #183

## FATO / DECISÃO

O HANDOFF 4.8 descreve leitura OFX/QFX com transações SGML/XML, UTF-8 e fallback Windows-1252, data DTPOSTED, valor TRNAMT, FITID, nome/memo, documento e saldo. A prévia implementa um parser de texto limitado a16 MiB e50 mil transações. Respeita declarações de codificação do cabeçalho ou XML, sem interpretar textos de transações como charset. Não usa um parser XML que resolva entidades, não acessa arquivo, rede ou banco por conta própria e não persiste dados.

Cada transação tem posição e valor/data originais para apresentação. Data válida e valor em centavos exatos produzem StatementValues com sinal, memo, FITID e documento. Linhas inválidas, zeradas, incompletas, fora do limite ou com fração residual de centavo retornam erro explícito; não são arredondadas nem descartadas silenciosamente. O saldo informado é opcional e só é apresentado se válido e exato; valores monetários são representados como texto no contrato serializado. Os textos de diagnóstico e toString não expõem dados do arquivo.

O parser avisa sobre ausência de FITID e assinala todas as linhas de mesmo identificador dentro do arquivo, mesmo quando uma linha tem data ou valor inválido. XML aceita somente referências predefinidas/numericamente válidas; entidades desconhecidas geram erro e nunca são resolvidas externamente. Transações XML sem fechamento explícito permanecem como erro; SGML com cabeçalho reconhecido preserva a forma legada que omite esse fechamento. A prévia mantém todas as linhas, inclusive duplicidades potenciais. Banco/conta/moeda informados e saldo são dados privados que uma futura API só deve retornar após autorização apropriada; não devem ser registrados em logs. Valores com mais de255 caracteres em metadados de banco/conta/moeda geram aviso sem devolução do valor excessivo.

## PONTO ABERTO / próxima integração

OFX com múltiplos bancos ou contas é sinalizado; as linhas ainda não são associadas individualmente a cada conta, portanto a confirmação futura deve impedir mistura até que essa associação e a titularidade sejam verificadas. Moeda ausente, diferente de BRL ou conflitante também é sinalizada: não há conversão nem regra cambial presumida. O número da conta e banco informado no arquivo ainda não é comparado com a conta escolhida.

Comparação de duplicidades já persistidas, vinculação ao lote, upload com limites/autorização antes do corpo, armazenamento/expiração da prévia e confirmação atômica continuam em #40. CSV/XLSX, conciliação e auditoria não são implementados aqui. Não criar movimentos nem saldo operacional a partir desta prévia. A política de entrada subcentavo permanece #128.

## Validação

Onze testes sintéticos cobrem SGML/XML, UTF-8/Windows-1252, sinal, data/documento/saldo, erros visíveis, subcentavo, duplicidade inclusive em linhas inválidas, referências XML sem resolução externa, limites, múltiplas contas e moeda não confirmada. Não usam conexão ou dados bancários reais.
