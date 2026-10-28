# Parceiros comerciais — Issue120 /38

## FATO CONFIRMADO DA REFERÊNCIA

Consulta pontual às funções959–1141 da cópia privada já preservada, sem refazer inventário ou ZIP: cadastro manual distingue PF/PJ, valida CPF/CNPJ (inclusive CNPJ alfanumérico), exige nome com pelo menos dois caracteres e documento único dentro da Empresa, incluindo inativos. Fantasia é limpa para PF. Papéis cliente e fornecedor incluem o parceiro com ambos os papéis nos filtros; o filtro ambos é exato. Campos de contato, inscrições e endereço são cadastrais. UF é convertida para maiúsculas e limitada aos dois primeiros caracteres; código municipal mantém apenas dígitos. E-mail é opcional e validado quando preenchido.

Parceiro usado por lançamento é inativado na exclusão e conserva histórico; sem uso é removido. Não há quantidade mínima. Cadastros sem documento observados no protótipo vêm de migração/importação; essa exceção não transforma documento manual em opcional. Consulta externa CNPJ é auxiliar e permite preenchimento manual quando falha.

## DECISÃO

BusinessPartner é cliente/fornecedor comercial de uma Empresa. Client continua representando cliente SaaS da Contabilidade; AccountingMovement continua sendo fato contábil importado. Não se inferem vínculos entre esses conceitos.

Persistência relacional com UUID, perfil embutido tipado, versão inicial zero e timestamps do servidor. API recebe profile e expectedVersion; a versão é obrigatória no PUT e deve ser omitida/null no POST. O profile é substituído integralmente. Formatos de exibição de CPF/CNPJ permanecem no consumidor; o backend valida e normaliza. Não há dependência de API externa para salvar. Textos opcionais null/ausentes tornam-se vazios, preservando o comportamento do formulário. Limites técnicos: nome/fantasia/contato/endereço principal255, cidade/bairro150, telefone/inscrições80, número40, CEP20, notas4000; documento de entrada64 antes de normalizar. Limites não constituem novas regras financeiras.

BUSINESS_PARTNER_VIEW ou MANAGE autoriza leitura; mutação exige MANAGE. Grant explícito por Empresa e escopo ALL; papel administrativo não concede acesso automaticamente. Autoridade é reconsultada após aguardar trava da Empresa. Mesmo recurso de outra Empresa retorna404 após autorização da empresa da rota. Empresas de outros Clientes/Contabilidades não se tornam acessíveis por conhecer UUID. Rotas específicas atravessam autenticação/CSRF; rotas não implementadas continuam negadas.

Escrita serializada por Empresa em READ_COMMITTED, com timeout5s e conflito seguro para transação ambiente incompatível. Documento é verificado dentro da trava antes de escrever, inclusive inativos, com constraint física de unicidade e chave composta (company_id,id) para referências futuras. Isso evita depender apenas da pré-validação contra corridas. Versão é comparada antes de alterar e retornada após flush. Listagem paginada1–100 (padrão50), busca literal normalizada sem acentos por nome/fantasia/cidade e documento a partir de3 caracteres. Ordem técnica estável por nome de exibição normalizado/UUID; não afirma collation idêntica ao navegador pt.

Payloads/entidades/respostas não incluem dados pessoais em toString; erros têm mensagens fixas sem eco de documento, nome, e-mail ou diagnóstico SQL. Nenhuma credencial ou dado real é usado na entrega. Feature compartilhada FINEMP_FINANCE_CATALOG_ENABLED permanece desligada por padrão. O módulo não implementa cálculos monetários.

## DEPENDÊNCIAS E PONTOS ABERTOS ISOLADOS

BusinessPartnerReferenceGuard é a porta de consulta de uso operacional. #39 fornecerá adapter de lançamentos e respectivas FKs; teste com guarda simulada não comprova integração inexistente. Constraints físicas impedem exclusão de referência já existente. Atualização de rótulo histórico/contato pertence aos módulos que persistem esses dados, não a uma consulta de AccountingMovement.

Consulta auxiliar de CNPJ, importação/migração de cadastros pendentes e política de proveniência pertencem à #52. Relatórios de recebido/pago/em aberto dependem da #41; ficha comercial depende da #48. Não há migração automática, carga de exemplos reais ou promessa de agregados nesta API. #38 continua aberta para categorias e centros.

## VALIDAÇÃO

TDD de CPF/CNPJ e regras de segurança/versão/desativação (RED → GREEN), com documentos sintéticos. Contratos PostgreSQL/HTTP cobrem timestamps/versão, isolamento entre Contabilidades/Clientes/Empresas, busca/papéis, duplicidade inclusive inativa, corridas de cadastro, histórico, CSRF, sessão/revogação, isolamento transacional e FK composta. PostgreSQL real é executado pela CI quando Docker local estiver indisponível; compilar não significa executar integração.
