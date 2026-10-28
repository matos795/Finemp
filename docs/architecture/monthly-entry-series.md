# Recorrência mensal canônica

Referências: #39, #155 e #160. Comando interno antes da API e da exclusão coordenada de séries.

## FATO CONFIRMADO

O calendário já integrado preserva documento/competência/vencimento desde suas origens, data de lançamento fixa e pagamento apenas na primeira ocorrência. MONTHLY repete o valor, sem distribuí-lo nem acrescentar sufixo à descrição. A quantidade é de 2 a 120. Parcelamento e seus efeitos binários continuam separados na #128.

## DECISÃO técnica

O comando aceita o contrato canônico positivo em centavos já existente. Valida calendário inteiro e referências atuais, com FINANCIAL_ENTRY_MANAGE ALL antes/depois do bloqueio da Company, READ COMMITTED e timeout. Grupo, lançamentos, posições originais, eventos e recibo ordenado pertencem a uma transação; falha no final desfaz o conjunto inteiro.

Um registro comum de chaves no PostgreSQL impede colisão entre recibos individuais e de criação de série. As chaves anteriores são migradas preservando fingerprints/recibos. Os dois tipos de recibo têm FK com o tipo da chave; nenhum UUID de comando pode pertencer aos dois. Reenvio exige permissão atual e recupera os identificadores/versões originais antes de revalidar cadastros alterados. O recibo não afirma estado atual dos lançamentos.

A auditoria amplia o diário financeiro oficial: vários eventos CREATED sob a mesma chave, um por lançamento. Não há sistema paralelo de audit trail. Evento/recibo não têm FK para a linha operacional que apague o histórico após exclusão. O recibo de conjunto armazena uma lista imutável e limitada de resultados; quantidade e cardinalidade são verificadas também no banco.

## PRÓXIMO PASSO / PONTO ABERTO

Integrar exclusão coordenada conforme a referência e só então expor a captura mensal. O gate RESTRICT de membros permanece até essa integração. Não há scheduler, geração INSTALLMENTS ou definição de percentuais/normalização bruta. Depois da #39: tipos explícitos de Centro de Resultado (#157), fechamento por Company (#158), retenções fundamentais (#159), #40 e #41, conforme a ordem homologada em28/10.
