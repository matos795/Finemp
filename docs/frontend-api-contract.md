# Contrato de integração frontend ↔ API FINEMP

> Checkpoint 26/10/2026: o comportamento funcional do `FINEMP_web_1.zip` foi homologado. A [matriz integral de paridade](reference/frontend-backend-parity.md) registra os requisitos e o estado real de implementação. Contratos planejados continuam indisponíveis até a respectiva entrega; a DRE operacional será exposta separadamente da DRE contábil existente.

> Contrato headless inicial. A API é a autoridade de segurança, persistência e cálculos financeiros. O frontend Claude é apenas um consumidor substituível.

## 1. Status do contrato

- **Implementado:** operação disponível no backend atual e incluída no OpenAPI versionado.
- **Planejado:** direção de contrato para o roadmap; não deve ser chamada por consumidores até sua implementação.
- **Ponto aberto:** depende de regra de negócio ou decisão ainda não homologada.

O arquivo [`openapi/finemp-api-v1.yaml`](openapi/finemp-api-v1.yaml) descreve somente operações implementadas. Este documento registra também a evolução planejada para evitar acoplamento do frontend a detalhes transitórios.

## 2. Princípios obrigatórios

1. Toda regra financeira oficial e todo dado compartilhado de negócio pertencem ao backend.
2. O frontend não consulta Questor, Firebird, Excel, Power Query ou bancos externos.
3. Toda leitura de tela usa dados previamente sincronizados no PostgreSQL FINEMP.
4. `companyId`, `clientId` e demais identificadores não concedem acesso por si mesmos; o backend aplica grant, permission e data scope.
5. O consumidor não reproduz fórmulas oficiais para “corrigir” respostas da API.
6. Dinheiro e agregações financeiras são calculados em `BigDecimal` no backend.
7. Exemplos deste documento são fictícios e não contêm dados de clientes.

## 3. Transporte, sessão e CSRF

### 3.1 Base e conteúdo

- prefixo atual: `/api`;
- conteúdo JSON: `application/json`;
- datas: ISO 8601 (`YYYY-MM-DD`);
- identificadores: UUID;
- autenticação: sessão em cookie `HttpOnly`;
- o cliente deve enviar cookies nas requisições (`credentials: "include"`).

### 3.2 Fluxo de autenticação implementado

1. `GET /api/auth/csrf` obtém `headerName` e `token`.
2. `POST /api/auth/login` envia e-mail e senha e inclui o cabeçalho CSRF retornado.
3. `GET /api/auth/me` obtém o usuário, empresas, roles, permissions e escopo.
4. Operações mutáveis incluem o token CSRF vigente.
5. `POST /api/auth/logout` encerra e invalida a sessão.

Senha nunca é salva em `localStorage`, log, URL ou payload de telemetria.

Com entrega de e-mail configurada, a API também oferece `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/complete` e `POST /api/auth/activate`. Essas operações exigem CSRF mesmo sem login. A solicitação retorna 202 vazio para e-mail conhecido, desconhecido, inativo ou limitado; nunca retorna o token. O frontend lê o segredo do fragmento do link, remove-o do histórico e envia `token`/`newPassword` no corpo da conclusão. Sucesso 204 não inicia sessão: obter novo CSRF e fazer login. Link inválido/consumido/expirado recebe 400; excesso de tentativas de conclusão recebe 429. Sem entrega habilitada, as rotas não existem (404). Detalhes em [identidade HTTP](architecture/identity-http.md).

### 3.3 Autorização

`GET /api/auth/access-catalog` publica [metadados e presets view/edit](architecture/access-catalog.md)
versionados das operações disponíveis. Não são grants do usuário nem autorização.
O consumidor aplica sugestões somente ao PUT explícito de um grant para a
empresa selecionada; presets indisponíveis nunca autorizam operação.

A [administração de grants por empresa](architecture/company-access-administration.md)
usa `/api/admin/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/users`
(GET paginado) e `/{userId}` (GET/PUT/DELETE). PUT recebe `role`,
`costCenterScope` e `permissions`; role não concede capabilities implicitamente.
O destinatário deve ser membro da contabilidade. RESTRICTED é rejeitado com 409
até haver filtro real; DELETE preserva identidade e todos os outros vínculos.

