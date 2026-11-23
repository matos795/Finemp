# HANDOFF_CONTABIL.md — Módulo Contábil FINEMP

Handoff técnico para o time do backend Java/Spring do FINEMP.

O documento descreve, tela por tela, o que o módulo contábil construído neste projeto exibe, calcula e grava. O objetivo é permitir a integração com o backend existente.

Este documento **não** define endpoints novos e **não** cria regras novas. Onde algo nunca foi decidido, está marcado como ponto aberto.

---

## 0. Como ler este documento

| Marca | Significado |
|---|---|
| **[FATO]** | Comportamento que existe no código construído e publicado, conferido na versão atual. |
| **[DECISÃO]** | Definição explícita tomada pelo usuário: nas conversas deste projeto ou no pedido deste handoff. |
| **[ABERTO]** | Nunca foi decidido. O backend **não** deve preencher com suposição: precisa de definição do produto. |
| **[PROPOSTA]** | Sugestão deste handoff para rota e prioridade (o pedido exigiu os dois campos). Não é decisão: validar com o produto. |

Convenções:

- **Competência:** `AAAA-MM`.
- **Valores:** reais com 2 casas.
- **Sinal no razão:** débito positivo e crédito negativo (padrão D − C).
- **Códigos de tela:** entre crases, por exemplo `cfdocs`. São os identificadores internos de aba usados no protótipo e servem para rastrear o código.

---

## 1. Contexto e premissas

### 1.1 O que existe hoje

- **[FATO]** O módulo contábil foi construído como um **protótipo de front-end em um único arquivo HTML**: um artefato publicado no claude.ai, sem backend próprio.
- **[FATO]** Faz parte de um software de gestão por grupo de empresas. No menu, o módulo é o bloco **"Contabilidade (equipe Finemp)"**, que tem três grupos:
  - **Central de Fechamento**, com quatro subgrupos: Rotina do mês, Fiscal, Folha e Qualidade da base;
  - **Pacote de Consolidação**;
  - **Sucesso do Cliente**.
- **[FATO]** **Nenhum endpoint, contrato REST ou esquema de banco foi especificado neste chat** para o módulo contábil. Em todas as telas, o campo "Contrato de API imaginado anteriormente" está como **"nenhum"**.

### 1.2 Origem dos dados no protótipo

- **[FATO] Dados contábeis.** São um **snapshot estático embutido no HTML**, gerado offline por scripts Python a partir de planilhas exportadas do Questor:
  - "Base Fechamento", "Base Gerencial" e a planilha de gestão `.xlsm`;
  - conteúdo: plano de contas, saldos iniciais, razão com contrapartida e histórico, movimento mensal D/C e encerramentos.
  - Para a Volumetria, também entram o razão detalhado e o cadastro do DP (Questor).
- **[FATO]** O protótipo **não lê o Firebird diretamente**. A base é atualizada quando o HTML é regerado.
- **[FATO] Trabalho do usuário.** Classificações, lançamentos preparados, regras, checklists etc. ficam no **banco de documentos do runtime de artefatos do claude.ai**, em coleções. Há uma cópia de segurança no `localStorage` do navegador.
- **[FATO] Capacidades do runtime usadas:**
  - `db`: coleções de documentos;
  - `assets`: upload de arquivos;
  - `sample`: leitura de documentos por IA;
  - `user`: e-mail e nome de quem acessa;
  - `downloads`: geração de arquivo.

  São essas as únicas "APIs" consumidas hoje. Nenhuma é do FINEMP.

### 1.3 Decisões do usuário para a integração (dadas neste pedido)

- **[DECISÃO]** A área contábil pertence à **contabilidade** (equipe do escritório) e permite **selecionar seus clientes/empresas**.
- **[DECISÃO]** **Questor/Firebird é fonte externa somente leitura.**
- **[DECISÃO]** O FINEMP mantém **sua própria cópia sincronizada em PostgreSQL**.
- **[DECISÃO]** **Não misturar** o módulo financeiro do cliente (Fluxo de Caixa e telas de gestão do dono) com o módulo contábil.
- **[DECISÃO]** Nenhuma credencial nem dado real de cliente neste documento.
- **[FATO]** O token da API GOB (situação fiscal) nunca deve ser lido nem exposto. O GOB **não** faz parte deste handoff (seção 9.3).

### 1.4 Diferenças entre o protótipo e o alvo

- **[FATO] Seleção de empresa.** No protótipo, o seletor mostra só as empresas de **um** grupo (3 empresas do cliente piloto), embutidas no arquivo.
- **[DECISÃO]** No alvo, o seletor lista os clientes/empresas da contabilidade.
- **[ABERTO]** A hierarquia exata do seletor nunca foi decidida: escritório → cliente/grupo → empresa, ou lista plana de empresas.
- **[FATO] Competência em fechamento.** Por padrão é o **mês seguinte ao último mês da base**, editável em cada tela da Central. Fica só na memória da sessão: não é gravada.
- **[FATO] Controle de acesso.** O protótipo aplica perfis **só na interface**: os dados vão todos no HTML.
- **[ABERTO]** Toda a autorização real precisa ser feita no backend. Não há modelo definido (seção 9.1).

---

## 2. Modelo de dados base (consumido por todas as telas)

Os nomes abaixo são os conceitos do protótipo, e não um esquema proposto.

### 2.1 Conta contábil (plano de contas)

**[FATO]** Atributos usados:

| Atributo | Uso |
|---|---|
| código classificado | Ex.: `1.1.01.001.0001`; chave de ligação. |
| código reduzido | Usado no arquivo de exportação. |
| descrição | Nome da conta. |
| grupo patrimonial | `AC`, `ANC`, `PC`, `PNC`, `PL` ou `RES`. |
| subgrupo | Ex.: "Fornecedores", "Clientes", "Bancos". |
| linha da DRE | Ex.: "RECEITA BRUTA", "(-) IMPOSTOS E DEVOLUÇÕES", "(-)CUSTOS", "(-) DESPESAS", "OUTRAS", "FINANCEIRAS", "(-) IR E CS". |
| categoria gerencial | Agrupamento dentro da linha da DRE. |
| flag "fora do plano de gestão" | Conta nova sem classificação gerencial. |

- **[FATO]** O protótipo identifica fornecedores e clientes como **contas contábeis individuais**: subgrupo "Fornecedores…" ou "Clientes…", uma conta por fornecedor ou cliente, com o CNPJ às vezes no nome.
- **[ABERTO]** Não há cadastro de participantes separado. Nunca foi decidido se o backend terá entidade "Participante" ligada à conta.

### 2.2 Lançamento do razão (Questor, somente leitura)

**[FATO]** Campos usados:

- conta;
- contrapartida: pode não existir; em lançamento de múltiplas partidas, a contrapartida é "múltipla";
- valor com sinal;
- data;
- histórico;
- número/chave do lançamento (Volumetria);
- documento, quando houver;
- flag de **lançamento de encerramento**, que é ignorado nas análises.

### 2.3 Saldos e movimentos

- **[FATO]** Saldo inicial por conta.
- **[FATO]** Débitos e créditos por conta e por mês.
- **[FATO]** Encerramento do resultado por conta e por mês.
- **[FATO]** Saldo final do mês = saldo inicial + Σ (D − C) até o mês.

### 2.4 Lançamento preparado na Central (dado do FINEMP, gravável)

Coleção `cflanc`. **[FATO]** Campos:

| Campo | Conteúdo |
|---|---|
| `id` | Identificador. |
| `emp` | Empresa. |
| `comp` | Competência. |
| `data` | Data do lançamento. |
| `deb`, `cred` | Códigos de conta. |
| `ccD`, `ccC` | Centros de custo. |
| `valor` | Valor. |
| `hist` | Histórico. |
| `doc` | Documento. |
| `origem` | `manual`, `doc`, `rec`, `antecip`, `adiant`, `lalur`, `folha`. |
| `ref` | Chave de idempotência, ex.: `rec:<modelo>:<comp>`. |
| `status` | `rascunho`, `aprovado`, `exportado` ou `excluido`. |
| `lote` | Ex.: `L2026-10-1`. |
| `exportadoEm` | Data e hora da exportação. |
| `em` | Data e hora da última gravação. |

- **[FATO]** Cada lançamento preparado é **partida simples** (uma conta a débito e uma a crédito). Um documento gera N linhas.
- **[DECISÃO]** O destino final desses lançamentos é a **importação no Questor por arquivo**. O FINEMP não escreve no Firebird.

### 2.5 Coleções gravadas pelo módulo

**[FATO]** Coleções no protótipo, todas com escopo por empresa:

| Coleção | Conteúdo |
|---|---|
| `cfdocs` | Documentos enviados e a extração. |
| `cflanc` | Lançamentos preparados. |
| `cfrec` | Modelos recorrentes. |
| `cfantecip` | Despesas antecipadas. |
| `cflalur` | Apurações do Lalur e Parte B. |
| `cfregras` | Regras de classificação aprendidas. |
| `cfcfg` | Configuração por empresa: centro de custo, layouts, históricos, regime fiscal, analistas, volumetria. |
| `cffolha` | Totais da folha por competência. |
| `cffiscal` | Valores declarados. |
| `cfroteiro` | Roteiro do analista. |
| `checklist` | Checklist de fechamento. |
| `suportes` | Suportes dos saldos. |
| `depara` | Regras de-para do plano de contas. |
| `config` | Diretório de suportes, cadastro da empresa e logo. |
| `conselho` | Coleção genérica, com os documentos `cong-*` (congelamento) e `acessos-grupo` (usuários). |
| `ata` | Ações, usada por Sucesso do Cliente. |

- **[FATO] Documentos imutáveis.** Os documentos lidos do banco do runtime vêm **congelados** e são sempre copiados antes de editar. Não afeta o backend, mas explica a função `dbCopia` no código.

---

## 3. Navegação geral e componentes comuns

### 3.1 Barra de contexto da Central

**[FATO]** Componente presente em quase todas as telas da Central:

- campo **Competência em fechamento** (`input month`);
- texto "Base contábil até mmm/aa", indicando o último mês importado;
- ações específicas da tela.

### 3.2 Flags de pré-fechamento

**[FATO]** Flags globais da sessão:

| Flag | Padrão | Efeito |
|---|---|---|
| **Considera pré-fechamento?** | ligada | Soma aos saldos os lançamentos preparados ainda não importados no Questor. |
| **Incluir rascunhos** | ligada | Inclui os rascunhos nessa soma. Desligada, só os aprovados entram. |

Usadas em Pré-fechamento, Lalur e Folha.

### 3.3 Ferramentas transversais de tabela

- **[FATO] Ordenar e filtrar.** Clicar no cabeçalho ordena A–Z ou Z–A. Tabelas com 8 linhas ou mais ganham o campo "Filtrar esta tabela…". Ficam de fora as tabelas em árvore e as marcadas para exclusão.
- **[FATO] Drilldown universal.** Linhas cujo rótulo é uma conta, categoria ou grupo ganham lupa. Clicar abre um modal com:
  - as contas e seus débitos e créditos;
  - os lançamentos (data, histórico, contrapartida, D/C);
  - o resumo por contrapartida.

  Depende do razão completo.
- **[FATO] Imprimir e Excel.** Toda tela tem **Imprimir** (A4 paisagem) e **Excel**: uma planilha "Informações" e uma por tabela.

### 3.4 Seletor de empresa e filtro de período

- **[FATO]** Ficam no cabeçalho global.
- **[FATO]** Telas da Central usam a **competência**. Telas do Pacote usam o **mês ATÉ** do filtro global.
- **[FATO]** Há também "Exibir valores em R$ ou R$ mil".

---

## 4. Telas — Central de Fechamento · Rotina do mês

### 4.1 Cockpit de Fechamento (`cfcock`)

- **Rota proposta [PROPOSTA]:** `/contabil/empresas/{empresaId}/fechamento/{competencia}`
- **Objetivo [FATO]:** num lugar só, a situação do fechamento da competência para a empresa, com os passos e o que falta.
- **Quem utiliza [FATO]:** analista e supervisor da contabilidade. Perfis com acesso: Administrador e Analista Finemp (seção 9.1).
- **Dados exibidos [FATO]:**
  - **4 cartões:**
    - lançamentos do pré-fechamento: quantidade, valor total e valor aprovado;
    - documentos a classificar;
    - alertas do pré-fechamento;
    - situação de centro de custo da empresa.
  - **9 passos com status** `ok`, `pendente` ou `sem dados`, cada um com um resumo e o passo seguinte:
    1. Documentos do mês: quantidade e quantos a classificar.
    2. Lançamentos recorrentes: modelos ativos × gerados na competência.
    3. Despesas antecipadas: itens e se a apropriação do mês foi gerada.
    4. Adiantamentos: quantidade de sugestões de baixa.
    5. IRPJ e CSLL (Lalur): último período e se está fechado.
    6. Folha: se o relatório da competência foi informado.
    7. ISS, PIS e COFINS: sempre "sem dados" (só orienta).
    8. Pré-fechamento: número de alertas.
    9. Lote para o Questor: rascunhos, aprovados, exportados e lançamentos sem centro de custo.
- **Filtros [FATO]:** competência.
- **Tabelas [FATO]:** nenhuma; só cartões e passos.
- **Botões e ações [FATO]:** cada passo navega para a tela correspondente.
- **Modais [FATO]:** nenhum.
- **Estados vazios [FATO]:** passos sem cadastro aparecem como "sem dados".
- **Validações [FATO]:** nenhuma.
- **Cálculos [FATO]:**
  - contagens e somas sobre `cflanc`, `cfdocs`, `cfrec`, `cfantecip`, `cflalur` e `cffolha`;
  - "sem centro de custo" = lançamentos com conta de resultado sem centro de custo, quando a empresa usa centro de custo.
- **Regras de negócio [FATO]:**
  - um passo fica "pendente" quando há itens a tratar (documentos não classificados, modelos não gerados, apropriação não gerada, sugestões de baixa, Lalur não fechado, folha não informada, alertas, rascunhos);
  - o passo de Lalur fica "pendente" se não houver apuração salva.
