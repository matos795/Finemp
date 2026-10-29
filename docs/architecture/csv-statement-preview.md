# Prévia CSV de extrato — #40 / #185

## FATO / DECISÃO

A referência identifica o cabeçalho nas primeiras40 linhas e aceita data, valor ou crédito/débito, histórico, documento e direção D/C. A prévia CSV recebe separador explícito entre vírgula, ponto e vírgula, tabulação ou barra vertical; não tenta adivinhar um formato ambíguo. Compartilha a decodificação UTF-8 estrita/fallback Windows-1252 com OFX, sem avaliar fórmulas. Aceita aspas e quebras de linha dentro de campos, preserva a ordem e limita entrada a16 MiB, 50.040 registros lógicos, 200 campos por registro e 4.000 caracteres por campo.

Datas ISO e dd/mm/aa ou dd/mm/aaaa são validadas no calendário. Valor, sinal e D/C seguem os comportamentos observados; com crédito e débito, cada componente deve ser representável em centavos antes da diferença. A prévia mantém os registros inválidos com erros fixos e deixa linhas de saldo visíveis como aviso. FITID não existe no CSV desta etapa: avisa sobre identidade de fallback e marca todas as duplicidades detectadas no arquivo. Valores canônicos usam centavos exatos e não alteram banco ou lançamentos. Memos iniciados por fórmula são tratados somente como texto; valores com sintaxe de fórmula são inválidos.

Para impedir leituras monetárias incorretas, um número de campos diferente do cabeçalho é erro explícito. Um único ponto seguido de três dígitos, como `1.234`, é ambíguo entre milhar e fração subcentavo e fica pendente de decisão, sem interpretação automática. A moeda do arquivo não é presumida. Strings monetárias com fração residual de centavo seguem abertas na #128.

## PONTO ABERTO / próxima integração

Detecção automática de separador, planilhas XLSX, metadados de conta/moeda, comparação com movimentos já persistidos, prévia autorizada e confirmação atômica continuam na #40. A futura importação deve validar Company/conta, fechamento, duplicidades, idempotência e auditoria antes de qualquer efeito. Prévia pura não faz I/O nem confirma movimentos. Ao exportar texto para planilhas, preservar proteção contra interpretação de fórmulas no módulo de exportação correspondente.

## Validação

Sete testes sintéticos cobrem aspas/multilinhas, BOM/Windows-1252, data e sinais, crédito/débito, duplicidades, saldo, números ambíguos/subcentavos, fórmulas em valor e colunas desalinhadas. As regressões OFX e de identidade foram executadas em conjunto.