As [leituras contábeis](architecture/accounting-read-authorization.md) exigem
`ACCOUNTING_MOVEMENT_VIEW` para o razão e `ACCOUNT_CLASSIFICATION_VIEW` ou
`ACCOUNT_CLASSIFICATION_MANAGE` para classificações. `DRE_VIEW` não autoriza
o razão completo. Grants novos vazios e rótulo ADMIN não autorizam leitura.
A V15 explicita somente capacidades já existentes nos grants legados.

A [administração de membros da contabilidade](architecture/firm-access-administration.md)
usa `/api/admin/accounting-firms/{firmId}/users` (GET paginado) e `/{userId}`
(GET/PUT/DELETE). PUT recebe `permissions` administrativas; DELETE retorna 409
se ainda houver acessos explícitos às empresas dessa contabilidade. Não há
cascata financeira nem alteração da conta global. Exige `USER_ACCESS_MANAGE`
na firma ou `PLATFORM_USER_MANAGE` global, além de CSRF nas mutações.

A [administração global de identidades](architecture/platform-user-administration.md) usa `/api/admin/users` para busca/detalhe, PATCH de status e PUT de permissões de plataforma. Exige `PLATFORM_USER_MANAGE`, CSRF nas mutações e não concede grants financeiros. A remoção do último administrador ativo e a ativação de marcador de convite são rejeitadas com 409.

Alterar classificação exige `ACCOUNT_CLASSIFICATION_MANAGE` na empresa; importar Questor exige `QUESTOR_SYNC` na empresa da fonte, resolvida pelo servidor. Sessão, ID recebido ou grant genérico não autorizam essas escritas. Escopo `RESTRICTED` não pode receber dados integrais enquanto não houver filtro efetivo no backend. Ver [autorizações contábeis](architecture/accounting-write-authorization.md).

`GET /api/auth/organization-access` retorna somente permissões de plataforma e vínculos/permissões administrativas do usuário da sessão. Não recebe `userId` como seletor, não lista organizações externas e não concede acesso financeiro. O contrato existente de empresas em `/api/auth/me` permanece separado e inalterado. O campo `active` da contabilidade é metadado; não representa um grant financeiro.

Convites administrativos usam `POST /api/admin/accounting-firms/{firmId}/invitations`, com entrega habilitada, sessão, CSRF e `USER_ACCESS_MANAGE` explícita nessa contabilidade. Corpo: `name`, `email`, `permissions` administrativas. Operação aditiva; resposta 204 vazia e sem segredo. Não concede grant de empresa nem acesso financeiro. Ver [convites por contabilidade](architecture/firm-invitations.md).

O frontend pode ocultar ações indisponíveis para melhorar a experiência, mas isso não substitui a autorização do backend.

Fluxo aplicado pelo servidor:

```text
usuário autenticado
→ grant para a empresa/organização
→ permission da operação
→ data scope (incluindo centro de custo quando aplicável)
```

Permissões existentes:

- `ACCOUNTING_MOVEMENT_VIEW`
- `ACCOUNT_CLASSIFICATION_VIEW`
- `DASHBOARD_VIEW`
- `DRE_VIEW`
- `BALANCE_SHEET_VIEW`
- `CASH_FLOW_VIEW`
- `INDICATORS_VIEW`
- `ACCOUNT_CLASSIFICATION_MANAGE`
- `MANUAL_ENTRY_MANAGE`
- `QUESTOR_SYNC`

## 4. Operações implementadas

A [API de controle SyncRun](architecture/sync-control-api.md) oferece solicitação,
consulta e cancelamento de intenção por fonte, com histórico transacional.
POST exige `Idempotency-Key` UUID e retorna 202; não significa sincronização
concluída. Rotas são condicionadas a `FINEMP_SYNC_CONTROL_ENABLED`, desabilitado
por padrão até executor/publicação prontos. O frontend consulta status e não
executa o ERP durante leituras de tela.