- **Permissões [FATO]:** só de interface (seção 9.1).
- **Navegação [FATO]:** `cfdocs`, `cfrec`, `cfantecip`, `cfadiant`, `cflalur`, `cffolha`, `cfimp`, `cfpre`, `cflanc`.
- **Dados mock [FATO]:** nenhum. Os indicadores são derivados dos dados gravados.
- **Dados de API [FATO]:** coleções do runtime (seção 2.5).
- **Contrato de API anterior:** nenhum.
- **Dependências:** todas as telas da Rotina do mês e Fiscal/Folha.
- **Prioridade [PROPOSTA]:** **P1**. Agrega o estado das outras telas; depende delas existirem.

### 4.2 Roteiro do Analista (`cfrot`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fechamento/{competencia}/roteiro`
- **Objetivo [FATO]:** passo a passo do fechamento por competência, com responsável, prazo, situação, revisor e observação.
- **Quem utiliza [FATO]:** analistas (execução) e supervisor (revisão).
- **Dados exibidos [FATO]:**
  - **13 etapas fixas** em 5 blocos, cada uma com prazo em dias úteis após o fim do mês (D+n):
    - **Preparação:**
      - conferir importação e diagnóstico (D+1);
      - definir responsáveis e competência (D+1).
    - **Movimento do mês:**
      - documentos (D+3);
      - recorrentes (D+2);
      - antecipadas (D+2);
      - adiantamentos (D+3).
    - **Folha:** conciliar (D+4).
    - **Fiscal:**
      - ISS/PIS/COFINS (D+5);
      - IRPJ/CSLL (D+6).
    - **Revisão e envio:**
      - alertas do pré-fechamento (D+6);
      - aprovar lotes (D+7);
      - gerar arquivo e importar no Questor (D+7);
      - checklist e revisão do supervisor (D+8).
  - Cada etapa tem um texto "Como fazer".
  - Barra de progresso "x/13 concluídas" e contador de atrasadas.
- **Filtros [FATO]:** competência.
- **Tabela [FATO]:** Etapa | Responsável | Prazo (data + D+n) | Situação | Revisor | Observação | Atualizado | Abrir →
- **Campos [FATO]:**
  - Responsável: texto, com sugestões da lista de analistas;
  - Situação: `pendente`, `andamento`, `concluido`, `revisado` ou `na` (não se aplica);
  - Revisor: texto;
  - Observação: texto;
  - **Analistas da equipe:** nomes separados por vírgula, gravados na configuração da empresa.
- **Botões [FATO]:** "Abrir →" navega para a tela da etapa.
- **Modais [FATO]:** nenhum. "Como fazer" é um expansor.
- **Estados vazios [FATO]:** todas as etapas começam como pendentes.
- **Validações [FATO]:** nenhuma.
- **Cálculos [FATO]:**
  - Prazo = n-ésimo dia útil do mês seguinte à competência. Conta só **sábado e domingo** como não úteis: **sem feriados**.
  - Atrasada = prazo < hoje e situação diferente de concluído ou revisado.
- **Regras de negócio [FATO]:** a lista de etapas e os prazos D+n são **fixos no código**.
- **Pontos abertos:**
  - **[ABERTO]** Calendário de feriados (nacional ou municipal).
  - **[ABERTO]** Etapas configuráveis por cliente.
- **Persistência [FATO]:** documento `rot-{empresa}-{competencia}` em `cfroteiro`, com itens indexados por etapa.
- **Permissões [FATO]:** de interface.
- **[ABERTO]** Se o responsável deve ser um usuário do sistema ou texto livre.
- **Navegação [FATO]:** `diag`, `cfdocs`, `cfrec`, `cfantecip`, `cfadiant`, `cffolha`, `cfimp`, `cflalur`, `cfpre`, `cflanc`, `checklist`.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Dependências:** cadastro de analistas (configuração da empresa).
- **Prioridade [PROPOSTA]:** **P1**.

### 4.3 Checklist de Fechamento + Suportes dos Saldos (`checklist`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/checklist/{ano}`, com a subseção `/suportes`.
- **Objetivo [FATO]:**
  - checklist mensal "Análise de fechamento contábil" com validação automática por item;
  - anexação de suportes que comprovam cada saldo, com conciliação.
- **Quem utiliza [FATO]:** analista (elabora) e supervisor (revisa).

#### Bloco Checklist

- **Dados exibidos [FATO]:** 33 itens em 3 blocos, com frequência:
  - **Patrimoniais:**
    - p1–p7 mensais;
    - p8–p12 trimestrais (mar, jun, set, dez);
    - p13–p19 anuais (dez).
  - **Resultado:** r1–r10 mensais.
  - **Produto:** d1–d4 mensais.
  - Os textos dos itens estão no Anexo A.
- **Grade [FATO]:**
  - 12 meses × itens;
  - status por célula: `OK`, `Atenção`, `Falha`, `Manual` ou "não se aplica";
  - resumo no topo.
- **Filtros [FATO]:** ano (select).
- **Ações [FATO]:**
  - clicar no **mês**, no cabeçalho, mostra as validações automáticas do mês;
  - clicar no **item** abre o detalhe com evidências, comentário e link para a tela relacionada;
  - **"Marcar itens OK"** marca como OK todos os itens com validação automática OK;
  - campos **Elaborado por** e **Revisado por** (nome), com data registrada.
- **Validação automática por item [FATO]:**
  - **Patrimoniais:**
    - composição × saldo;
    - percentual de saldos com suporte;
    - conciliação bancária pelo suporte anexado;
    - natureza invertida;
    - impostos a recuperar: compensação nos últimos 3 meses e histórico com Selic;
    - empréstimos e parcelamentos com movimento no mês;
    - vencidos há mais de 30 dias cruzados com a Ata;
    - adiantamentos cruzados com clientes e fornecedores;
    - certidões (dependem do GOB, fora deste handoff);
    - investimento × PL da investida;
    - imobilizado em andamento ou importação com mais de 180 dias;
    - créditos fiscais com mais de 4 ou 5 anos;
    - LP → CP em dezembro.
  - **Resultado:**
    - receita, deduções e custos comparados com a média de 6 meses;
    - folha e ocupação;
    - fornecedor em contas distintas;
    - depreciação comparada com a média de 3 meses;
    - antecipadas sem amortização no mês;
    - impostos: deduções e IR do trimestre.
  - **Produto:**
    - composição registrada no mês;
    - base importada;
    - Ata do mês;
    - oscilações do balanço acima de 10% **e** acima de R$ 50 mil exigem comentário.
- **Persistência [FATO]:**
  - coleção `checklist`, documento `{empresa}-{ano}`;
  - formato: `it[item][MM] = {ok, em, com, auto}` e `ass[MM] = {elab, elabEm, rev, revEm}`.

#### Bloco Suportes dos saldos

- **Campos [FATO]:**
  - Diretório fixo dos suportes: caminho de rede ou URL. Fica em `config/emp-{id}.dirSup`.
  - Caminho sugerido no padrão `diretório\AAAA\MM\categoria\conta`.
- **Tabela [FATO]:** por conta (bancos e aplicações, empréstimos e parcelamentos, impostos a recuperar) e por grupo (impostos a pagar, clientes, fornecedores, folha, adiantamentos, imobilizado, investimentos, demais). Colunas:
  - saldo contábil;
  - suporte (arquivo ou caminho);
  - saldo do documento;
  - situação: **Conciliado**, **Com diferença** ou **Sem suporte**.
- **Upload [FATO]:**
  - PDF, imagem, CSV, TXT/OFX ou JSON, até 20 MB;
  - **.xlsx não é aceito** (informar caminho ou link);
  - documento em `suportes`: `{id, emp, mes, ref (conta ou 's:<grupo>:<subgrupo>'), nome, asset|path, saldoDoc, em}`.
- **Estados vazios [FATO]:** conta sem suporte aparece como "Sem suporte".
- **Validações [FATO]:**
  - tamanho de arquivo;
  - tipo de arquivo;
  - conciliação = saldo do documento × saldo contábil.
- **[ABERTO]** A tolerância de conciliação não está documentada neste handoff e precisa ser confirmada no código antes de portar.

#### Comum aos dois blocos

- **Permissões [FATO]:** de interface.
- **[ABERTO]** Quem pode assinar "Revisado por" (papel de supervisor não existe como perfil).
- **Navegação [FATO]:** cada item abre uma tela relacionada:
  - **contábeis:** Composição dos Saldos, Balancete Analítico, Balanço, DRE, Diagnóstico;
  - **fora do módulo:**
    - Faturamento, Custo de Pessoal, Custos e Despesas, Estrutura Societária e Ata;
    - GOB: Débitos, Certidões e PER/DCOMP.

  Ver seção 9.
- **Mock [FATO]:** nenhum. Os itens que dependem do GOB ficam como "Manual" quando não há dados.
- **Contrato de API anterior:** nenhum.
- **Dependências:**
  - razão e saldos;
  - Composição dos Saldos;
  - Ata (módulo do cliente);
  - GOB (excluído);
  - Despesas Antecipadas;
  - inconsistências de Custos e Despesas.
- **Prioridade [PROPOSTA]:** **P0** para o checklist; **P1** para os suportes.

### 4.4 Documentos e Classificação (`cfdocs`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fechamento/{competencia}/documentos`
- **Objetivo [FATO]:** receber documentos do mês, extrair os dados, sugerir a contabilização e gerar lançamentos preparados após a conferência do analista.
- **Quem utiliza [FATO]:** analista.

#### Entrada

- **Upload [FATO]:**
  - área de arrastar, múltiplos arquivos;
  - formatos: `.pdf .png .jpg .jpeg .webp .gif .xml .csv .txt .xlsx .xls .ofx`;
  - arquivos de até 20 MB vão para o armazenamento, exceto xlsx e xml.

#### Listagem

- **Tabela [FATO]:**
  - colunas: Arquivo (nome, tipo, tamanho) | Emitente (nome, CNPJ) | Valor | Competência | Situação | Sugestão (até 3 linhas D/C) | ações;
  - ordenação: mais recente primeiro.
- **Situações [FATO]:** `novo`, `lido` (XML), `lendo…`, `sugerido`, `rascunho`, `classificado`, `erro`.
- **Ações por linha [FATO]:**
  - **Ler com Claude:** extração por IA;
  - **Sugerir pelo histórico:** sem IA;
  - **Classificar:** abre o modal;
  - **excluir.**

#### Modal "Classificar · {arquivo}"

- **Painel do documento [FATO]:** imagem ou link e os dados extraídos:
  - Tipo, Número, Emitente, CNPJ, Emissão, Vencimento, Competência;
  - Valor total, Retenções, Descrição, Natureza;
  - fonte da sugestão e confiança.
- **Campos [FATO]:**
  - Competência e Data;
  - linhas com Débito, CC débito, Crédito, CC crédito, Valor e Histórico (as colunas de centro de custo só aparecem se a empresa usa centro de custo);
  - "+ linha" e "remover";
  - checkbox **"Guardar como regra para este fornecedor"**, marcado por padrão.
- **Botões [FATO]:** Fechar | Salvar como rascunho | **Aprovar lançamentos**.

#### Extração

- **XML [FATO]:** NF-e, CT-e e NFS-e (ABRASF e padrão nacional) são **lidos localmente, sem IA**:
  - tipo, número, emitente e CNPJ, destinatário;
  - emissão, vencimento, competência;
  - valor total e líquido;
  - retenções: IRRF, INSS, ISS, PIS/COFINS/CSLL;
  - descrição e natureza.
- **Demais formatos [FATO]:** a IA devolve JSON com os mesmos campos, mais `periodo_cobertura_meses`.

#### Sugestão de contabilização

**[FATO]** Por ordem de prioridade:

1. **Regra aprendida** do fornecedor, por raiz de CNPJ (8 dígitos) ou nome normalizado.
2. **Sem IA:** a conta de resultado mais usada para esse fornecedor no razão.
3. **Com IA:** proposta em partidas dobradas, restrita a códigos de uma lista fechada:
   - contas do histórico do fornecedor;
   - as 70 contas de resultado mais usadas;
   - contas de retidos a recolher;
   - "Fornecedores diversos".

   Orientação dada à IA:
   - despesa a débito e fornecedor a crédito;
   - retenções a crédito em retidos, reduzindo o valor a pagar;
   - documentos com cobertura de vários meses vão para despesas antecipadas.

   Linhas com conta inexistente são descartadas.

- **Identificação do fornecedor [FATO]:** pela raiz do CNPJ no nome da conta ou por similaridade de palavras do nome. Ignora palavras como LTDA, ME, COMERCIO etc.

#### Gravação

- **Validações ao aprovar [FATO]:**
  - conta de débito válida;
  - conta de crédito válida;
  - valor > 0;
  - histórico não vazio;
  - se a empresa usa centro de custo: centro de custo obrigatório em conta de resultado, a débito ou a crédito.

  O rascunho pode ser salvo sem validação.
- **Ao salvar [FATO]:**
  - os lançamentos anteriores do documento (`ref = doc:{id}`) são **substituídos**;
  - linhas incompletas são descartadas;
  - status `aprovado` ou `rascunho`;
  - documento passa a `classificado` ou `rascunho`.
- **Ao aprovar com "guardar regra" [FATO]:** grava em `cfregras`:
  - id = raiz do CNPJ ou nome;
  - linhas com **percentual** de cada uma sobre o total;
  - crédito do próprio fornecedor substituído pelo marcador `{fornecedor}`;
  - histórico padrão;
  - contador de usos.

#### Demais campos

- **Estados vazios [FATO]:**
  - sem documentos: tabela vazia;
  - IA indisponível: aviso para usar "Sugerir pelo histórico" ou classificar à mão;
  - sem armazenamento: aviso de que o arquivo fica só na sessão.
- **Cálculos [FATO]:** rateio das linhas da regra pelo percentual × valor total do novo documento.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** os lançamentos gerados seguem para Lotes e Exportação; as regras aparecem em Base de Classificação.
- **Mock [FATO]:** nenhum.
- **API usada [FATO]:** `assets` (arquivo), `sample` (IA), `db`.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** Onde armazenar os arquivos no alvo (storage do FINEMP).
  - **[ABERTO]** Se a leitura por IA será mantida no backend e com qual provedor.
  - **[ABERTO]** Retenção e LGPD dos documentos.
  - **[ABERTO]** Deduplicação de documento (mesma chave de NF-e enviada duas vezes): não há regra.
- **Dependências:**
  - plano de contas e razão (12 meses);
  - configuração de centro de custo;
  - históricos padrão.
- **Prioridade [PROPOSTA]:** **P0**.

