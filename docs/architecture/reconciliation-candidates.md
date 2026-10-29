# Candidatos de conciliação — #40 / #179

## FATO CONFIRMADO

O comportamento funcional observado no HANDOFF (4.9) seleciona lançamentos ainda sem vínculo pelo sinal do extrato e diferença de valor inferior a0,011. Pagos usam valor efetivo, mesma conta e até3 dias de distância da data de pagamento. Em aberto usam nominal, vencimento até15 dias e não exigem mesma conta. Ordenação prioriza pagos, depois menor distância; empates preservam ordem de entrada.

## DECISÃO técnica

O cálculo puro recebe um snapshot já autorizado e restrito à Company, valores canônicos de centavos exatos e datas. Compara com BigDecimal sem arredondamento; entradas repetidas por UUID são recusadas, e resultados são imutáveis. O conjunto de IDs já vinculados é fornecido pelo chamador. Não consulta banco, cria vínculos, altera lançamentos, fecha períodos ou decide importação. Uma futura consulta deve obter o conjunto atual por Company, sob autorização, e a conciliação efetiva deve verificar a unicidade e os períodos sob trava, com auditoria e recibo.

## PONTO ABERTO

O frontend de referência usa números binários JavaScript. Para valores monetários extremos, sua aritmética pode divergir da comparação decimal exata; essa diferença não autoriza arredondar silenciosamente ou escolher política da #128. A deduplicação observada usa FITID por conta, ou data/valor/memo normalizado quando ausente. Na futura importação, a prévia deve tornar visíveis possíveis duplicidades, sem descartar silenciosamente fatos financeiros antes da confirmação. O parsing OFX/planilha, a prévia e confirmação atômica, a deduplicação efetiva, a proteção por Company e a conciliação continuam em #40.

## Validação

Testes sintéticos cobrem diferença de um/dois centavos, sinais, conta, limites de3/15 dias, valor efetivo, vínculo anterior, ordenação e entradas inválidas. Nenhuma baixa parcial, taxa de cartão, antecipação ou regra de bruto/líquido foi adicionada.
