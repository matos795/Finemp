# Fechamento de extratos vinculados

## DECISÃO

Alteração de lançamento, baixa/estorno e exclusão individual ou relacionada verificam também o mês de cada movimento de extrato vinculado. A leitura usa Company e os identificadores dos lançamentos afetados, sob o mesmo bloqueio da Company usado no fechamento. A verificação ocorre antes da mutação e preserva autorização, versões, auditoria e atomicidade. Replay idêntico já confirmado continua retornando o recibo original antes de verificar o estado atual.

O vínculo existente permanece protegido mesmo quando a funcionalidade de extratos estiver desabilitada. Não são criados vínculos, alterados saldos ou calculados valores por esta proteção.

## PONTO ABERTO

O comportamento de alteração ou estorno de baixa vinculada em período aberto continua sem decisão. Esta proteção não resolve vínculos órfãos nesse cenário e não habilita operacionalmente os comandos de conciliação da #197. Nenhuma política de desvinculação automática foi introduzida.