### 4.5 Base de Classificação (`cfbase`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/classificacao`
- **Objetivo [FATO]:** mostrar a base que o sistema usa para classificar: o histórico do razão, as regras aprendidas e os lançamentos preparados.
- **Quem utiliza [FATO]:** analista e supervisor.
- **Filtros [FATO]:** busca livre (fornecedor, histórico ou conta). Aplica-se às tabelas 1 e 3.
- **Tabelas [FATO]:**
  1. **Histórico por fornecedor (razão dos últimos 12 meses):**
     - colunas: Fornecedor | Conta principal | % das vezes | Lançamentos | Valor 12m | Outras contas (até 3, com contagem) | Último histórico;
     - selo "mais de uma conta";
     - considera lançamentos em conta de resultado cuja contrapartida é do subgrupo Fornecedores;
     - limite de 300 linhas, ordenadas por valor.
  2. **Regras aprendidas:**
     - colunas: Fornecedor | CNPJ | Natureza | Contabilização aprendida (D/C e %) | Vezes | Última | excluir;
     - ordenadas por número de usos.
  3. **Lançamentos classificados:**
     - colunas: Competência | Data | Débito | Crédito | Valor | Histórico | Origem | Situação;
     - todos os meses, até 500 linhas.
  4. **Históricos padrão:**
     - textarea, um modelo por linha;
     - variáveis: `{numero} {fornecedor} {nome} {descricao} {competencia} {parcela} {total} {periodo}`;
     - botão "Salvar históricos padrão".
- **Estados vazios [FATO]:**
  - "Nenhuma regra aprendida ainda. Elas são criadas ao aprovar documentos.";
  - "Nenhum lançamento classificado na Central ainda."
- **Regras de negócio [FATO]:**
  - fornecedor com mais de uma conta indica classificação inconsistente;
  - excluir a regra afeta as próximas sugestões.
- **Valores padrão [FATO]:** os 6 históricos padrão iniciais estão no Anexo B.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** nenhuma explícita.
- **Mock [FATO]:** os históricos padrão iniciais são valores-semente do código, não mock.
- **Contrato de API anterior:** nenhum.
- **Dependências:** razão, `cfregras`, `cflanc`, configuração da empresa.
- **Prioridade [PROPOSTA]:** **P1**.

### 4.6 Lançamentos Recorrentes e Planilhas (`cfrec`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/recorrentes`
- **Objetivo [FATO]:** cadastrar modelos de lançamentos que se repetem (seguros, IPTU, aluguel, honorários, licenças, provisões) e gerar o lote da competência sem duplicar.
- **Quem utiliza [FATO]:** analista.

#### Tabela de modelos

- **Colunas [FATO]:** Categoria | Descrição | Tipo | Débito | Crédito | CC (se usa) | Valor (mensal ou total) | Parcelas | Início | Fim | Dia | Histórico | "Em {competência}" (valor que será gerado) | ação.
- A última linha serve para adicionar um modelo novo.
- **Categorias [FATO]:** Seguros, IPTU, Aluguel, Honorários, Software e licenças, Provisões, Depreciação manual, Juros e encargos, Outros.
- **Tipos [FATO]:**
  - `fixo`: valor mensal, entre início e fim;
  - `parcelado`: valor total ÷ parcelas a partir do início.
- **Botões [FATO]:** Adicionar, excluir e **"Gerar lote de {competência}"**, na barra.

#### Planilhas de importação

**[FATO]** Quatro cards, cada um com **Baixar modelo** e **Importar** (xlsx, xls ou csv):

- Lançamentos avulsos;
- Modelos recorrentes;
- Despesas antecipadas;
- Centros de custo.

#### Cálculos [FATO]

- **Parcelado:**
  - parcela = round(total ÷ n, 2);
  - a **última parcela absorve a diferença de arredondamento**;
  - fora do intervalo [início, início + n − 1], não gera.
- **Fixo:** gera se início ≤ competência ≤ fim.
- **Data do lançamento:** mínimo entre o dia configurado (padrão 28) e o último dia do mês.

#### Regras de negócio [FATO]

- **Idempotência:** `ref = rec:{modelo}:{competencia}`. Se já existe um lançamento com essa referência, ele é pulado, e o contador mostra "já existiam".
- **Status gerado:** `rascunho`.
- **Histórico:** modelo aplicado com {descricao}, {competencia}, {parcela} e {total}.
- **Modelos inativos** (`ativo === false`) não geram lançamento.

#### Demais campos

- **Estados vazios [FATO]:** a tabela mostra só a linha de inclusão.
- **Validações [FATO]:** o lançamento só é gerado com valor > 0.
- **[ABERTO]** Validação de conta no cadastro do modelo: não há; só na aprovação ou exportação.
- **[ABERTO]** O layout exato das 4 planilhas modelo e as regras de importação (colunas obrigatórias, tratamento de erro) não estão especificados neste handoff. Extrair do código antes de portar.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** os lançamentos gerados vão para Pré-fechamento e Lotes.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Dependências:** plano de contas, centros de custo, configuração.
- **Prioridade [PROPOSTA]:** **P0**.

### 4.7 Despesas Antecipadas (`cfantecip`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/despesas-antecipadas`
- **Objetivo [FATO]:** controlar despesas pagas antes do consumo, gerar a apropriação mensal e conciliar com o saldo do balanço.
- **Quem utiliza [FATO]:** analista.

#### Tabela "Itens e apropriação do mês"

- **Colunas [FATO]:** Descrição | Categoria | Conta do ativo | Conta de despesa | CC (se usa) | Valor total | Início (mês) | Meses | Parcela {comp} (com i/n) | Saldo após | ação.
- Linha final de inclusão.
- Rodapé com o total.
- **Categorias [FATO]:** Seguros, IPTU, Licenças e software, Assinaturas, Aluguel antecipado, Outros.
- **Botões [FATO]:** Adicionar, excluir, **"Gerar apropriação de {comp}"**.

#### Bloco "Conciliação com o balanço"

- **[FATO]** Compara:
  - o saldo contábil das contas da linha de despesas antecipadas, na competência ou no último mês da base;
  - o saldo pelos cadastros da tela.

  O alerta aparece se a diferença for maior que R$ 1.
- **[FATO]** Abaixo, sugestões do razão: débitos nos últimos 12 meses nessas contas, com contrapartida não sendo de resultado e valor não cadastrado. Até 40 linhas: Data | Conta | Valor | Histórico | **Cadastrar**.
- **[FATO]** "Cadastrar" pré-preenche o item, com a conta de despesa sugerida.

#### Cálculos [FATO]

- Parcela = round(valor ÷ meses, 2). A última parcela absorve o arredondamento.
- Saldo após = valor − parcela × i.
- **Conta de despesa sugerida:** a conta de resultado mais usada nas apropriações anteriores daquela conta do ativo.

#### Regras de negócio [FATO]

- Idempotência pela referência `antecip:{item}:{comp}`.
- Lançamento gerado: D despesa / C ativo, no último dia do mês, status `rascunho`.
- Histórico: "APROPRIAÇÃO {descricao} PARC {i}/{n}".
- **Itens sem conta de ativo ou de despesa ficam de fora**, com aviso.

#### Demais campos

- **Pontos abertos:**
  - **[ABERTO]** A identificação das "contas de despesas antecipadas" depende do **código da linha do pacote de consolidação** (mapeamento do Pacote de Consolidação), e não de uma configuração própria.
  - **[ABERTO]** Se o alvo usará outra forma de identificar essas contas.
- **Estados vazios [FATO]:** "Nenhum débito nas contas de despesas antecipadas nos últimos 12 meses fora dos cadastros."
- **Validações [FATO]:** a descrição é obrigatória para incluir; os meses têm padrão 12.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** lançamentos para Pré-fechamento e Lotes.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Dependências:**
  - razão;
  - mapeamento do pacote (linha de despesas antecipadas);
  - planilha de importação (em `cfrec`).
- **Prioridade [PROPOSTA]:** **P1**.

### 4.8 Adiantamentos a Fornecedores e de Clientes (`cfadiant`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/adiantamentos`
- **Objetivo [FATO]:** identificar adiantamentos em aberto que já têm nota (fornecedor) ou fatura (cliente) posterior e sugerir a baixa.
- **Quem utiliza [FATO]:** analista.

#### Tabelas

1. **"Adiantamentos que podem ser baixados" [FATO]:**
   - colunas: Tipo (Fornecedor ou Cliente) | Fornecedor/cliente | Adiantado desde | Adiantamento em aberto | Notas/faturas depois do adiantamento (valor e quantidade) | Saldo da conta dele | **Baixa sugerida** | Lançamento (D/C) | ação "Gerar baixa" ou selo "gerado".
2. **"Saldos por fornecedor e cliente" [FATO]:**
   - colunas: Tipo | Nome | Adiantado | Baixado | Em aberto | Desde | Dias;
   - inclui os itens não identificados.

#### Cálculos [FATO]

- **Identificação das contas:**
  - adiantamento a fornecedor: AC com subgrupo de adiantamento e nome contendo fornecedor ou importação;
  - adiantamento de cliente: PC com nome "adiantamento … cliente".
- **Entidade:** a contrapartida do lançamento ou o nome encontrado no histórico.
- **Em aberto** = adiantado − baixado.
- **Notas posteriores** = movimentos na conta do fornecedor ou cliente desde o primeiro adiantamento:
  - créditos, para fornecedor;
  - débitos, para cliente.
- **Baixa sugerida** = mínimo entre o valor em aberto e o saldo da conta do fornecedor ou cliente. Só aparece quando há notas posteriores e saldo > 0.

#### Regras e demais campos

- **Regras de negócio [FATO]:**
  - lançamento de baixa com referência `adiant:{conta}:{comp}`, status `rascunho`;
  - fornecedor: D Fornecedor / C Adiantamento;
  - cliente: D Adiantamento / C Cliente.
- **Estados vazios [FATO]:** "Nenhum adiantamento em aberto com nota ou fatura já lançada para baixar."
- **Validações [FATO]:** não gera de novo se já houver lançamento com a mesma referência na competência.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** para Lotes.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** As regras de identificação são **heurísticas por nome de conta**. Não foi decidido se o alvo terá configuração explícita das contas de adiantamento por empresa.
  - **[ABERTO]** Como tratar adiantamento parcialmente baixado em várias competências: a referência é única por competência.
- **Dependências:** razão completo, plano de contas.
- **Prioridade [PROPOSTA]:** **P2**.

### 4.9 Pré-fechamento (Balancete e DRE) (`cfpre`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fechamento/{competencia}/pre-fechamento`
- **Objetivo [FATO]:** ver o balancete e a DRE da competência **somando** a base do Questor e os lançamentos preparados que ainda vão ser importados, antes de importar. Mostra alertas de linearidade e de natureza.
- **Quem utiliza [FATO]:** analista e supervisor.
- **Filtros [FATO]:**
  - competência;
  - **Considera pré-fechamento?**;
  - **Incluir rascunhos**.

#### Blocos

1. **"Alertas e recomendações" [FATO]:**
   - um bloco destacado, "N conta(s) de resultado recorrente(s) ainda sem lançamento": tabela Conta | Média 6m | Sugestão;
   - os demais alertas, até 30.
2. **"DRE de pré-fechamento" [FATO]:**
   - colunas: os 6 meses anteriores | Média | {comp} (pré) | Var. vs média;
   - linhas: grupo da DRE → conta, com marcador de alerta;
   - linha final "Resultado antes de IR e CSLL e depois".
3. **"Balancete de pré-fechamento" [FATO]:**
   - colunas: Conta (código + nome) | Saldo anterior | Débitos | Créditos | Saldo final | Do pré-fechamento;
   - rodapé: Totais (débitos − créditos).

#### Cálculos e regras de alerta [FATO]

Histórico de 6 meses anteriores à competência; média e desvio padrão amostral por conta.

| Alerta | Gravidade | Condição |
|---|---|---|
| **Recorrente sem lançamento** | alta | ≥ max(3, n−1) meses com movimento acima de R$ 1, \|valor do mês\| < R$ 1 e \|média\| > R$ 500. |
| **Valor acima do padrão / fora do padrão** | atenção | \|média\| > R$ 500, \|valor − média\| > max(2 × desvio, 50% da média) **e** > R$ 3.000. |
| **Conta nova no resultado** | informação | Sem movimento nos meses anteriores e \|valor\| > R$ 1.000. |
| **Natureza invertida** (balancete, contas patrimoniais) | atenção | Saldo final contrário à natureza > R$ 100. |

Regras complementares:

- **Natureza invertida** exclui as contas redutoras: (−), depreciação, amortização, PDD e encargos a transcorrer.
- **Natureza invertida** só alerta se houve movimento do pré-fechamento na conta ou se a inversão piorou.
- **Sugestão** do bloco recorrente:
  - "Faturamento do mês ainda não importado", para receita e deduções;
  - "Provisionar ou incluir no lote recorrente", para as demais contas.
- **Saldo anterior** de conta de resultado em janeiro = 0.
- **Mês fora da base:** se a competência ainda não está importada, mostra só os lançamentos da Central, com texto explicativo.

#### Demais campos

- **Estados vazios [FATO]:** "Sem alertas — Receitas e despesas dentro do padrão dos últimos meses."
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** drilldown nas contas.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **[ABERTO]** Os limiares (R$ 500, R$ 1.000, R$ 3.000, R$ 100, 2σ, 50%) são **fixos no código**. Nunca foi decidido se são configuráveis por cliente.
- **Dependências:**
  - razão e saldos por mês;
  - `cflanc`;
  - estrutura da DRE (linhas e categorias, via De-Para).
- **Prioridade [PROPOSTA]:** **P0**.

### 4.10 Lotes e Exportação Questor (`cflanc`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fechamento/{competencia}/lotes`
- **Objetivo [FATO]:** revisar, aprovar e exportar para o Questor, em arquivo texto, todos os lançamentos preparados da competência.
- **Quem utiliza [FATO]:** analista (aprova e gera) e supervisor.

#### Tabela "Lançamentos preparados"

- **Colunas [FATO]:** seleção | Data | Débito | CC D | Crédito | CC C | Valor | Histórico | Origem | Situação | ação.
- Linha de inclusão de lançamento manual.
- **Filtros internos [FATO]:**
  - situação: todos, rascunho, aprovado, exportado;
  - origem;
  - busca.