A [administração SaaS](architecture/saas-administration.md) disponibiliza cadastro/consulta paginada de Contabilidades, Clientes, Empresas e Fontes, além de associação explícita de empresa legada. Essas operações exigem permissões administrativas nos escopos corretos e não concedem acesso financeiro automaticamente. Os DTOs de fonte não incluem configuração de conexão nem secrets.

### 4.1 Autenticação

#### `GET /api/auth/csrf`

Resposta `200`:

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "valor-opaco"
}
```

#### `POST /api/auth/login`

```json
{
  "email": "usuario@exemplo.invalid",
  "password": "segredo-fornecido-pelo-usuario"
}
```

Resposta: `204`. Credenciais inválidas: `401`.

#### `GET /api/auth/me`

Resposta `200`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "name": "Usuário de exemplo",
  "email": "usuario@exemplo.invalid",
  "companies": [
    {
      "id": "00000000-0000-0000-0000-000000000101",
      "name": "Empresa Demonstração",
      "role": "ACCOUNTANT",
      "permissions": ["DRE_VIEW", "DASHBOARD_VIEW"],
      "costCenterScope": "ALL"
    }
  ]
}
```

O frontend forma o seletor de empresas somente a partir desta resposta.

#### `POST /api/auth/logout`

Resposta: `204`.

### 4.2 Movimentos contábeis

#### `GET /api/companies/{companyId}/accounting-movements`

Parâmetros obrigatórios: `startDate`, `endDate`.

Resposta `200`:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000201",
    "accountingDate": "2026-07-01",
    "accountDescription": "Conta demonstrativa",
    "accountKey": "3.1.1",
    "side": "DEBIT",
    "amount": 1250.50,
    "history": "Histórico fictício",
    "sourceEntryKey": "origem-ficticia-1"
  }
]
```

Estado atual: lista sem paginação. Evolução obrigatória: paginação, busca, filtros e ordenação server-side com período obrigatório.

### 4.3 Catálogo e vínculo de classificação DRE

#### `GET /api/dre-classifications`

Retorna o catálogo global FINEMP:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000301",
    "dreClassification": "Receita",
    "dreOpenClassification": "Receita operacional",
    "ebitdaClassification": "Receita líquida"
  }
]
```

Os textos acima são meramente fictícios; `0`, `N/A` e `(-) Verificar`, quando presentes nos dados reais, não podem ser reinterpretados pelo consumidor.

#### `GET /api/companies/{companyId}/accounting-accounts/classifications`

Resposta `200`:

```json
[
  {
    "accountId": "00000000-0000-0000-0000-000000000401",
    "accountingSourceId": "00000000-0000-0000-0000-000000000402",
    "accountKey": "3.1.1",
    "description": "Conta demonstrativa",
    "status": "PENDING",
    "classificationId": null,
    "dreClassification": null,
    "dreOpenClassification": null,
    "ebitdaClassification": null
  }
]
```

`accountKey` não é global. A identidade da conta inclui a fonte/empresa; o frontend não deve mesclar contas pelo código textual.

#### `PUT /api/companies/{companyId}/accounting-accounts/{accountId}/classification`

```json
{
  "classificationId": "00000000-0000-0000-0000-000000000301"
}
```

Resposta: `204`. Exige `ACCOUNT_CLASSIFICATION_MANAGE` explícita na empresa e escopo integral autorizado.

### 4.4 DRE inicial

#### `GET /api/companies/{companyId}/dre`

Parâmetros obrigatórios: `startDate`, `endDate`.

Resposta `200`:

```json
{
  "lines": [
    {
      "dreClassification": "Grupo demonstrativo",
      "dreOpenClassification": "Abertura demonstrativa",
      "ebitdaClassification": "Linha demonstrativa",
      "total": 1000.00
    }
  ],
  "unclassifiedAccountCount": 2
}
```

O backend já agrega os três níveis disponíveis e informa contas pendentes. A hierarquia oficial de drill-down e os níveis adicionais vistos no protótipo permanecem ponto aberto.

### 4.5 Importação Questor atual

#### `POST /api/accounting-sources/{accountingSourceId}/questor-import`

Parâmetros obrigatórios: `startDate`, `endDate`. Resposta: `204`.

Este endpoint:

- fica desativado quando o executor controlado é habilitado; nesse modo usar
  a API SyncRun, sem contornar seu protocolo de exclusão mútua;
