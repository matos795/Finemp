# Permissões para mutações contábeis

## DECISÃO técnica

Presença de sessão ou grant genérico não autoriza alteração de classificação nem importação contábil:

- `PUT /api/companies/{companyId}/accounting-accounts/{accountId}/classification` exige `ACCOUNT_CLASSIFICATION_MANAGE` na empresa;
- `POST /api/accounting-sources/{accountingSourceId}/questor-import` exige `QUESTOR_SYNC` na empresa proprietária da fonte;
- a empresa da fonte é consultada pelo backend, não recebida como prova do consumidor;
- fonte desconhecida ou externa ao grant retorna 403, sem chamar importação/leitor;
- escopo `RESTRICTED` continua bloqueado enquanto não há segmentação efetiva;
- CSRF permanece obrigatório e permissões administrativas de contabilidade/plataforma não substituem grant financeiro explícito.

O leitor Questor continua somente leitura. O serviço interno de importação permanece utilizável por fluxos internos autorizados; esta alteração protege a entrada HTTP antes de qualquer consulta à origem ou gravação de movimentos.

## Verificação

Testes unitários de resolução de fonte e negação antecipada. Testes HTTP com PostgreSQL real para 401/403, perfil somente leitura, empresa externa, fonte inexistente, permissões explícitas positivas e CSRF. Importação e leitor são doubles: nenhum Questor real ou segredo é necessário para esses testes.

## Limites

Não cria operação de manutenção do catálogo DRE (o catálogo existente é somente GET), não redefine classificações nem altera cálculos. Sincronização assíncrona/Connector e auditoria abrangente continuam em #50/#51.