- **Botões [FATO]:**
  - Adicionar;
  - excluir;
  - **Aprovar selecionados**;
  - **Voltar para rascunho**;
  - **Gerar arquivo Questor · com centro de custo**;
  - **Gerar arquivo Questor · sem centro de custo**.

#### Bloco "Centro de custo e layouts do Questor"

- **Configuração da empresa [FATO]:**
  - flag **"usa centro de custo"**;
  - tabela de centros de custo (código e nome).
- **Editor dos 2 layouts [FATO]:** com e sem centro de custo. Campos:
  - Colunas (ordem);
  - Separador;
  - Decimal;
  - Data;
  - Conta: reduzido, classificação ou completo;
  - Cabeçalho (sim/não);
  - Extensão.
- Botão **Salvar configuração**.

#### Regras de negócio [FATO]

- **Bloqueio por centro de custo:** se a empresa usa centro de custo, a exportação "sem centro de custo" é bloqueada.
- **Escopo da exportação:** só lançamentos `aprovado` da competência, ordenados por data.
- **Validação antes de gerar:** a mesma da seção 4.4. Para o layout sem centro de custo, os erros de centro de custo não bloqueiam.
- **Numeração do lote:** `L{competencia}-{n}`, sequencial por competência.
- **Arquivo:**
  - UTF-8 com BOM e linhas CRLF;
  - nome: `questor-{com-cc|sem-cc}-{empresa}-{comp}-lote{n}.{ext}`.
- **Depois de salvar o arquivo:** os lançamentos passam a `exportado` e recebem o lote e a data e hora.
- **Colunas disponíveis:** data, debito, credito, cc_debito, cc_credito, valor, historico, documento, competencia, lote, origem.

#### Demais campos

- **Estados vazios [FATO]:** "Nenhum lançamento aprovado em {comp}." (ao exportar).
- **Validações [FATO]:** ver Regras de negócio.
- **Permissões [FATO]:** de interface.
- **[ABERTO]** Segregação aprovar × exportar (quatro olhos): não existe.
- **Navegação [FATO]:** origens a partir de `cfdocs`, `cfrec`, `cfantecip`, `cfadiant`, `cflalur` e `cffolha`.
- **Mock [FATO]:** **os layouts do Questor são PROVISÓRIOS**, criados como placeholder até o envio dos layouts oficiais.
  - Sem centro de custo: `;` | `,` | `dd/mm/aaaa` | conta reduzida | colunas data, debito, credito, valor, historico, documento.
  - Com centro de custo: as mesmas, mais cc_debito e cc_credito.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** O **layout oficial de importação do Questor** nunca foi recebido.
  - **[ABERTO]** Como confirmar que o lote foi importado no Questor: hoje "exportado" significa só "arquivo gerado".
  - **[ABERTO]** Estorno e reabertura de lote exportado: não há regra.
- **Dependências:** todas as origens de lançamento; configuração da empresa.
- **Prioridade [PROPOSTA]:** **P0**.

---

## 5. Telas — Central de Fechamento · Fiscal e Folha

### 5.1 IRPJ e CSLL · Lalur (`cflalur`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fiscal/lalur/{ano}/T{trimestre}`
- **Objetivo [FATO]:** apuração **trimestral** do IRPJ e da CSLL no **Lucro Real**: Lalur e Lacs com adições e exclusões, compensação de prejuízo, Parte B e lançamentos de ajuste da provisão.
- **Quem utiliza [FATO]:** analista fiscal e supervisor.
- **Filtros [FATO]:**
  - Ano;
  - trimestre (pílulas 1º a 4º);
  - **Considera pré-fechamento?**;
  - selo de status "fechado" ou "em aberto".

#### Bloco "Adições e exclusões"

- **Tabela [FATO]:** ativo (checkbox) | Tipo (Adição ou Exclusão) | Descrição (selo "automática" ou "Parte B") | Valor | IRPJ ✓ | CSLL ✓ | ação.
- Linha de inclusão manual: tipo, descrição, valor, IRPJ, CSLL e o botão **Adicionar**.
- **Adições automáticas [FATO]:** contas de resultado **devedoras** do trimestre cujo nome ou categoria contém:
  - multa;
  - brinde;
  - doação ou patrocínio;
  - festa ou confraternização;
  - PDD ou crédito de liquidação duvidosa;
  - "provisão p…";
  - equivalência (resultado negativo).
- **Exclusões automáticas [FATO]:** resultado **positivo** de equivalência patrimonial.
- **Itens automáticos [FATO]:** podem ser desmarcados, e a escolha fica gravada em `sel`.

#### Bloco "Cálculo" (IRPJ Lalur × CSLL Lacs)

**[FATO]** Fórmulas:

| Linha | Cálculo |
|---|---|
| LAIR do trimestre | Soma das linhas de LAIR da DRE dos meses do trimestre que estão na base. Se o último mês ainda não estiver na base e o pré-fechamento estiver ligado, soma o movimento de resultado preparado, exceto o grupo IR e CS. |
| Lucro ajustado | LAIR + adições − exclusões, separado por tributo. |
| Compensação | Se o lucro ajustado > 0: mínimo entre o prejuízo disponível e 30% do lucro ajustado. |
| Base | max(0, ajustado − compensação). |
| IRPJ | Base × 15% + adicional de 10% sobre (base − 60.000). Valor do adicional **fixo por trimestre**. |
| CSLL | Base × 9%. |
| Devido | max(0, imposto − deduções). |

- **Campos editáveis [FATO]:**
  - Prejuízo fiscal / base negativa disponível (IRPJ e CSLL);
  - (−) Deduções: IRRF, retenções e estimativas pagas.
- **Botões [FATO]:** **Salvar apuração** e **Fechar e gerar lançamentos**.

#### Bloco "Parte B"

- **Tabela [FATO]:** Item | Natureza | Tributo | Saldo inicial | (+) Constituição | (−) Baixa/realização | Saldo final | Parte A (levar à Parte A) | Suporte | ação.
- Botões: Adicionar, excluir, **Salvar Parte B**.
- **Naturezas [FATO]:**
  - prejuízo fiscal / base negativa;
  - adição temporária (exclui na realização);
  - exclusão temporária (adiciona na reversão);
  - outro (só acompanhamento).
- **Sugestões de itens [FATO]:** 12 descrições prontas (Anexo C).
- **Regras com "Parte A" marcado [FATO]:**
  - adição temporária: constituição entra como adição e realização como exclusão;
  - exclusão temporária: constituição entra como exclusão e reversão como adição.
- **Herança entre trimestres [FATO]:** ao abrir um trimestre novo, herda do anterior (T1 herda do T4 do ano anterior):
  - prejuízo e base negativa (saldo final);
  - itens da Parte B com saldo inicial = ini + constituição − baixa. Os itens zerados saem, salvo marcação "manter".

#### "Fechar e gerar lançamentos" [FATO]

- Apaga os lançamentos anteriores com `ref = lalur:{periodo}`.
- Calcula a diferença entre o devido e a provisão já contabilizada no trimestre:
  - IRPJ pela conta de despesa de IRPJ;
  - CSLL = provisão total − IRPJ.
- Gera os lançamentos de ajuste:
  - **complemento** (D despesa / C provisão) ou **estorno**;
  - no último dia do último mês do trimestre;
  - **com status `aprovado` direto**.
- Marca a apuração como `fechado`.
- **Contas localizadas por nome [FATO]:**
  - despesa de IRPJ e de CSLL no grupo da DRE "IR E CS";
  - "Provisão para imposto de renda" e "Provisão para contribuição social" no PC.

#### Demais campos

- **Persistência [FATO]:** `cflalur`, documento `lal-{empresa}-{ano}-T{t}`, com:
  - `manuais`, `sel`;
  - `prejIR`, `prejCS`, `dedIR`, `dedCS`;
  - `partB[]`, `pbFim`;
  - `res` (lair, bases, devidos, provisão);
  - `status`, `fechadoEm`.
- **Estados vazios [FATO]:** sem meses do trimestre na base, o LAIR = 0. Base indicada como "x/3 mês(es)".
- **Validações [FATO]:** nenhuma além da conversão numérica.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** lançamentos vão para Lotes.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** **Lucro Real anual (estimativa mensal / balancete de suspensão)** não foi tratado: só o trimestral.
  - **[ABERTO]** Reabrir uma apuração fechada.
  - **[ABERTO]** Integração com ECF / e-Lalur.
  - **[ABERTO]** A lista de adições automáticas é heurística por nome. Não foi decidido se o alvo usará um mapeamento fiscal configurável.
  - **[ABERTO]** O lançamento do Lalur nasce `aprovado`, diferente das outras origens, que nascem `rascunho`. Confirmar se é intencional.
- **Dependências:**
  - DRE por trimestre;
  - pré-fechamento;
  - regime fiscal da empresa (seção 5.2);
  - Lotes.
- **Prioridade [PROPOSTA]:** **P1**, para clientes no Lucro Real.

### 5.2 Validador de Lucro Presumido (`cfpres`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fiscal/presumido/{ano}/T{trimestre}`
- **Objetivo [FATO]:** recalcular o IRPJ e a CSLL do Lucro Presumido do trimestre pela receita contabilizada e comparar com o contabilizado e o declarado. Diagnostica o que ficou de fora.
- **Quem utiliza [FATO]:** analista fiscal.
- **Filtros [FATO]:**
  - Ano;
  - trimestre;
  - **Regime da empresa:** Lucro Presumido ou Lucro Real. É gravado na configuração fiscal da empresa e vale também para `cfimp`.

#### Blocos

1. **"Erros e pontos de atenção" [FATO]:** alertas com gravidade.
2. **"Recálculo do trimestre" [FATO]:**
   - tabela IRPJ × CSLL: receitas por atividade, bases, majoração, demais receitas, financeiras, ganho de capital, imposto, adicional, deduções, devido, contabilizado e declarado;
   - campos editáveis: declarado de IRPJ e de CSLL, deduções de IRPJ e de CSLL;
   - botão **Salvar valores declarados**.
3. **"Atividades e percentuais" [FATO]:**
   - tabela: Categoria de receita | Atividade | Presunção IRPJ % | Presunção CSLL % | PIS e COFINS (sim/não) | ISS %;
   - ISS padrão da empresa;
   - configuração por categoria de receita da DRE.

#### Cálculos [FATO]

- **Atividade padrão pelo nome da categoria** (editável):

  | Atividade | IRPJ | CSLL | PIS/COFINS | ISS |
  |---|---|---|---|---|
  | Locação | 32% | 32% | sim | 0 |
  | Frete | 8% | 12% | sim | — |
  | Venda, mercadoria, produto, indústria, sucata | 8% | 12% | sim | — |
  | Venda de imobilizado | ganho de capital | — | não | — |
  | Demais (serviços) | 32% | 32% | sim | ISS padrão da empresa (padrão 5%) |

- **Base presumida** = Σ receita da categoria × percentual.
- **Majoração (LC 224/2025, conforme implementada):**
  - excesso = max(0, receita bruta do trimestre − R$ 1.250.000);
  - majoração = excesso × (base ÷ receita) × 10%;
  - IRPJ a partir de 2026; CSLL a partir do 2º trimestre de 2026.
- **Somadas integralmente às bases:** demais receitas (grupo "OUTRAS", exceto imobilizado), receitas financeiras e ganho de capital.
  - Ganho de capital = max(0, venda de imobilizado − custo do imobilizado baixado).
- **Imposto:**
  - IRPJ = base × 15% + 10% × (base − 60.000);
  - CSLL = base × 9%.

#### Diagnóstico [FATO]

- **Regime configurado = Real:** aviso informativo; o validador serve só para simulação.
- **Receita total do ano anterior > R$ 78 milhões:** alerta "não pode optar pelo Presumido".
- **Presumido com diferença:** se \|contabilizado − calculado\| > max(R$ 100, 2%), o sistema testa **6 variantes** de cálculo para descobrir o que ficou de fora:
  - só a presunção;
  - sem majoração;
  - sem demais receitas;
  - sem financeiras;
  - sem ganho de capital;
  - sem adicional.

  Se uma variante bater dentro da tolerância, informa o item omitido. Senão, informa a diferença.
- **Presumido sem provisão:** alerta.

#### Demais campos

- **Persistência [FATO]:**
  - `cffiscal`, documento `fis-{empresa}-pres-{ano}-T{t}`;
  - configuração fiscal em `cfcfg` (`fis.regime`, `fis.issPad`, `fis.ativ{categoria}`).
- **Estados vazios [FATO]:** "Sem meses de {t}º trimestre de {ano} na base contábil."
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** aviso para usar o Lalur quando o regime é Real.
- **Mock [FATO]:**
  - o **regime padrão** é decidido por uma regra fixa no código (id da empresa do piloto → Real; demais → Presumido);
  - **é um placeholder**: o regime deve vir do cadastro.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** Fonte oficial do regime tributário (cadastro do cliente no FINEMP? Questor?).
  - **[ABERTO]** Atualização das regras legais (majoração, limites) fora do código.
- **Dependências:** DRE por categoria de receita, configuração fiscal.
- **Prioridade [PROPOSTA]:** **P1**.

### 5.3 Validador de ISS, PIS e COFINS (`cfimp`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/fiscal/iss-pis-cofins/{ano}`
- **Objetivo [FATO]:** recalcular mês a mês o PIS, a COFINS e o ISS pela receita contabilizada e confrontar com o contabilizado nas deduções e com o declarado (EFD-Contribuições, DCTF, município).
- **Quem utiliza [FATO]:** analista fiscal.
- **Filtros e ações [FATO]:**
  - Ano;
  - Regime (o mesmo de `cfpres`);
  - **Modelo de planilha de apurações** (download);
  - **Importar apurações declaradas** (xlsx, xls ou csv).

#### Blocos

1. **"Erros e pontos de atenção" [FATO].**
2. **"Apuração mês a mês" [FATO]:**
   - colunas = meses do ano;
   - linhas:
     - Receita bruta;
     - Base de PIS e COFINS;
     - PIS calculado, contabilizado, declarado (campo editável) e situação;
     - o mesmo para COFINS e ISS;
     - no não cumulativo: créditos e receitas financeiras.
   - Botão **Salvar valores declarados**.
3. **"Atividades e alíquotas" [FATO]:** a mesma tabela de `cfpres`.

#### Cálculos [FATO]