- usa a [porta canônica de leitura](architecture/accounting-source-reader.md),
  valida contexto/fonte ativa e retorna 400/409/502 neutros quando necessário;
- mantém a validação da identidade externa da empresa como gate de produção
  separado: CNPJ esperado não é comprovação da empresa no ERP;
- só existe quando a integração Questor está habilitada;
- é uma operação administrativa, nunca uma leitura de tela;
- não expõe credenciais;
- será evoluído para o modelo de `SyncRun`/Connector;
- deve exigir `QUESTOR_SYNC` e acesso à empresa dona da fonte.

## 5. Erros

Status mínimos:

| Status | Significado para o consumidor |
| --- | --- |
| `400` | entrada inválida, como período invertido; não repetir sem corrigir |
| `401` | sessão ausente/expirada ou login inválido; voltar ao fluxo de autenticação |
| `403` | usuário autenticado sem acesso/permissão/escopo; não tentar contornar |
| `404` | recurso inexistente dentro do escopo autorizado |
| `409` | conflito de estado ou idempotência em operações futuras |
| `422` | regra de negócio não atendida em operações futuras |
| `500` | falha inesperada; exibir mensagem neutra e preservar correlation id quando fornecido |

Erros de validação devem convergir para `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A data inicial deve ser anterior ou igual à data final."
}
```

**PROPOSTA:** adicionar `code`, `correlationId` e erros por campo de forma retrocompatível. Mensagens nunca incluem SQL, stack trace, credenciais ou detalhes do banco externo.

## 6. Paginação planejada

Para movimentos, contas, decisões, lançamentos manuais e históricos:

```text
GET ...?page=0&size=50&search=texto&sort=accountingDate,desc
```

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalItems": 0,
  "totalPages": 0
}
```

Regras de ordenação e tamanho máximo são definidas pelo servidor. Busca não deve aceitar fragmentos SQL ou nomes internos de colunas.

## 7. Roadmap de contratos planejados

Estes recursos não estão disponíveis ainda. Os caminhos registram intenção e podem ser refinados por Issue/PR sem comprometer consumidores atuais.

### 7.1 Estrutura SaaS e administração

- `/api/accounting-firms`
- `/api/accounting-firms/{accountingFirmId}/clients`
- `/api/clients/{clientId}/companies`
- `/api/companies/{companyId}/accounting-sources`
- `/api/users`, convites, ativação e recuperação segura de senha
- grants, roles, permissions e escopo futuro por centro de custo

Hierarquia aprovada:

```text
FINEMP → Contabilidade → Cliente → Empresa/CNPJ → AccountingSource
```

Um cliente pode ter várias empresas. A consolidação pertence ao cliente e recebe seleção explícita das empresas participantes.

### 7.2 Sincronização e Connector

- provisionamento seguro de Connector e fonte;
- criação e consulta de `SyncRun`;
- `INITIAL`, `INCREMENTAL` e `RECONCILIATION`;
- cursor, contadores, status, falhas e rastreabilidade;
- publicação atômica, sem carga parcial;
- credenciais externas armazenadas fora de código, Git, logs e documentação.

Fluxo obrigatório:

```text
ERP → Adapter/Connector → sincronização → PostgreSQL FINEMP
    → reporting/domain → REST API → frontend
