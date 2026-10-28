# Identificação de séries financeiras

Referências: #39 e #155. Fundação de persistência, antes da geração operacional de recorrências.

## FATO CONFIRMADO

salvarLanc775–818 atribui UUID compartilhado de série e posição original i/n às ocorrências. A quantidade vai de 2 a 120, e a descrição permanece a mesma. Recorrência mensal repete o valor; parcelamento divide o valor por regra monetária ainda aberta na #128. O calendário já confirmado está em [calendário de séries](entry-series-calendar.md): origem não cumulativa, último dia do mês, documento/competência e pagamento somente na primeira ocorrência.

pedirExcluir820–828 usa a série e os vencimentos atuais para incluir parcelas seguintes ainda abertas com vencimento estritamente maior que o alvo. Não renumera as posições restantes. Exclusão em lote e itens de estoque possuem comportamentos próprios.

## DECISÃO técnica — metadados originais e isolados

FinancialEntrySeries guarda Empresa, modo de criação MONTHLY/INSTALLMENTS, quantidade original, ator/data e versão. EntrySeriesMembership associa o lançamento existente à série e conserva posição/quantidade originais. Não representa SINGLE como grupo artificial. Ambas as referências pertencem à mesma Empresa; FKs compostas também impõem essa garantia no PostgreSQL, incluindo a quantidade do grupo. Cada lançamento tem no máximo um vínculo, e uma posição é única na série.

O grupo não determina valor, vencimento atual ou situação de pagamento. Esses dados pertencem aos lançamentos. Posições não são renumeradas quando um vínculo é removido; quantidade original não é quantidade restante. Preservar o modo de criação é metadado técnico, não promessa de executar automaticamente recorrências futuras.

As FKs de membro para grupo e lançamento usam RESTRICT, sem cascade automático. Até integrar a exclusão coordenada homologada, um comando individual anterior não pode apagar silenciosamente parte de uma série. Esse gate é temporário e técnico, não regra definitiva de negócio. Não há criação operacional de séries ou nova API neste incremento. O repositório interno não substitui autorização, idempotência ou auditoria dos comandos seguintes.

## PRÓXIMO PASSO / PONTO ABERTO

Entregar comando mensal atômico com recibo do conjunto, autorização atual e auditoria; integrar exclusão coordenada antes de expor a captura. Entrada canônica positiva em centavos pode ser repetida mensalmente sem decidir normalização bruta. INSTALLMENTS continua sem comando de geração até resolver a política da #128. Não introduzir scheduler ou cadastro automático futuro sem evidência funcional.

Testes sintéticos cobrem posição/quantidade, modo, referências, unicidade, isolamento de duas Contabilidades/Clientes/Empresas, versão e bloqueio de exclusão parcial pelos serviços existentes. A fundação não afirma fechar a #39.