- **Alíquotas:**
  - Regime Real → PIS/COFINS **não cumulativo**, 1,65% e 7,6%;
  - Presumido → **cumulativo**, 0,65% e 3%;
  - no não cumulativo, receitas financeiras a 0,65% e 4%.
- **Base de PIS e COFINS:** receitas das categorias marcadas "PIS e COFINS = sim", exceto venda de imobilizado.
- **ISS** = Σ receita × ISS % da atividade.
- **Créditos de PIS/COFINS (não cumulativo):** débitos do mês nas contas de AC com "PIS" ou "COFINS" e "recuperar/crédito/compensar", exceto quando a contrapartida é caixa ou banco.
- **Situação por célula:**
  - "confere" se \|contabilizado − calculado\| ≤ max(R$ 50, 3%);
  - senão "contabilizado a maior" ou "a menor";
  - gravidade "ruim" se a diferença > max(R$ 1.000, 10%).

#### Demais campos

- **Persistência [FATO]:** `cffiscal`, documento `fis-{empresa}-imp-{ano}`, com chaves `pis:AAAA-MM`, `cof:AAAA-MM` e `iss:AAAA-MM`.
- **Estados vazios [FATO]:** "Sem meses de {ano} na base."
- **Permissões [FATO]:** de interface.
- **Mock [FATO]:** **o valor declarado é digitado ou importado.** Não há integração com SPED, DCTF nem prefeitura.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** Integração com obrigações acessórias.
  - **[ABERTO]** ISS por município e retenções.
  - **[ABERTO]** Tratamento da Reforma Tributária (CBS/IBS) neste validador.
- **Dependências:** DRE (receitas e deduções), configuração fiscal.
- **Prioridade [PROPOSTA]:** **P1**.

### 5.4 Validador e Conciliador da Folha (`cffolha`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/folha/{competencia}`
- **Objetivo [FATO]:** a partir dos relatórios da folha da competência, cruzar os totais com **todas as contas de ativo e passivo da folha** e com as despesas de pessoal, apontar diferenças e gerar o lançamento de ajuste.
- **Quem utiliza [FATO]:** analista (contábil e DP).
- **Filtros e ações [FATO]:**
  - **Competência da folha** (padrão: último mês da base);
  - **Considera pré-fechamento?**;
  - **Preencher com o cadastro do DP**.

#### Blocos

1. **"Relatórios da folha" [FATO]:**
   - upload: PDF, imagem, xlsx, xls, csv ou txt;
   - leitura: **CSV e XLSX localmente**; PDF e imagem por IA;
   - tabela: Arquivo | Lido em | Campos encontrados.
2. **"Totais da folha (confira)" [FATO]:**
   - tabela: Total do relatório | Valor (editável) | Origem;
   - botão **Salvar valores**;
   - campos: proventos, INSS descontado, INSS patronal, FGTS, IRRF, consignados, pensão, outros descontos, adiantamentos, líquido, pró-labore, provisão de férias e provisão de 13º. A lista completa está em `FL_CAMPOS` no código.
3. **"Testes e alertas" [FATO].**
4. **"Contas de ativo e passivo × folha" [FATO]:**
   - colunas: Conta (grupo de conciliação → contas) | Saldo em {comp} | Créditos do mês | Relatório da folha | Diferença | Situação | **Gerar ajuste**.
5. **"Despesas de pessoal × folha" [FATO]:**
   - colunas: Despesa | Resultado {comp} | Relatório da folha | Diferença | Situação.

#### Cálculos e conciliações [FATO]

- **Contas da folha:** identificadas por nome ou subgrupo (salário, encargo, férias, 13º, FGTS, INSS, pró-labore, consignado, pensão etc.). Saem as retenções de terceiros, fornecedores, financiamentos e IRPJ/CSLL.
- **Saldo × esperado:**

  | Conta | Esperado |
  |---|---|
  | Salários a pagar | Líquido |
  | Pró-labore a pagar | Pró-labore × **80%** (estimativa do líquido) |
  | INSS | Descontado + patronal |
  | FGTS | FGTS do relatório |
  | IRRF | IRRF do relatório |
  | Consignados | Descontos do mês |
  | Pensão | Descontos |
  | Provisão de férias | Relatório de provisões |
  | Provisão de 13º | Relatório de provisões |
  | Outros descontos | Valor do relatório |
  | Adiantamentos | Devem zerar |

- **Situação da conta:**
  - "confere" se \|dif\| ≤ max(R$ 10, 1%);
  - grave se > max(R$ 1.000, 5%).
- **Resultado:** salários × proventos; INSS patronal; FGTS; pró-labore. Tolerância max(R$ 50, 2%). Férias e 13º do mês ficam só em acompanhamento.
- **Testes de razoabilidade:**

  | Teste | Alerta quando |
  |---|---|
  | FGTS | Fora de 8% ± 0,6 p.p. dos proventos |
  | INSS patronal | Fora de 22% a 30% |
  | INSS descontado | Acima de 14% |
  | Líquido | Diferente de proventos − descontos, acima de max(R$ 50, 1%) |
  | Proventos × salário cadastral do DP | Diferença acima de 25% |

- **"Gerar ajuste" [FATO]:**
  - aparece só quando o grupo tem uma única conta e a diferença é maior que R$ 10;
  - gera o lançamento de ajuste em **rascunho** para Lotes.

#### Demais campos

- **Persistência [FATO]:** `cffolha`, documento `fl-{empresa}-{competencia}`, com `{v: totais, orig, arqs[]}`.
- **Estados vazios [FATO]:**
  - "Nenhum relatório enviado para esta competência.";
  - "Informe ou suba o relatório da folha para ver os testes.";
  - mês fora da base: a conciliação com o resultado aparece depois da importação.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** Lotes.
- **Mock [FATO]:**
  - "Preencher com o cadastro do DP" usa o **cadastro de colaboradores embutido** (snapshot do DP);
  - o fator de 80% do pró-labore é uma estimativa fixa.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** Integração com o módulo DP/Folha do Questor (somente leitura) para trazer os totais em vez de upload.
  - **[ABERTO]** Desoneração, aprendiz e outras alíquotas de FGTS e INSS por empresa.
- **Dependências:** saldos e movimentos, DRE, cadastro do DP, pré-fechamento.
- **Prioridade [PROPOSTA]:** **P1**.

---

## 6. Telas — Central de Fechamento · Qualidade da base

### 6.1 Períodos Congelados (`cfcong`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/periodos-congelados`
- **Objetivo [FATO]:** proteger meses já fechados. Guarda uma fotografia do movimento de cada conta. Quando a base for reimportada ou reprocessada, lista as mudanças nesses meses, que só viram oficiais depois de **aceitas com motivo**.
- **Quem utiliza [FATO]:** supervisor e analista.

#### Blocos

1. **Ações [FATO]:**
   - "Congelar até" (select de meses até o último da base) e **Congelar**;
   - **Descongelar {mês}**: só o último mês congelado.
2. **Linha do tempo [FATO]:** um selo por mês:
   - aberto;
   - congelado;
   - congelado com alterações a aceitar.
3. **"Alterações em meses congelados" [FATO]:**
   - resumo: quantidade, meses e efeito líquido;
   - tabela: Mês | Conta (nome + código) | Grupo | Valor congelado | Valor atual | Diferença;
   - campo **Motivo** (obrigatório) e botão **Aceitar alterações**.
4. **"Registro (quem, quando e por quê)" [FATO]:** Quando | Quem | Ação | Motivo.

#### Cálculos e regras [FATO]

- **Fotografia do mês:**
  - por conta: round(débitos − créditos do mês, 2);
  - só contas com movimento ≥ R$ 0,01;
  - indexada pelo código da conta.
- **Diferença:** \|antes − agora\| ≥ **R$ 0,50**. Contas que sumiram aparecem como "(conta não existe mais)".
- **Congelar:**
  - cria a fotografia dos meses ainda sem fotografia, do início da base até o mês escolhido;
  - registra no log.
- **Aceitar:**
  - refaz a fotografia dos meses com diferença;
  - registra no log o motivo (**mínimo de 5 caracteres**) e até 40 itens (mês, conta, antes, depois).
- **Descongelar:**
  - pede o motivo (prompt);
  - remove a fotografia do último mês congelado;
  - recua a data de corte.
- **Aviso automático:** ao abrir a aplicação, se houver diferença, um modal lista até 20 alterações, com o link "Revisar e aceitar". Aparece uma vez por empresa por sessão.
- **Autor:** quem fez a ação vem da identidade da conta (nome ou e-mail).

#### Demais campos

- **Persistência [FATO]:**
  - documento `cong-{empresa}`: `{ate, log[]}`;
  - documento `cong-{empresa}-{AAAA-MM}`: `{snap, em, quem}`.
- **Estados vazios [FATO]:**
  - "Nenhum período congelado.";
  - "Nenhuma alteração nos meses congelados: a base atual bate com as fotografias."
- **Validações [FATO]:**
  - confirmação ao congelar;
  - motivo obrigatório ao aceitar e ao descongelar.
- **Permissões [FATO]:** de interface.
- **[ABERTO]** Quem pode congelar, aceitar e descongelar.
- **Navegação [FATO]:** do modal de aviso para esta tela.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** No alvo, o congelamento deve **impedir** a sincronização Questor → PostgreSQL de alterar o mês, ou só **sinalizar**? Hoje só sinaliza: os relatórios já mostram o valor novo antes do aceite.
  - **[ABERTO]** Relação com o encerramento ou bloqueio de período no próprio Questor.
- **Dependências:** movimento mensal por conta (sincronização).
- **Prioridade [PROPOSTA]:** **P0**. Com a sincronização automática, é o controle de integridade do fechamento.

### 6.2 Diagnóstico das Bases (`diag`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/diagnostico`
- **Objetivo [FATO]:** verificar a integridade da base importada no período do filtro.
- **Quem utiliza [FATO]:** analista; é a primeira etapa do Roteiro.

#### Conteúdo

- **Resumo por gravidade [FATO]:** alta, média ou baixa.
- **Tabela "Inconsistências" [FATO]:**
  - colunas: Gravidade | Tipo | Conta ou demonstração | Valor | Causa provável;
  - ordenada por gravidade e valor.
- **Lista "Verificações aprovadas" [FATO].**

#### Verificações [FATO]

1. **Ativo ≠ Passivo + PL** em cada mês do período, com tolerância de R$ 0,01. Gravidade alta.
2. **DRE ≠ variação do resultado do exercício no balanço** em cada mês, considerando os encerramentos. Tolerância de R$ 0,01.
3. **Inconsistências vindas do pré-processamento da base**, marcadas como fonte "ERP".
4. **Lançamentos manuais:** pendente de confirmação, sem contrapartida, em mês fechado, não exportados.

#### Demais campos

- **Filtros [FATO]:** período, origem e status do filtro global.
- **Estados vazios [FATO]:** sem inconsistências, mostra só a lista de aprovadas.
- **Permissões [FATO]:** de interface.
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **[ABERTO]** O recurso "Lançamentos manuais" (pela planilha de gestão) foi **retirado da interface a pedido do usuário**, mas as verificações 4 continuam no código. Decidir se saem do alvo.
- **[ABERTO]** Quais verificações o processo de sincronização Questor → PostgreSQL deve executar e gravar (item 3).
- **Dependências:** saldos, DRE, encerramentos, resultado da sincronização.
- **Prioridade [PROPOSTA]:** **P0**. É a validação mínima da cópia sincronizada.

### 6.3 De-Para do Plano de Contas (`depara`)

- **Rota [PROPOSTA]:** `/contabil/empresas/{empresaId}/de-para`
- **Objetivo [FATO]:** garantir que toda conta do Questor tenha uma classificação gerencial:
  - contas de resultado → linha e categoria da DRE;
  - contas patrimoniais → grupo e subgrupo do balanço.

  Contas fora do Plano de Gestão recebem uma **sugestão** pela posição no plano do ERP.
- **Quem utiliza [FATO]:** analista e supervisor.

#### Conteúdo

- **Cartões de resumo [FATO].**
- **Filtros [FATO]:**
  - pílulas: **Pendentes**, **Contas novas**, **Divergentes**, **Com regra**, **Todas as contas · alterar classificação**;
  - busca por código, conta ou grupo.
- **Tabela "Classificação das contas" [FATO]:** código, conta, classificação do ERP, classificação atual ou sugerida e editor por linha.
- **Editor da conta [FATO]**, com três modos:
  1. acatar a sugestão do sistema;
  2. escolher na estrutura das demonstrações: select agrupado com as linhas e categorias existentes e "+ Nova categoria…";
  3. usar a classificação de outra conta (busca código · nome).
- **Escopo [FATO]:**
  - "Somente esta conta" é o padrão para contas do plano;
  - a conta-mãe é o padrão para contas novas. A regra vale também para contas futuras com o mesmo prefixo.
- **Botões [FATO]:**
  - por linha: salvar;
  - **Confirmar sugestões**, só no filtro de pendentes: agrupa as confirmações pela conta-mãe;
  - "Alterar", no filtro "Todas".
- **Tabela "Regras de-para ativas" [FATO]:** prefixo, tipo, destino, data e **remover**. Ao remover, as contas voltam à classificação do plano.

#### Regras e demais campos

- **Regras de negócio [FATO]:**
  - formato da regra: `{p: prefixo do código, t: 'res'|'pat', g, k (resultado) | e, s (patrimonial), em}`;
  - na aplicação, vale a regra de **maior prefixo** que casa com o código;
  - a classificação original fica guardada;
  - as regras valem para todos os usuários.
- **Persistência [FATO]:** coleção `depara`, um documento por empresa: `{regras: [...]}`.
- **Estados vazios [FATO]:** sem pendentes, a tabela fica vazia no filtro padrão.
- **Permissões [FATO]:** de interface.
- **Navegação [FATO]:** afeta todas as demonstrações (DRE, Balanço, Pacote, Pré-fechamento).
- **Mock [FATO]:** nenhum.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** O "Plano de Contas da Gestão" (estrutura gerencial padrão) hoje vem da planilha de gestão do cliente. Não foi decidido se no alvo será **um padrão do escritório** ou **um por cliente**.
  - **[ABERTO]** Versionamento das regras: hoje a regra vale para todos os meses, inclusive os passados.
  - **[FATO]** Existe um ponto de classificação pendente de confirmação com o cliente piloto (um subgrupo de resultados acumulados classificado no passivo circulante). Isso é dado do cliente, não regra.
