# Consulta de lançamentos financeiros — Issue133 /39

## FATO CONFIRMADO

filtrarLanc685–701 usa dataRef = pagamento quando presente, senão vencimento. Pago significa data de pagamento presente, inclusive futura; aberto inclui todos sem pagamento, mesmo vencidos. Vencido é aberto com vencimento anterior à referência de hoje, não incluindo o dia atual.

Categoria inclui suas subcategorias; centro inclui a subárvore com limite de segmento, ou o grupo Sem centro. Fixo/variável efetivo usa override do lançamento antes da categoria/grupo/padrão. Busca é literal em minúsculas, sem remover acentos neste filtro, sobre descrição, rótulo/documento do parceiro, documento, observação e nome da categoria. Valor efetivo usa pago informado ou nominal, calculado pelo backend.

## DECISÃO

GET coleção e item em financial-entries são condicionais a FINEMP_FINANCE_ENTRIES_ENABLED. FINANCIAL_ENTRY_VIEW ou MANAGE explícitos ALL, consultados no banco; papel administrativo ou grant de outra Empresa/Cliente/Contabilidade não substitui permissão. A autorização de leitura não habilita escrita; criação/alteração individuais têm opt-in e contrato adicionais em [comandos](financial-entry-commands.md).

Resposta por item contém identificador/Empresa, values tipado imutável, labels financeiros mínimos, custo e valor efetivos, status e data de referência, versão/timestamps. Valores monetários são strings decimais de duas casas. Rótulos de banco/categoria/parceiro/centro fazem parte do contexto do lançamento sob permissão financeira. Perfil completo do parceiro continua sob permissão própria; não retornar CPF/CNPJ, e-mail, telefone/endereço ou IDs dos atores de auditoria nesta consulta. Busca por documento do parceiro permanece restrita aos lançamentos da Empresa autorizada.

Projeção de valores/record embutido e rótulos na mesma instrução SQL, sem selecionar entidades gerenciadas. Joins possuem company_id e não reutilizam estado de entidades do contexto JPA. Classificação de custo é resolvida na consulta com os mesmos fallbacks homologados. Filtros, páginas e contagem ocorrem no servidor; itens não são agregados pelo consumidor.

Página1–100 (padrão50), busca até100 caracteres, ordem estável por dataRef/UUID. from/to são inclusivos, precisam estar em ordem e usam pagamento/vencimento conforme referência. centerId identifica raiz da mesma Empresa; sem correspondência retorna404 e nunca vira filtro irrestrito. withoutCenter não pode ser combinado com raiz. Filtros de banco/parceiro/categoria permanecem tenant-scoped e não ampliam autoridade.

asOfDate explícito permite reproduzir classificação de vencidos. Ausente, usa calendário configurável FINEMP_FINANCE_CALENDAR_ZONE (padrão America/Sao_Paulo). É escolha técnica reversível de calendário; não muda datas armazenadas nem cria Clock global que conflite com outros módulos. Datas ISO limitadas a anos1–9999. Consulta tem orçamento SQL5s; indisponibilidade retorna mensagem fixa sem detalhes SQL. V29 acrescenta índice company/dataRef/id sem reescrever migrations anteriores.

## PENDÊNCIAS ISOLADAS

Anexos e seus filtros dependem da51. Criação/alteração individual e recibos pertencem aos incrementos135/136; baixa/estorno/exclusão/transferências/séries e normalização monetária128 continuam na39. Essas operações não ficam implicitamente prontas por existir GET. Este módulo não decide distribuição decimal versus efeitos binários legados, nem consulta Excel/Questor para renderizar uma página.

## VALIDAÇÃO

TDD de permissão, ownership, limites e raiz desconhecida. PostgreSQL/HTTP cobre dois tenants completos, grant atual/revogação/RESTRICTED, datas de pagamento versus vencimento, status/asOf, grupos/subárvores/custo, busca literal/acentos, precisão, rótulos mínimos, isolamento e escrita negada. Regressão lê com uma entidade antiga já gerenciada para verificar projeção atual. Dados sintéticos e nenhuma conexão operacional.