```

### 7.3 Classificação

- desclassificação;
- importação controlada de PlanoQ;
- preview e conflitos;
- pendências;
- auditoria/histórico.

Nenhuma evolução deve assumir que `accountKey` identifica uma conta entre empresas.

### 7.4 Reporting

- dashboard;
- DRE comparativa e drill-down;
- Balanço;
- Composição;
- Fluxo de Caixa;
- DMPL;
- DuPont;
- Analítico paginado;
- Indicadores;
- Diagnóstico;
- metadados/premissas versionadas.

Cada endpoint só será publicado depois de classificar suas regras como fato, decisão, proposta ou ponto aberto e homologar as regras financeiras necessárias.

### 7.5 Persistência funcional

- Ata de Decisões em PostgreSQL, com auditoria e paginação;
- lançamentos manuais em PostgreSQL, com permissão e histórico;
- preferências visuais continuam no frontend.

## 8. Regras para o cliente TypeScript

O cliente a ser gerado a partir do OpenAPI deve:

- ser fino, sem fórmulas ou persistência de negócio;
- enviar cookies e CSRF corretamente;
- usar tipos gerados, evitando duplicação manual;
- tratar 401 e 403 de forma distinta;
- não inferir acesso a partir de itens de menu;
- não mesclar contas por `accountKey`;
- preservar valores de classificação desconhecidos;
- formatar, mas não recalcular, valores financeiros.

## 9. Contas financeiras operacionais

Com `FINEMP_FINANCE_CATALOG_ENABLED`, `/api/companies/{companyId}/bank-accounts` oferece GET paginado/POST e `/{bankId}` oferece GET/PUT/DELETE. Exige permissões financeiras explícitas por Empresa e escopo ALL; administração não as substitui. PUT recebe expectedVersion, DELETE recebe version; ambos aceitam a versão inicial zero e recusam estado divergente. Saldo inicial retorna string decimal exata de duas casas. Dados cadastrais não são saldo calculado nem plano contábil. Ver [contrato e dependências operacionais](architecture/financial-bank-accounts.md) e OpenAPI.

## 10. Parceiros comerciais

Com a mesma flag financeira, /api/companies/{companyId}/business-partners oferece GET paginado/POST e /{partnerId} oferece GET/PUT/DELETE. VIEW ou MANAGE explícitos por Empresa e escopo ALL; escrita exige MANAGE, sessão e CSRF. Dados ficam em profile tipado (PF/PJ, documento validado, papéis, nome, contato/endereço); expectedVersion acompanha PUT e version acompanha DELETE. Documento manual é obrigatório; pendentes legados/importações não são criados por este endpoint. CUSTOMER e SUPPLIER incluem BOTH nos filtros; active omitido inclui os dois estados. O consumidor pode manter o padrão visual de ativos enviando active=true. Cadastro manual independe da consulta pública de CNPJ. Ver [contrato e dependências](architecture/business-partners.md) e OpenAPI; agregados financeiros e ficha CRM seguem entregas próprias.

## 11. Centros de resultado operacionais

/api/companies/{companyId}/result-centers oferece catálogo paginado, busca e subárvore por rootId; GET/POST na coleção, GET/PUT/DELETE no UUID. Código não é alterável; create recebe code/name e inicia ativo, update recebe name/active/expectedVersion. hasChildren é calculado no servidor e não depende de filtros da página. VIEW/MANAGE explícitos ALL, escrita MANAGE com CSRF. /settings recebe requireResultCenter/expectedVersion e inicia false/version=null quando não persistido; não reclassifica legado. POST /suggested-plan é explícito, atômico e exclusivo de plano vazio. Adapter de uso e exigência nas operações financeiras seguem39/42. Ver [hierarquia e dependências](architecture/operational-result-centers.md) e OpenAPI.

## 12. Categorias financeiras

/api/companies/{companyId}/financial-categories expõe lista paginada e item GET/PUT/DELETE; POST /groups cria grupo com uma sub inicial e POST /{groupId}/subcategories cria sub. FINANCIAL_CATEGORY_VIEW/MANAGE ALL explícitos; escrita exige MANAGE com CSRF. O backend gera códigos, controla versões e retorna raw e effective. NULL de atividade/custo na sub herda do grupo; NULL de DRE no PUT fotografa a classificação atual do grupo, preservando o handler homologado. Sub recém-criada sem DRE ainda usa fallback dinâmico. O cliente apenas apresenta essa distinção. Sem desativação: uso impede excluir, e grupo sem uso exclui seus filhos na mesma transação. Plano padrão completo e referências operacionais são entregas próprias. Ver [regras e evidências](architecture/financial-categories.md).

## 13. Compatibilidade

Até a introdução de versionamento explícito, alterações aditivas são preferidas. Renomear campos, alterar significado ou remover operações exige migração coordenada. O endpoint atual `/api/companies/{companyId}/dre` permanece canônico para a DRE inicial; uma futura família `/reporting` deve ser adicionada ou migrada por uma decisão documentada, não por troca silenciosa.