- **Dependências:** plano de contas do Questor e estrutura gerencial.
- **Prioridade [PROPOSTA]:** **P0**. Sem o De-Para, DRE e Balanço gerenciais ficam incompletos para contas novas.

### 6.4 Volumetria do Contrato (`cfvol`)

- **Rota [PROPOSTA]:** `/contabil/clientes/{clienteId}/volumetria`. É por grupo ou empresa, não por competência.
- **Objetivo [FATO]:** medir o volume de trabalho do escritório para o cliente (funcionários processados na folha e lançamentos contábeis) e apoiar o reajuste de honorários.
- **Quem utiliza [FATO]:** gestão do escritório (comercial / sócio da contabilidade).
- **Filtros [FATO]:**
  - Escopo: o grupo inteiro ou uma empresa;
  - Comparar: mesmo período do ano (jan até o último mês) ou anos completos, com o ano corrente anualizado.

#### Conteúdo

- **Cartões [FATO]:**
  - funcionários na folha, média por mês no ano corrente, com variação contra o ano-base;
  - lançamentos.
- **Tabela "Ano a ano" [FATO]:** Ano | Meses | Funcionários por mês (média) | Pico no mês | Var. vs ano anterior | Admissões | Desligamentos | Lançamentos | Por mês | Partidas (linhas do razão).
- **Gráficos [FATO]:** lançamentos por mês e funcionários por mês, um por ano.
- **Calculadora de reajuste [FATO]:**
  - campos: Ano-base do contrato (2024 ou 2025), Honorário mensal atual (R$), Peso dos funcionários no índice (padrão 50%), Franquia sem reajuste (padrão 5%);
  - fórmulas:

    ```
    índice  = var. funcionários × peso + var. lançamentos × (1 − peso)
    reajuste = max(0, índice − franquia)
    ```

  - resultado aplicado sobre o honorário.

#### Definições e demais campos

- **Definições [FATO]:**
  - lançamento = **chave distinta do lançamento no razão** do Questor;
  - partidas = linhas;
  - funcionários = **CLT que estiveram na folha no mês**, inclusive admitidos e desligados.
- **Persistência [FATO]:** configuração em `cfcfg.vol`: base, comp, honor, pesoF, franquia, emps.
- **Estados vazios [FATO]:** "—" quando não há base de comparação.
- **Permissões [FATO]:** de interface.
- **[DECISÃO]** O contexto é comercial e interno do escritório.
- **Mock [FATO]:**
  - as séries são um **snapshot embutido** (jan/2024 até o último mês), não uma consulta;
  - os anos 2024–2026 e as opções de ano-base estão **fixos no código**.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** A fórmula foi deixada para "ajustar à cláusula do contrato". A cláusula real nunca foi informada.
  - **[ABERTO]** Fonte de funcionários: o DP do Questor é somente leitura (confirmar tabela).
  - **[ABERTO]** Se o honorário fica no FINEMP.
- **Dependências:** razão (contagem de chaves) e DP.
- **Prioridade [PROPOSTA]:** **P2**.

---

## 7. Telas — Pacote de Consolidação

Contexto comum a todo o grupo:

- **[FATO] Origem.** Recria, com os números de cada empresa, as folhas de um **modelo de pacote de consolidação** (planilha "ConsA", 59 folhas) exigido por um grupo controlador. A estrutura (linhas, rótulos, códigos e fórmulas) foi extraída da planilha e fica embutida no código.
- **[FATO] Motor:**
  - converte as fórmulas do Excel em cálculo (SUM, +/−, ROUND, IF por mês, referências entre folhas);
  - liga as linhas aos números da empresa pelo **código do pacote** atribuído a contas e grupos (mapeamento gerencial → pacote).
- **[FATO] Período.** Balanço mensal de dezembro do ano anterior até o mês ATÉ do filtro global; fluxos no mesmo intervalo.
- **[FATO] Drilldown nas tabelas BS, PL e EXPS:**
  - ▸ no título abre as contas;
  - ▸ na conta abre os movimentos: no BS, entradas e saídas do mês; no PL e no EXPS, as 8 maiores contrapartidas mês a mês mais "outras";
  - clicar no nome da conta abre os lançamentos com histórico.
- **[FATO] Ferramentas.** Todas as telas têm Imprimir e Excel.
- **Quem utiliza [FATO]:** contabilidade que entrega o pacote ao controlador.
- **[ABERTO]** É um requisito de **um** cliente: não foi decidido se o pacote é um produto do módulo contábil para todos os clientes ou uma customização.
- **Prioridade [PROPOSTA] para o grupo:** **P2** para o primeiro cliente genérico. Seria P0 apenas se o primeiro cliente for o que exige o pacote: **[ABERTO]**.
- **Contrato de API anterior:** nenhum, em todas as telas do grupo.
- **Rota [PROPOSTA] para o grupo:** `/contabil/empresas/{empresaId}/pacote/{folha}`, com `folha` ∈ {mapa, bs, pl, exps, ss, tfa, ifa, bb, cf, cfinfo, invest, lease, pldtl, mgt, excng, bonus, info, int, fcst, bud}.

| Tela (código) | Objetivo e conteúdo [FATO] | Cálculos e conferências [FATO] | Situação dos dados [FATO] |
|---|---|---|---|
| **Estrutura e Mapa** (`gcmapa`) | Lista das folhas do modelo. Colunas: Folha do pacote, Conteúdo, Situação (criada / não criada), Onde ver, Observação. | — | Estático. Folhas não criadas: as vazias no modelo, as de controle e as sem dado disponível. |
| **Balanço · BS** (`gcbs`) | Balanço mensal com os códigos do pacote e drilldown. | Conferência ativo = passivo + PL por mês. | Real. |
| **Resultado · PL** (`gcpl`) | Receita, custo (com depreciação), margem, despesas por função, venda de imobilizado líquida em outros resultados operacionais, financeiro, IR e lucro. | Conferido com a DRE. | Real. |
| **Custos por Natureza · EXPS** (`gcexps`) | Custos e despesas por natureza: pessoal próprio, mão de obra externa, instalações, depreciação, TI, despesas gerais. | — | Real, conforme o mapeamento por natureza. |
| **Patrimônio Líquido · SS** (`gcss`) | Mutações do PL: capital, AFAC, reservas e lucros acumulados. | — | Real. |
| **Imobilizado · TFA (+GROSS1)** (`gctfa`) | Imobilizado por classe do pacote, movimentação do valor líquido e posição bruta. | — | Real. Os bens de locação foram classificados em "máquinas, equipamentos e bens de locação" (decisão de mapeamento do piloto). |
| **Intangível · IFA** (`gpifa`) | Folha genérica: software e outros intangíveis. | Fórmulas do modelo. | Real nas linhas com código. |
| **Empréstimos · BB** (`gcbb`) | Empréstimos de curto e longo prazo. | — | Real. |
| **Fluxo de Caixa · CF** (`gccf`) | Fluxo indireto mensal, montado das variações do balanço por linha do pacote. | Conferência com a variação do caixa = 0. | Real. |
| **Informações do Fluxo · CF_INFO** (`gpcfinfo`) | Aplicações financeiras: aplicações, resgates e rendimentos. | Fórmulas do modelo. | Real. |
| **Investimentos · INVEST** (`gpinvest`) | Aportes, equivalência, dividendos e baixas. | Fórmulas do modelo. | Real. |
| **Arrendamentos · LEASE** (`gplease`) | Folha genérica. | — | **Só estrutura**: sem fonte de dados. |
| **Detalhe de outros resultados · PLDTL** (`gppldtl`) | Detalhe por conta. | — | Real. |
| **Perdas de rentabilidade · MGT** (`gpmgt`) | Folha genérica. | — | **Só estrutura** ou parcial. |
| **Câmbio · EXCNG** (`gpexcng`) | Rótulos traduzidos. | — | **Só estrutura** (sem operações em moeda estrangeira). |
| **Bônus · BONUS** (`gpbonus`) | Colunas 1º tri, 1º sem, 3º tri, 2º sem. | — | **Só estrutura.** |
| **Informações da empresa · INFO** (`gpinfo`) | Cadastro de contatos e responsáveis do pacote: empresa, CNPJ, país, moeda, representante legal, CFO, contador, tesouraria, aprovador, contato, endereço, telefone. | — | CNPJ e responsáveis vêm da configuração da empresa; os demais campos ficam **"a preencher"**: **não há tela de edição**. |
| **Intercompany · INT_BS / INT_PL** (`gpint`) | Seletor de folha. | Linhas "Inter Company" recebem as contas cujo **nome** identifica empresa do grupo ou sócio. | Real, por **heurística de nome**. |
| **Projeção · PL_F** (`gpfcst`) | Realizado nos meses fechados; meses futuros em branco. | — | Meses futuros **vazios** (sem projeção). |
| **Orçamento · PL_B / EXPS_B** (`gpbud`) | Folha genérica. | — | **Só estrutura.** |

Campos comuns e pontos abertos:

- **Filtros e botões [FATO]:**
  - folhas genéricas: seletor de folha (pílulas) e botão **"Só linhas com valor"**;
  - Ano / mês ATÉ: pelo filtro global.
- **Pontos abertos:**
  - **[ABERTO]** Onde e como manter o **mapeamento conta → código do pacote** no alvo. Hoje está no código e na estrutura gerencial.
  - **[ABERTO]** Envio do pacote: formato do arquivo de entrega ao controlador e preenchimento da planilha original.
  - **[ABERTO]** Fonte de dados para LEASE, EXCNG, BONUS, PL_B e as linhas manuais da INFO.

---

## 8. Tela — Sucesso do Cliente · Acompanhamento das Ações (`sucesso`)

- **Rota [PROPOSTA]:** `/contabil/sucesso-do-cliente/acoes`. A tela é transversal: lista as ações de todas as empresas do grupo.
- **Objetivo [FATO]:** acompanhar e cobrar as ações em aberto da Ata de Decisões, com resumo por responsável, registro de andamento e cobrança por e-mail.
- **Quem utiliza [FATO]:** perfil "Sucesso do Cliente" e equipe do escritório.

#### Conteúdo

- **Cartões de resumo [FATO].**
- **Tabela "Por responsável" [FATO].**
- **Filtros [FATO]:** empresa, responsável e status.
- **Tabela de ações [FATO]:** empresa, decisão, ação, responsável, prazo, status, última cobrança, último andamento e ações.
- **Botões [FATO]:**
  - registrar andamento: grava `acomp[]` com data, quem e texto;
  - **E-mail** por ação;
  - **"Enviar todas por e-mail"**.

  O e-mail abre o programa de e-mail do usuário (`mailto`), já preenchido, com cópia para os usuários marcados como Sucesso do Cliente. Cada envio grava `cobrancas[]` com data e quem.

#### Regras e demais campos

- **Regras de negócio [FATO]:**
  - "atrasada" = prazo anterior a hoje e status diferente de concluída;
  - o e-mail do responsável vem do cadastro de usuários.
- **Persistência [FATO]:** coleção `ata`. Os documentos de ação têm empresa, decisão, ação, responsável, e-mail do responsável, prazo, tipo, status, origem, nível, chave, ROI, `cobrancas[]`, `acomp[]` e datas.
- **Estados vazios [FATO]:** sem ações, a tabela fica vazia.
- **Permissões [FATO]:** perfis Administrador, Analista Finemp e Sucesso do Cliente.
- **Mock [FATO]:** nenhum. **O envio não é automático**: depende do programa de e-mail de quem clica.
- **Contrato de API anterior:** nenhum.
- **Pontos abertos:**
  - **[ABERTO]** **Fronteira de módulo.** A Ata de Decisões pertence à gestão do cliente: é criada pelo dono, pelo conselho e por várias telas, inclusive as do módulo contábil (Checklist, Diagnóstico). Não foi decidido se a Ata é uma entidade compartilhada entre os módulos ou se Sucesso do Cliente fica fora do módulo contábil.
  - **[ABERTO]** Envio de e-mail pelo servidor (SMTP / serviço) e agendamento de cobrança.
- **Dependências:** Ata e Usuários e Acessos.
- **Prioridade [PROPOSTA]:** **P2**.

---

## 9. Permissões, fronteiras e itens fora do escopo

### 9.1 Permissões (estado atual)

- **[FATO]** Existem perfis editáveis na tela **Usuários e Acessos**, que é transversal: fica em Configurações e Ajuda, não no módulo contábil. Gravados no documento `acessos-grupo`. Perfis padrão:

  | Perfil | O que vê | Altera dados |
  |---|---|---|
  | Administrador | Tudo, inclusive usuários | Sim |
  | Analista Finemp | Tudo, menos usuários | Sim |
  | Sócio / Dono | Áreas de gestão | Sim |
  | Conselheiro | Áreas de consulta | Não |
  | Gestor financeiro | Áreas de gestão | Sim |
  | Sucesso do Cliente | Áreas do perfil | Sim |
  | Consulta | Áreas de consulta | Não |
  | Só recebe ações | Sem acesso | — |

- **[FATO]** Nos perfis padrão, **só Administrador e Analista Finemp** veem as telas da Central de Fechamento e do Pacote. Sucesso do Cliente vê a própria tela.
- **[FATO] Cadastro do usuário:** nome, e-mail, perfil, empresas permitidas, área, flag Sucesso do Cliente e ativo.
- **[FATO] Identificação:** pelo e-mail da conta claude.ai.
  - o dono do artefato é sempre Administrador;
  - quem não está cadastrado entra como Consulta;
  - sem nenhum cadastro, todos são Administrador.
- **[FATO] Somente leitura.** A trava só existe em parte da gravação (documentos genéricos e Ata). As gravações da Central de Fechamento **não verificam o perfil**: só a interface é ajustada.
- **[FATO]** O controle é de interface: os dados de todas as empresas do grupo estão no HTML.
- **[ABERTO]** Modelo de autorização do backend: papéis (analista, supervisor, sócio do escritório), escopo por cliente e empresa, permissões por ação (aprovar lote, exportar, congelar, aceitar alteração, fechar Lalur).

### 9.2 Telas fora do módulo contábil acessadas a partir dele

**[DECISÃO]** Não misturar com o módulo financeiro do cliente. As telas abaixo são abertas pelo Checklist, pelo Roteiro ou pelo drilldown, mas **não** estão no bloco "Contabilidade (equipe Finemp)" do menu.

**[ABERTO]** A classificação de cada uma (contábil ou cliente) nunca foi decidida:

- **Balanço** (`bal`), **Composição dos Saldos** (`comp`) e **Balancete Analítico** (`analitico`). Telas de natureza contábil, mas no menu "Patrimônio" do cliente. **Composição dos Saldos** é usada por vários itens do Checklist e pelos suportes.
- **DRE** (`dre`), **Faturamento** (`fat`), **Custos e Despesas** (`cdesp`) e **Custo de Pessoal** (`rhcusto`).
  - **Custos e Despesas** tem a **reclassificação gerencial** (regras por fornecedor: conta de → conta para, a partir de um mês). Ela altera DRE e EBITDA gerenciais, sem tocar no Questor. Visão gerencial × contábil.
- **Estrutura Societária** (`soc`) e **Ata de Decisões** (`ata`).
- **Variações do Balanço** (imobilizado, empréstimos, investimentos, estoques, impostos, folha): conciliações por tipo de movimento; menu Patrimônio.

### 9.3 Fora do escopo deste handoff

- **[FATO / DECISÃO] GOB:** Situação Fiscal, débitos, certidões, PER/DCOMP e caixa postal.
  - O token da API GOB **nunca** deve ser lido, exibido, copiado nem enviado.
  - O Claude não chama a API do GOB.
  - Os itens do Checklist que dependem do GOB (p7, p9, p13) ficam "Manual" quando não há dados.
- **[DECISÃO]** O módulo financeiro do cliente: Fluxo de Caixa FINEMP e telas de gestão do dono.
- **[FATO]** Existe um documento de referência "Agentes de Automação do Fechamento" (15 agentes, calendário D+1 a D+8). Ele não define telas.

---

## 10. Mapa completo da navegação do módulo contábil

```
[Seletor global: cliente/empresa (ABERTO: hierarquia) · competência]
│
└── Contabilidade (equipe Finemp)
    ├── Central de Fechamento
    │   ├── Rotina do mês
    │   │   ├── Cockpit de Fechamento (cfcock) ──► cada passo abre: cfdocs, cfrec, cfantecip, cfadiant,
    │   │   │                                       cflalur, cffolha, cfimp, cfpre, cflanc
    │   │   ├── Roteiro do Analista (cfrot) ──► "Abrir →": diag, cfdocs, cfrec, cfantecip, cfadiant,
    │   │   │                                    cffolha, cfimp, cflalur, cfpre, cflanc, checklist
    │   │   ├── Checklist de Fechamento + Suportes (checklist) ──► comp, analitico, bal, dre, diag
    │   │   │        └── (fora do módulo) fat, rhcusto, cdesp, soc, ata, GOB: gobdeb, gobcert, gobperd
    │   │   ├── Documentos e Classificação (cfdocs) ──[modal Classificar]──► gera lançamentos em cflanc
    │   │   │        └── regras aprendidas ──► cfbase
    │   │   ├── Base de Classificação (cfbase)
    │   │   ├── Lançamentos Recorrentes e Planilhas (cfrec) ──► gera em cflanc
    │   │   ├── Despesas Antecipadas (cfantecip) ──► gera em cflanc
    │   │   ├── Adiantamentos (cfadiant) ──► gera em cflanc
    │   │   ├── Pré-fechamento (cfpre) ◄── lê cflanc (rascunho/aprovado) + base
    │   │   └── Lotes e Exportação Questor (cflanc) ──► arquivo .txt para importação no Questor
    │   ├── Fiscal
    │   │   ├── IRPJ e CSLL · Lalur (cflalur) ──► gera em cflanc (aprovado)
    │   │   ├── Validador de Lucro Presumido (cfpres) ──► (Real) aviso para cflalur
    │   │   └── Validador de ISS, PIS e COFINS (cfimp)  [compartilha regime e atividades com cfpres]
    │   ├── Folha
    │   │   └── Validador e Conciliador da Folha (cffolha) ──► gera ajuste em cflanc
    │   └── Qualidade da base
    │       ├── Períodos Congelados (cfcong) ◄── modal de aviso ao abrir o sistema
    │       ├── Diagnóstico das Bases (diag)
    │       ├── De-Para do Plano de Contas (depara) ──► afeta todas as demonstrações
    │       └── Volumetria do Contrato (cfvol)
    ├── Pacote de Consolidação
    │   ├── Demonstrações e movimentações: gcmapa, gcbs, gcpl, gcexps, gcss, gctfa, gpifa, gcbb, gccf, gpcfinfo
    │   ├── Informações complementares: gpinvest, gplease, gppldtl, gpmgt, gpexcng, gpbonus, gpinfo
    │   ├── Intercompany: gpint
    │   └── Projeção e orçamento: gpfcst, gpbud
    └── Sucesso do Cliente
        └── Acompanhamento das Ações (sucesso) ◄── lê a Ata (fronteira ABERTA)

Transversais: drilldown (modal de lançamentos) · Imprimir/Excel · Usuários e Acessos (Configurações)
Fluxo do lançamento preparado:
  origem (doc | rec | antecip | adiant | lalur | folha | manual)
    → rascunho → aprovado → exportado (lote)
    → [importação manual no Questor] → reaparece na base sincronizada (ABERTO: conciliação)
```

---

## 11. Entidades e conceitos utilizados pelas telas

| Entidade / conceito | Origem | Usado em | Situação |
|---|---|---|---|
| Escritório / contabilidade | — | seletor | **[ABERTO]** não existe no protótipo |
| Cliente / grupo de empresas | embutido (1 grupo) | seletor, volumetria, sucesso | **[DECISÃO]** selecionável; hierarquia **[ABERTO]** |
| Empresa | embutido | todas | **[FATO]** |
| Competência (AAAA-MM) | sessão | Central | **[FATO]** não persistida |
| Conta contábil (plano do Questor) | Questor (somente leitura) | todas | **[FATO]** |
| Estrutura gerencial (linhas da DRE, categorias, grupos e subgrupos do balanço) | planilha de gestão | DRE, balancete, De-Para, Pacote | **[FATO]**; padrão por cliente ou do escritório **[ABERTO]** |
| Regra de De-Para | FINEMP | De-Para e todas as demonstrações | **[FATO]** |
| Lançamento do razão (com contrapartida, histórico, chave, encerramento) | Questor | quase todas | **[FATO]** |
| Saldo inicial / movimento mensal D/C / encerramento | Questor | todas | **[FATO]** |
| Participante (fornecedor/cliente) | conta contábil por participante | cfdocs, cfbase, cfadiant | **[ABERTO]** entidade própria |
| Centro de custo | FINEMP (cadastro por empresa) | cfdocs, cfrec, cfantecip, cflanc | **[FATO]**; vínculo com Questor **[ABERTO]** |
| Documento fiscal/financeiro (arquivo + extração) | upload | cfdocs | **[FATO]** |
| Regra de classificação aprendida | FINEMP | cfdocs, cfbase | **[FATO]** |
| Histórico padrão (template) | FINEMP | cfdocs, cfrec, cfbase | **[FATO]** |
| Modelo recorrente | FINEMP | cfrec | **[FATO]** |
| Despesa antecipada | FINEMP | cfantecip | **[FATO]** |
| Lançamento preparado | FINEMP | cfdocs, cfrec, cfantecip, cfadiant, cflalur, cffolha, cfpre, cflanc | **[FATO]** |
| Lote de exportação | FINEMP (campo do lançamento) | cflanc | **[FATO]**; entidade própria **[ABERTO]** |
| Layout de exportação Questor | FINEMP (configuração) | cflanc | **[FATO] provisório** |
| Configuração da empresa (usa centro de custo, centros, layouts, históricos, regime, ISS padrão, atividades, analistas, volumetria) | FINEMP | várias | **[FATO]** |
| Apuração Lalur trimestral + Parte B | FINEMP | cflalur | **[FATO]** |
| Valores declarados (Presumido; PIS/COFINS/ISS) | digitação/importação | cfpres, cfimp | **[FATO]** |
| Totais da folha por competência | upload/digitação | cffolha | **[FATO]** |
| Cadastro de colaboradores (DP) | Questor DP (snapshot) | cffolha, cfvol | **[FATO]** snapshot |
| Roteiro por competência | FINEMP | cfrot | **[FATO]** |
| Checklist anual por empresa (+ assinaturas) | FINEMP | checklist | **[FATO]** |
| Suporte de saldo | FINEMP + arquivo | checklist | **[FATO]** |
| Fotografia de período congelado + log | FINEMP | cfcong | **[FATO]** |
| Inconsistência de diagnóstico | calculado + pré-processamento | diag | **[FATO]** |
| Estrutura do pacote de consolidação (folhas, linhas, códigos, fórmulas) | planilha do controlador | Pacote | **[FATO]** |
| Mapeamento conta → código do pacote | código | Pacote, cfantecip | **[ABERTO]** onde manter |
| Ação da Ata (+ cobranças, andamentos) | FINEMP (gestão) | sucesso, checklist | fronteira **[ABERTO]** |
| Usuário / perfil | FINEMP | todas | **[FATO]** interface; modelo do backend **[ABERTO]** |
| Séries de mercado (Selic etc.) | embutidas | (fora do contábil; não usadas nas telas acima) | — |

---

## 12. Cálculos e regras contábeis (consolidado)

Todos **[FATO]**, implementados no protótipo. Os limiares são fixos no código, salvo indicação.

### 12.1 Base

1. Saldo da conta no fim do mês = saldo inicial + Σ (débitos − créditos). A natureza é invertida para passivo, PL e receitas na exibição.
2. Ativo = Passivo + PL por mês. Tolerância de R$ 0,01 (Diagnóstico).
3. Resultado da DRE do mês = variação do resultado do exercício no balanço, considerando os encerramentos. Tolerância de R$ 0,01.
4. De-Para: a regra de **maior prefixo** de código define a classificação gerencial. A original é preservada.

### 12.2 Lançamentos preparados

5. Partida simples (uma conta a débito e uma a crédito), valor > 0, histórico obrigatório.
6. Se a empresa usa centro de custo, a conta de resultado exige centro de custo do lado em que aparece.
7. Ciclo de vida:

   ```
   rascunho → aprovado → exportado
       ↑        │
       └────────┘ "voltar para rascunho"
   ```

   - O Lalur gera direto `aprovado`.
   - As demais origens geram `rascunho`, exceto a aprovação de documento, que gera `aprovado`.
8. Idempotência por referência:
   - `rec:{modelo}:{comp}`;
   - `antecip:{item}:{comp}`;
   - `adiant:{conta}:{comp}`;
   - `doc:{id}`: substitui;
   - `lalur:{AAAA-Tt}`: substitui.
9. Lote `L{comp}-{n}`, sequencial por competência. O arquivo é UTF-8 com BOM e CRLF. Os layouts são provisórios.
10. Conta no arquivo: reduzido (último segmento do código), classificação ou código completo. Formatos de data: dd/mm/aaaa, aaaa-mm-dd ou ddmmaaaa.

### 12.3 Rateios e parcelas

11. Parcelado e despesa antecipada: parcela = round(total ÷ n, 2). **A última parcela absorve o arredondamento.**
12. Regra aprendida: cada linha guarda o percentual sobre o total e é reaplicada ao valor do novo documento.

### 12.4 Adiantamentos

13. Em aberto = adiantado − baixado.
14. Baixa sugerida = mínimo entre o valor em aberto e o saldo do participante, só quando há documentos posteriores.

### 12.5 Pré-fechamento (linearidade sobre 6 meses)

15. Recorrente sem lançamento:
    - ≥ max(3, n−1) meses com movimento acima de R$ 1;
    - mês atual com \|valor\| < R$ 1;
    - \|média\| > R$ 500.
16. Fora do padrão:
    - \|média\| > R$ 500;
    - \|valor − média\| > max(2σ, 50% da média);
    - \|valor − média\| > R$ 3.000.
17. Conta nova no resultado: sem movimento nos meses anteriores e \|valor\| > R$ 1.000.
18. Natureza invertida: saldo contrário à natureza > R$ 100, exceto contas redutoras.

### 12.6 Lalur (Lucro Real trimestral)

19. Lucro ajustado = LAIR + adições − exclusões, por tributo.
20. Compensação = mínimo entre o prejuízo disponível e 30% do lucro ajustado (se positivo).
21. IRPJ = 15% × base + 10% × (base − 60.000 por trimestre).
22. CSLL = 9% × base.
23. Devido = max(0, imposto − deduções).
24. Ajuste = devido − provisão contabilizada. Gera complemento ou estorno.
25. Parte B:
    - saldo final = inicial + constituição − baixa;
    - herança automática para o trimestre seguinte;
    - com "Parte A" marcado, a constituição e a realização viram adição ou exclusão, conforme a natureza.
26. Adições automáticas e exclusão de equivalência: por padrão de nome (multa, brinde, doação/patrocínio, festa, PDD, provisão, equivalência).

### 12.7 Lucro Presumido

27. Presunção por atividade (padrão, editável):
    - serviços 32/32;
    - locação 32/32;
    - venda 8/12;
    - frete 8/12;
    - venda de imobilizado → ganho de capital.
28. Majoração de 10% no percentual sobre a receita acima de R$ 1.250.000 no trimestre (LC 224/2025, conforme implementada): IRPJ desde jan/2026; CSLL desde o 2º trimestre de 2026.
29. Demais receitas, financeiras e ganho de capital entram integralmente na base. Ganho de capital = venda − custo do imobilizado.
30. Limite de R$ 78 milhões de receita no ano anterior para optar pelo regime.
31. Diagnóstico por 6 variantes de cálculo. Tolerância max(R$ 100, 2%).

### 12.8 PIS, COFINS e ISS

32. Cumulativo: 0,65% e 3%. Não cumulativo: 1,65% e 7,6% com créditos; financeiras a 0,65% e 4%.
33. ISS = receita × alíquota da atividade (padrão da empresa: 5%).
34. Situação "confere" se a diferença ≤ max(R$ 50, 3%). Grave se > max(R$ 1.000, 10%).

### 12.9 Folha

35. Conciliações conta × relatório. Tolerância max(R$ 10, 1%); grave acima de max(R$ 1.000, 5%).
36. Pró-labore líquido estimado = 80% do bruto.
37. Testes:
    - FGTS 8% ± 0,6 p.p.;
    - INSS patronal entre 22% e 30%;
    - INSS do empregado ≤ 14%;
    - líquido = proventos − descontos;
    - variação contra o cadastro do DP de até 25%.

### 12.10 Congelamento

38. Fotografia = movimento (D − C) por conta e por mês, arredondado em 2 casas.
39. Diferença relevante ≥ R$ 0,50.
40. Aceite exige motivo de pelo menos 5 caracteres.

### 12.11 Roteiro

41. Prazo = n-ésimo dia útil após o fim da competência. Exclui só sábados e domingos.

### 12.12 Volumetria

42. Índice = var. funcionários × peso + var. lançamentos × (1 − peso).
43. Reajuste = max(0, índice − franquia).

### 12.13 Checklist

44. Frequência dos itens:
    - mensal;
    - trimestral: meses múltiplos de 3;
    - anual: dezembro.
45. Oscilação do balanço que exige comentário: acima de 10% **e** acima de R$ 50 mil.
46. Imobilizado em andamento ou importação: alerta acima de 180 dias.
47. Créditos fiscais: alerta acima de 4 ou 5 anos.
48. Vencidos: acima de 30 dias.

### 12.14 Pacote de Consolidação

49. As fórmulas da planilha do controlador são reproduzidas.
50. Conferências: BS fecha, PL = DRE, CF conferido contra a variação do caixa.

---

## 13. Integrações necessárias

| # | Integração | Direção | Situação |
|---|---|---|---|
| 1 | **Questor/Firebird → PostgreSQL FINEMP**: plano de contas, saldos iniciais, razão com contrapartida, histórico, chave e documento, encerramentos | leitura | **[DECISÃO]** somente leitura e cópia sincronizada. Frequência, incremental × completa e janela **[ABERTO]**. |
| 2 | **Questor DP → PostgreSQL**: colaboradores, admissões e desligamentos, vínculo CLT, eventualmente totais da folha | leitura | **[ABERTO]** quais tabelas. Hoje é snapshot. |
| 3 | **FINEMP → Questor**: importação dos lotes | arquivo gerado pelo FINEMP e importado manualmente no Questor | **[FATO]** por arquivo. **[ABERTO]** layout oficial e confirmação da importação. |
| 4 | **Armazenamento de arquivos**: documentos, suportes, relatórios da folha | gravação | **[ABERTO]**. Hoje fica no storage do runtime claude.ai. |
| 5 | **Leitura de documentos por IA**: extração de NF, boleto, apólice, relatório da folha | serviço | **[ABERTO]** se haverá e com qual provedor. O XML é lido sem IA. |
| 6 | **Identidade e autorização** | — | **[ABERTO]**. Hoje é a conta claude.ai. |
| 7 | **E-mail de cobrança** (Sucesso do Cliente) | envio | **[ABERTO]**. Hoje é `mailto`. |
| 8 | **Obrigações acessórias** (EFD-Contribuições, DCTF, ECF, prefeituras) para o "declarado" | leitura | **[ABERTO]**. Hoje é digitado ou importado em planilha. |
| 9 | **GOB** (situação fiscal) | — | **Fora do escopo** (seção 9.3). |
| 10 | **Planilhas de importação**: avulsos, recorrentes, antecipadas, centros de custo, apurações declaradas | upload | **[FATO]** existem modelos. O layout precisa ser extraído do código **[ABERTO]**. |

---

## 14. O que hoje é mock, placeholder ou snapshot

**[FATO]**

1. **Toda a base contábil:** snapshot estático embutido no HTML, regerado manualmente. Não há sincronização.
2. **Cadastro do DP:** snapshot embutido (Folha: "Preencher com o cadastro do DP"; Volumetria).
3. **Séries da Volumetria:** embutidas, de jan/2024 até o último mês da base. Anos 2024–2026 e opções de ano-base fixos no código.
4. **Layouts de exportação do Questor:** provisórios.
5. **Regime tributário padrão:** decidido por uma regra fixa no código, pelo id da empresa do piloto.
6. **Lista de empresas do seletor:** fixa, um grupo.
7. **Valores "declarados"** (Presumido, PIS/COFINS/ISS): digitados ou importados manualmente.
8. **Pacote de Consolidação:**
   - LEASE, EXCNG, BONUS, PL_B, EXPS_B (e IFRS) só com estrutura;
   - PL_F sem projeção;
   - INFO com campos "a preencher";
   - Intercompany por heurística de nome.
9. **Prazos do Roteiro e etapas:** fixos, sem feriados.
10. **Limiares de alertas:** pré-fechamento, folha, fiscal, checklist e congelamento, todos fixos.
11. **Históricos padrão iniciais e listas de categorias:** recorrentes, antecipadas, sugestões da Parte B. São valores-semente do código.
12. **Estimativa de 80% do pró-labore líquido.**
13. **Identificação de contas por nome:**
    - adiantamentos;
    - contas da folha;
    - provisões e despesas de IR/CS;
    - créditos de PIS/COFINS;
    - retidos;
    - "Fornecedores diversos";
    - adições do Lalur.

    São heurísticas, não configuração.
14. **Persistência:** banco do runtime claude.ai com espelho em `localStorage`. Não há backend.
15. **E-mail:** `mailto`.
16. **Permissões:** só de interface.
17. **Leitura por IA:** depende da capacidade do runtime.

---

## 15. Pontos que nunca foram decididos

**[ABERTO]**

### Escopo e produto

1. Hierarquia do seletor: escritório → cliente/grupo → empresa, ou lista plana.
2. Se o Pacote de Consolidação é produto padrão ou customização de um cliente, e sua prioridade.
3. Se Sucesso do Cliente e a Ata pertencem ao módulo contábil, ao de gestão do cliente ou são compartilhados.
4. Em que módulo ficam Balanço, Composição dos Saldos, Balancete Analítico, Variações e a Reclassificação gerencial (seção 9.2).
5. Se a estrutura gerencial (Plano de Contas da Gestão) é padrão do escritório ou por cliente.

### Dados e sincronização

6. Frequência, modo (incremental ou completo) e janela da sincronização Questor → PostgreSQL. Tratamento de lançamentos alterados ou excluídos no Questor.
7. Se o congelamento **bloqueia** a sincronização de meses congelados ou só sinaliza.
8. Como reconhecer, na base sincronizada, que um lote exportado foi importado no Questor (conciliação lote ↔ razão).
9. Quais verificações de integridade a sincronização deve executar e gravar (Diagnóstico).
10. Tabelas do Questor DP a usar (colaboradores e totais da folha).
11. Entidade "Participante" (fornecedor/cliente) separada da conta contábil.
12. Onde manter o mapeamento conta → código do pacote e a identificação das contas especiais: adiantamentos, despesas antecipadas, provisões de IR/CS, retidos, folha.

### Regras

13. Layout oficial de importação do Questor (com e sem centro de custo).
14. Estorno e reabertura de lote exportado; reabertura de Lalur fechado.
15. Se o lançamento do Lalur nasce `aprovado` de propósito.
16. Lucro Real anual (estimativas mensais / suspensão e redução) e integração com ECF.
17. Fonte oficial do regime tributário por empresa.
18. Se os limiares de alerta são configuráveis por cliente.
19. Calendário de feriados para os prazos do Roteiro; etapas configuráveis por cliente.
20. Deduplicação de documentos enviados (mesma chave de NF-e).
21. Cláusula real de reajuste de honorários (Volumetria) e se o honorário fica no FINEMP.
22. Fonte de dados para LEASE, EXCNG, BONUS e orçamento no pacote.
23. Destino das verificações de "Lançamentos manuais" no Diagnóstico, já que o recurso foi retirado da interface.
24. Tratamento da Reforma Tributária (CBS/IBS) nos validadores fiscais.
25. Tolerância de conciliação dos suportes e layout das planilhas de importação: confirmar no código antes de portar.

### Plataforma

26. Modelo de autorização: papéis, escopo por cliente e empresa, permissões por ação (aprovar, exportar, congelar, aceitar alteração, fechar Lalur, assinar revisão). Segregação de funções.
27. Storage de arquivos, retenção e LGPD dos documentos.
28. Uso de IA para leitura de documentos no backend: provedor, custo, auditoria.
29. Envio de e-mail pelo servidor e agendamento de cobranças.
30. Persistência da competência em fechamento por usuário ou por empresa.

---

## Anexo A — Itens do Checklist de Fechamento [FATO]

| Id | Freq. | Item | Tela relacionada |
|---|---|---|---|
| p1 | M | Validar se as contas analíticas possuem composição e suporte anexo | Composição |
| p2 | M | Os bancos e aplicações estão devidamente conciliados e batem com os extratos? | (suportes) |
| p3 | M | Validar se as contas estão com a natureza correta (devedora e credora) e se foram conciliadas de acordo com a operação | Balancete Analítico |
| p4 | M | Analisar se os impostos a recuperar foram atualizados pela Selic e estão sendo compensados nas apurações ou com pedido de recuperação | Composição |
| p5 | M | Empréstimos e parcelamentos foram atualizados | Composição |
| p6 | M | Verificar se o financeiro/cliente foi acionado sobre contas a pagar e a receber em aberto de meses anteriores e se consta na Ata de ocorrências | Composição |
| p7 | M | Analisar se há imposto em atraso, composição, e relacionar na Ata com ponto de alerta | GOB (fora) |
| p8 | T | Contas de adiantamento foram cruzadas com clientes e fornecedores para baixar o adiantamento? | Composição |
| p9 | T | Certidões estão válidas | GOB (fora) |
| p10 | T | Verificar se o investimento da investidora está de acordo com o PL e o resultado da investida, conforme a participação societária | Estrutura Societária |
| p11 | T | Existe imobilizado em andamento que deve ser ativado? | Composição |
| p12 | T | Existe processo de importação em andamento que deveria ser transferido para o estoque? | Composição |
| p13 | A | Analisar se há impostos prescrevendo (mais de 5 anos sem PER/DCOMP) | GOB (fora) |
| p14 | A | Curto e longo prazo foram ajustados no mês de dezembro | Balanço |
| p15 | A | Confirmar se todas as atas registradas na Junta Comercial foram lançadas corretamente | Estrutura Societária |
| p16 | A | Base de cálculo anual do IR e CS, PIS e COFINS está correta? | DRE |
| p17 | A | Existem provisões passivas a serem ajustadas? | Balanço |
| p18 | A | Inventário foi realizado? Existe provisão para obsolescência de estoque? | Balanço |
| p19 | A | Existe ajuste de contratos de arrendamento a ser feito? | Balanço |
| r1 | M | Confirmar se as receitas e devoluções estão de acordo com prefeitura, estado e/ou contratos de venda | Faturamento |
| r2 | M | Validar as apurações dos impostos sobre receitas/devoluções e as devidas contabilizações | DRE |
| r3 | M | Validar se os custos foram calculados e contabilizados corretamente | DRE |
| r4 | M | Conferir se despesas/custos de folha, encargos e provisões estão contabilizados conforme o relatório da folha | Custo de Pessoal |
| r5 | M | Conferir se as despesas de ocupação estão contabilizadas (energia, aluguel, internet, telefonia etc.) e lineares durante o ano | Custos e Despesas |
| r6 | M | Confirmar se as despesas recorrentes estão lançadas (contabilidade, serviços diversos etc.) e de forma linear | Custos e Despesas |
| r7 | M | Analisar se existem gastos de um mesmo fornecedor classificados em contas distintas | Custos e Despesas |
| r8 | M | Validar a contabilização das depreciações e se estão conforme os mapas | DRE |
| r9 | M | Validar se as amortizações mensais de despesas antecipadas estão contabilizadas corretamente | Composição |
| r10 | M | Conferir o cálculo e a contabilização de PIS/COFINS/IRPJ/CSLL e se as guias foram enviadas ao cliente | DRE |
| d1 | M | Validar se os books contábeis estão com suas composições e salvos | Composição |
| d2 | M | Confirmar se o balancete foi importado e a Gestão Financeira está preenchida corretamente | Diagnóstico |
| d3 | M | Verificar o preenchimento da Ata de ocorrências e confirmar se foi informada ao cliente | Ata |
| d4 | M | Garantir que as principais oscilações no balanço foram analisadas e comentadas | Balanço |

Legenda: M = mensal; T = trimestral (mar, jun, set, dez); A = anual (dez).

## Anexo B — Históricos padrão iniciais [FATO]

```
VLR REF NF {numero} {fornecedor}
PROVISÃO {descricao} REF {competencia}
APROPRIAÇÃO {descricao} PARC {parcela}/{total}
BAIXA ADIANTAMENTO {nome}
PAGTO {descricao} {fornecedor}
IRPJ/CSLL APURAÇÃO {periodo}
```

Os históricos gerados são convertidos para MAIÚSCULAS.

## Anexo C — Sugestões de itens da Parte B do Lalur [FATO]

- Provisão para contingências trabalhistas
- Provisão para contingências cíveis e tributárias
- Provisão para créditos de liquidação duvidosa (PDD)
- Provisão para perdas em estoques
- Ajuste a valor justo (AVJ)
- Depreciação acelerada incentivada
- Diferença de depreciação contábil × fiscal
- Juros sobre capital próprio não deduzidos
- Subvenção para investimento
- Lucro de contratos com entes públicos (diferimento)
- Ganho por compra vantajosa
- Variação cambial (regime de caixa)

## Anexo D — Resumo de prioridades [PROPOSTA — validar com o produto]

| Prioridade | Telas |
|---|---|
| **P0** | Diagnóstico das Bases · De-Para · Períodos Congelados · Documentos e Classificação · Lançamentos Recorrentes · Pré-fechamento · Lotes e Exportação Questor · Checklist de Fechamento |
| **P1** | Cockpit · Roteiro do Analista · Base de Classificação · Despesas Antecipadas · Suportes dos Saldos · Lalur (clientes no Real) · Validador de Lucro Presumido · Validador de ISS/PIS/COFINS · Validador da Folha |
| **P2** | Adiantamentos · Volumetria do Contrato · Pacote de Consolidação (todas as folhas; ver 15, item 2) · Sucesso do Cliente |

Critério usado na proposta:

- **P0:** sem a tela, não há fechamento confiável a partir da cópia sincronizada, ou não há saída para o Questor.
- **P1:** acelera ou controla o fechamento.
- **P2:** específico de um cliente ou gerencial do escritório.
