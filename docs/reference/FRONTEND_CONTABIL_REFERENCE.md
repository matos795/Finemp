# FRONTEND_CONTABIL_REFERENCE

Referência visual oficial do módulo **Contabilidade (equipe Finemp)** do protótipo "Gestão Financeira Grupo [cliente piloto]" (v105, artefato versão 109, gerado em 23/11/2026), para o porte ao React no repositório FINEMP.

> **Regra de uso.** O protótipo é a referência de layout, nomenclatura e comportamento visual. **Não redesenhar nem simplificar.** Este documento **não define backend**: onde o protótipo lê dados (runtime `window.claude`, `DADOS_GRUPO`), o porte segue o contrato oficial do FINEMP; tela sem API oficial mostra "Em breve" (decisão já registrada em `HANDOFF_CONTABIL.md` e `accounting-area-map.md`).

> ⚠️ **DADOS SENSÍVEIS — NÃO COMMITAR NO REPOSITÓRIO FINEMP (é público).** `prototipo-completo.html`, `fonte/prototipo-dados.js`, `fonte/prototipo-app.js` e `capturas/*.png` contêm dados reais de clientes (razão contábil, nomes de sócios, CPFs parciais, CNPJs, funcionários). Usar só como referência local. No repositório entram apenas componentes reescritos, CSS e este `.md`. Não há token, credencial ou chave de API no pacote.

## 0. Conteúdo do pacote

| Arquivo | O que é |
|---|---|
| `prototipo-completo.html` | **HTML único atual, completo e sem alteração** (≈10 MB). Abre direto no navegador; fora do claude.ai a gravação cai no `localStorage`. |
| `fonte/prototipo-split.html` | O mesmo HTML com CSS/JS extraídos para arquivos (mesma ordem, sem mudança de código). Servir por HTTP: `python3 -m http.server` na pasta `fonte/` e abrir `prototipo-split.html`. Conferido: as 39 telas contábeis renderizam sem erro de console. |
| `fonte/prototipo-wrapper.css` | Bloco `<style>` 1 (372 B): reset do wrapper. |
| `fonte/prototipo.css` | Bloco `<style>` 2 (127 KB, 1155 linhas): **todo o CSS do app**. |
| `fonte/prototipo-dados.js` | Script 1: `DADOS_GRUPO`, `DADOS_RH`, `PUBLICADO_EM`. **Sensível.** Só dados. |
| `fonte/prototipo-app.js` | Script 2 (1,87 MB, ≈12 100 linhas): **todo o JavaScript do app** (render, eventos, menu, modais). Contém constantes com dados de cliente (`SOC`, `VOL_DATA`, `CMP_PARES`, `GP_DATA`). |
| `assets/logo-embutido.png` | Logo FINEMP (360×168) que no HTML está em `data:image/png` no `<img id="logo">`. |
| `navegacao/menu-completo.json` | Constante `MENU` inteira (todas as áreas). |
| `navegacao/menu-contabil.json` | Recorte da área "Contabilidade (equipe Finemp)": grupos → subgrupos → abas. |
| `navegacao/tutorial-por-tela.json` | Texto de tutorial (`TUT_DESC`) de cada aba contábil. |
| `capturas/desktop-{código}.png` | Página inteira a 1440 px de cada uma das 39 telas. |
| `capturas/mobile-{código}.png` | 390×844 (cfcock, cfdocs, cflanc, cfpre, checklist, cfcong, depara, gcbs, sucesso) + `mobile-menu-aberto.png`. |
| `docs/HANDOFF_CONTABIL.md` | Handoff funcional (regras, campos, cálculos). Fonte do "comportamento" abaixo. |
| `docs/accounting-area-map.md` | Mapa TELA → rota → API → Funcional/Em breve do shell React. |
| `docs/*.patch` | Shell React (`0001-feat-frontend-accounting-area-shell.patch`) e M1 (`M1-accounting-portfolio-access.patch`), ainda não aplicados no GitHub. |

Os números de linha citados abaixo são de `fonte/prototipo-app.js` (JS), `fonte/prototipo.css` (CSS) e `fonte/prototipo-split.html` (HTML).

## 1. Arquitetura da página (shell)

```
div.app#app
├─ button.side-tog#sideTog          (hambúrguer: recolhe/abre o menu; no mobile abre gaveta)
├─ div.side-bg#sideBg               (fundo escuro da gaveta no mobile)
├─ aside.side#side                  (menu lateral)
│   ├─ .side-tit "Menu"
│   ├─ input.menu-busca#menuBusca   (busca de aba; menuNorm/menuFiltrar/menuBuscaLigar, JS 12068–12079)
│   ├─ nav.tabs#tabs[role=tablist]  (montado por menuMontar, JS 3617, a partir de MENU, JS 3544)
│   └─ .side-foot                   (status de lançamentos manuais, botão "+ Novo lançamento" – oculto)
└─ div.main-col > div.wrap
    ├─ div.hdr-fixo#hdrFixo         (cabeçalho compacto e fixo, HTML l.23; CSS 1035–1072)
    │   ├─ header.top > .brand      (img#logo, .who: #hGrupo eyebrow, h1#hCliente, .meta#hMeta; #cliLogo; button.hdr-ico#btnFiltros)
    │   ├─ hr.rule                  (linha dourada)
    │   ├─ .pg-row (HTML l.40)      (h2.pg-tit#pgTit · nav.hdr-nav#hdrNav · .tut-bts: Imprimir #btnImpAba, Excel #btnXlsAba, Tutorial)
    │   └─ .legend#legend
    └─ main > section#p-{código}    (uma section por aba; só a ativa visível)
modais (fim do body, HTML l.1150–1293)
```

### 1.1 Navegação

- **Fonte da estrutura:** `MENU` (JS 3544) → áreas → grupos (`nav-g`, cabeçalho `nav-gh`, `data-grp` → menuMontar (JS 3617)) → subgrupos (`nav-sg`/`nav-sgh`, `data-sgrp` → menuMontar (JS 3617)) → itens (`nav-items`, botão `.tab` com `data-tab="{código}"`). Separadores `nav-sep`. Ícones SVG por grupo em `ICONES` (JS 3595).
- **Marcação ativa:** `menuMarcar` (JS 3632) marca a aba, abre o grupo/subgrupo dela e fecha os demais.
- **Troca de aba:** `mostrarAba(id)` (JS 704) esconde todas as `section`, mostra `#p-{id}`, atualiza `#pgTit`, `#hdrNav`, legenda e chama `renderAba` (JS 715), que despacha para a função de render da tela (coluna "Render" abaixo), sempre embrulhada em `seguro(fn, idContainer)` (JS 3673): em caso de exceção, o container mostra a mensagem de erro em vez de quebrar a página.
- **Submenu no cabeçalho (`#hdrNav`)**: `hdrNav`/`hdrNavLigar` (JS 8040/8050). Mostra em pílulas as abas do mesmo subgrupo (`hn-p`) e os subgrupos irmãos (`hn-s`), para navegar sem abrir o menu. CSS 1060–1072.
- **Busca no menu:** filtra por nome sem acento (`menuNorm`), abre os grupos que têm resultado.
- **Mobile (≤ 900 px):** o menu vira gaveta sobre o conteúdo com `#sideBg`; `#hdrNav` rola na horizontal. Ver `capturas/mobile-menu-aberto.png`.
- **Cabeçalho:** `hdrLigar` (JS 8037) – compacta ao rolar.
- **Empresa e período:** botão funil `#btnFiltros` abre `#mFiltros` (HTML 1203): Empresa `#fEmp`, De `#fDe`, Até `#fAte`, Visão dos custos `#fVisao`, valores em R$/R$ mil. Vale para todas as abas. `usarEmpresa` (JS 7), `aplicarFiltros` (JS 85). Telas da Central usam a **competência** da barra `cf-bar`; telas do Pacote usam o **mês ATÉ** do filtro.
- **Imprimir / Excel da aba:** `imprimirAba` (JS 4979, A4 paisagem via `@media print`) e `exportarAbaXlsx` (JS 5001; uma planilha "Informações" + uma por tabela, biblioteca xlsx).
- **Tutorial:** botão abre o texto de `TUT_DESC` (JS 10081) da aba; FAQ em `FAQ` (JS 10222).

### 1.2 Hierarquia do menu contábil (de `navegacao/menu-contabil.json`)

**Contabilidade (equipe Finemp)** (separador `nav-sep`)

- **Central de Fechamento** (grupo `nav-g`, ícone `fech`)
  - Rotina do mês (subgrupo `nav-sg`)
    - `cfcock` — Cockpit de Fechamento
    - `cfrot` — Roteiro do Analista
    - `checklist` — Checklist de Fechamento
    - `cfdocs` — Documentos e Classificação
    - `cfbase` — Base de Classificação
    - `cfrec` — Lançamentos Recorrentes e Planilhas
    - `cfantecip` — Despesas Antecipadas
    - `cfadiant` — Adiantamentos (Fornecedores e Clientes)
    - `cfpre` — Pré-fechamento (Balancete e DRE)
    - `cflanc` — Lotes e Exportação Questor
  - Fiscal (subgrupo `nav-sg`)
    - `cflalur` — IRPJ e CSLL · Lalur (Lucro Real)
    - `cfpres` — Validador de Lucro Presumido
    - `cfimp` — Validador de ISS, PIS e COFINS
  - Folha (subgrupo `nav-sg`)
    - `cffolha` — Validador e Conciliador da Folha
  - Qualidade da base (subgrupo `nav-sg`)
    - `cfcong` — Períodos Congelados
    - `diag` — Diagnóstico das Bases
    - `depara` — De-Para do Plano de Contas
    - `cfvol` — Volumetria do Contrato
  - `cfcock` — Cockpit de Fechamento
  - `cfrot` — Roteiro do Analista
  - `checklist` — Checklist de Fechamento
  - `cfdocs` — Documentos e Classificação
  - `cfbase` — Base de Classificação
  - `cfrec` — Lançamentos Recorrentes e Planilhas
  - `cfantecip` — Despesas Antecipadas
  - `cfadiant` — Adiantamentos (Fornecedores e Clientes)
  - `cfpre` — Pré-fechamento (Balancete e DRE)
  - `cflanc` — Lotes e Exportação Questor
  - `cflalur` — IRPJ e CSLL · Lalur (Lucro Real)
  - `cfpres` — Validador de Lucro Presumido
  - `cfimp` — Validador de ISS, PIS e COFINS
  - `cffolha` — Validador e Conciliador da Folha
  - `cfcong` — Períodos Congelados
  - `diag` — Diagnóstico das Bases
  - `depara` — De-Para do Plano de Contas
  - `cfvol` — Volumetria do Contrato
- **Pacote de Consolidação** (grupo `nav-g`, ícone `fech`)
  - Demonstrações e movimentações (subgrupo `nav-sg`)
    - `gcmapa` — Estrutura e Mapa
    - `gcbs` — Balanço (BS)
    - `gcpl` — Resultado (PL)
    - `gcexps` — Custos por Natureza (EXPS)
    - `gcss` — Patrimônio Líquido (SS)
    - `gctfa` — Imobilizado (TFA)
    - `gpifa` — Intangível (IFA)
    - `gcbb` — Empréstimos (BB)
    - `gccf` — Fluxo de Caixa (CF)
    - `gpcfinfo` — Informações do Fluxo de Caixa (CF_INFO)
  - Informações complementares (subgrupo `nav-sg`)
    - `gpinvest` — Investimentos (INVEST)
    - `gplease` — Arrendamentos (LEASE)
    - `gppldtl` — Detalhe de Outros Resultados (PLDTL)
    - `gpmgt` — Perdas de Rentabilidade (MGT)
    - `gpexcng` — Câmbio (EXCNG)
    - `gpbonus` — Bônus (BONUS)
    - `gpinfo` — Informações da Empresa (INFO)
  - Intercompany (subgrupo `nav-sg`)
    - `gpint` — Intercompany (INT_BS / INT_PL)
  - Projeção e orçamento (subgrupo `nav-sg`)
    - `gpfcst` — Projeção (PL_F)
    - `gpbud` — Orçamento (PL_B / EXPS_B)
  - `gcmapa` — Estrutura e Mapa
  - `gcbs` — Balanço (BS)
  - `gcpl` — Resultado (PL)
  - `gcexps` — Custos por Natureza (EXPS)
  - `gcss` — Patrimônio Líquido (SS)
  - `gctfa` — Imobilizado (TFA)
  - `gpifa` — Intangível (IFA)
  - `gcbb` — Empréstimos (BB)
  - `gccf` — Fluxo de Caixa (CF)
  - `gpcfinfo` — Informações do Fluxo de Caixa (CF_INFO)
  - `gpinvest` — Investimentos (INVEST)
  - `gplease` — Arrendamentos (LEASE)
  - `gppldtl` — Detalhe de Outros Resultados (PLDTL)
  - `gpmgt` — Perdas de Rentabilidade (MGT)
  - `gpexcng` — Câmbio (EXCNG)
  - `gpbonus` — Bônus (BONUS)
  - `gpinfo` — Informações da Empresa (INFO)
  - `gpint` — Intercompany (INT_BS / INT_PL)
  - `gpfcst` — Projeção (PL_F)
  - `gpbud` — Orçamento (PL_B / EXPS_B)
- **Sucesso do Cliente** (grupo `nav-g`, ícone `ata`)
  - `sucesso` — Acompanhamento das Ações



## 2. Tabela por tela: código → nome → área do HTML → comportamento

Colunas: **Código** (= `data-tab` → sideLigar (JS 690), ligarEventos (JS 3460), menuMarcar (JS 3632), usrAplicar (JS 7906), hdrNavLigar (JS 8050), turPassosAba (JS 10310), turGeral (JS 10337), menuFiltrar (JS 12069), menuBuscaLigar (JS 12079), id da section `#p-{código}` e nome da rota no shell React) · **Nome** (rótulo exato do menu) · **Grupo / subgrupo** · **Área do HTML** (section + linha no split HTML, containers internos) · **Render** (função + linha no JS) · **Comportamento principal** (do `HANDOFF_CONTABIL.md`, seção indicada) · **Captura**.

### `cfcock` — Cockpit de Fechamento

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfcock` (split HTML l.266) · containers: `#cfcockSub`, `#cfcockBar`, `#cfcockCards`, `#cfcockPassos`
- **Render:** `renderCfCock` (JS 5274) — chamada em `renderAba`: `seguro(renderCfCock, 'cfcockCards');`
- **Títulos de bloco (h3):** Cockpit de Fechamento · Passos do fechamento
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfgo` → cfLigar (JS 5708)
- **Comportamento principal:** num lugar só, a situação do fechamento da competência para a empresa, com os passos e o que falta. _(HANDOFF: 4.1 Cockpit de Fechamento (`cfcock`))_
- **Captura:** `capturas/desktop-cfcock.png`, `capturas/mobile-cfcock.png`

### `cfrot` — Roteiro do Analista

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfrot` (split HTML l.385) · containers: `#cfrotSub`, `#cfrotBar`, `#cfrotTbl`
- **Render:** `renderCfRot` (JS 6448) — chamada em `renderAba`: `seguro(renderCfRot, 'cfrotTbl');`
- **Títulos de bloco (h3):** Roteiro do Analista
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-emgo` → emLigar (JS 6206); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379); `data-rot` → cf2Ligar (JS 6458)
- **Comportamento principal:** passo a passo do fechamento por competência, com responsável, prazo, situação, revisor e observação. _(HANDOFF: 4.2 Roteiro do Analista (`cfrot`))_
- **Captura:** `capturas/desktop-cfrot.png`

### `checklist` — Checklist de Fechamento

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-checklist` (split HTML l.1155) · containers: `#ckAno`, `#ckAutoOk`, `#ckResumo`, `#ckTbl`, `#ckElab`, `#ckElabEm`, `#ckRev`, `#ckRevEm`, `#supBox`, `#supTit`, `#supRes`, `#supDir`, `#supTbl`
- **Render:** `renderChecklist` (JS 11726) — chamada em `renderAba`: `seguro(renderChecklist, 'ckTbl');`
- **Títulos de bloco (h3):** Análise de fechamento contábil · Suportes dos saldos
- **Ações (atributos data-* → handler):** `data-ck` → ckLigar (JS 11758); `data-faqir` → turLigar (JS 10419); `data-id` → renderSocOrg (JS 1137), renderDupont (JS 2534), ataLigar (JS 2802), ligarEventos (JS 3460), emRotLigar (JS 6196), vdLigar (JS 8005), mpLigar (JS 8460), conselhoLigar (JS 9109), tpLigar (JS 9671), cdLigar (JS 11379), monLigar (JS 11504), ckLigar (JS 11758), supLigar (JS 11876); `data-m` → ligarEventos (JS 3460)
- **Estados vazios (texto exato):** “Sem detalhes automáticos para este item.”
- **Comportamento principal:** checklist mensal "Análise de fechamento contábil" com validação automática por item; anexação de suportes que comprovam cada saldo, com conciliação. **Quem utiliza [FATO]:** analista (elabora) e supervisor (revisa). _(HANDOFF: 4.3 Checklist de Fechamento + Suportes dos Saldos (`checklist`))_
- **Captura:** `capturas/desktop-checklist.png`, `capturas/mobile-checklist.png`

### `cfdocs` — Documentos e Classificação

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfdocs` (split HTML l.271) · containers: `#cfdocsSub`, `#cfdocsBar`, `#cfDrop`, `#cfUpload`, `#cfdocsTbl`
- **Render:** `renderCfDocs` (JS 5431) — chamada em `renderAba`: `seguro(renderCfDocs, 'cfdocsTbl');`
- **Títulos de bloco (h3):** Documentos e Classificação · Enviar documentos · Documentos
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfdc` → cfLigar (JS 5708); `data-cfdh` → cfLigar (JS 5708); `data-cfdl` → cfLigar (JS 5708); `data-cfdx` → cfLigar (JS 5708)
- **Estados vazios (texto exato):** “Nenhum documento enviado ainda.”
- **Comportamento principal:** receber documentos do mês, extrair os dados, sugerir a contabilização e gerar lançamentos preparados após a conferência do analista. _(HANDOFF: 4.4 Documentos e Classificação (`cfdocs`))_
- **Captura:** `capturas/desktop-cfdocs.png`, `capturas/mobile-cfdocs.png`

### `cfbase` — Base de Classificação

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfbase` (split HTML l.277) · containers: `#cfbaseSub`, `#cfbaseBar`, `#cfbaseQ`, `#cfbaseHist`, `#cfbaseReg`, `#cfbaseLanc`, `#cfbaseHistTpl`
- **Render:** `renderCfBase` (JS 5440) — chamada em `renderAba`: `seguro(renderCfBase, 'cfbaseHist');`
- **Títulos de bloco (h3):** Base de Classificação · Histórico por fornecedor (razão dos últimos 12 meses) · Regras aprendidas · Lançamentos classificados · Históricos padrão
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfhist` → cfLigar (JS 5708); `data-cfrgx` → cfLigar (JS 5708)
- **Estados vazios (texto exato):** “Nenhuma regra aprendida ainda. Elas são criadas ao aprovar documentos.” | “Nenhum lançamento classificado na Central ainda.” | “Um modelo por linha. Campos: {numero} {fornecedor} {nome} {descricao} {competencia} {parcela} {total} {periodo}.”
- **Comportamento principal:** mostrar a base que o sistema usa para classificar: o histórico do razão, as regras aprendidas e os lançamentos preparados. _(HANDOFF: 4.5 Base de Classificação (`cfbase`))_
- **Captura:** `capturas/desktop-cfbase.png`

### `cfrec` — Lançamentos Recorrentes e Planilhas

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfrec` (split HTML l.286) · containers: `#cfrecSub`, `#cfrecBar`, `#cfrecTbl`
- **Render:** `renderCfRec` (JS 5464) — chamada em `renderAba`: `seguro(renderCfRec, 'cfrecTbl');`
- **Títulos de bloco (h3):** Lançamentos Recorrentes e Planilhas · Modelos recorrentes · Planilhas de importação
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfrec` → cfLigar (JS 5708); `data-id` → renderSocOrg (JS 1137), renderDupont (JS 2534), ataLigar (JS 2802), ligarEventos (JS 3460), emRotLigar (JS 6196), vdLigar (JS 8005), mpLigar (JS 8460), conselhoLigar (JS 9109), tpLigar (JS 9671), cdLigar (JS 11379), monLigar (JS 11504), ckLigar (JS 11758), supLigar (JS 11876); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379); `data-rec` → cfRecSalvarLinha (JS 5472), cfLigar (JS 5708)
- **Comportamento principal:** cadastrar modelos de lançamentos que se repetem (seguros, IPTU, aluguel, honorários, licenças, provisões) e gerar o lote da competência sem duplicar. _(HANDOFF: 4.6 Lançamentos Recorrentes e Planilhas (`cfrec`))_
- **Captura:** `capturas/desktop-cfrec.png`

### `cfantecip` — Despesas Antecipadas

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfantecip` (split HTML l.297) · containers: `#cfantecipSub`, `#cfantecipBar`, `#cfantecipTbl`, `#cfantecipConc`
- **Render:** `renderCfAntecip` (JS 5503) — chamada em `renderAba`: `seguro(renderCfAntecip, 'cfantecipTbl');`
- **Títulos de bloco (h3):** Despesas Antecipadas · Itens e apropriação do mês · Conciliação com o balanço
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-ant` → cfAntSalvarLinha (JS 5518), cfLigar (JS 5708); `data-cfant` → cfLigar (JS 5708); `data-cfantsug` → cfLigar (JS 5708); `data-id` → renderSocOrg (JS 1137), renderDupont (JS 2534), ataLigar (JS 2802), ligarEventos (JS 3460), emRotLigar (JS 6196), vdLigar (JS 8005), mpLigar (JS 8460), conselhoLigar (JS 9109), tpLigar (JS 9671), cdLigar (JS 11379), monLigar (JS 11504), ckLigar (JS 11758), supLigar (JS 11876); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379)
- **Estados vazios (texto exato):** “Nenhum débito nas contas de despesas antecipadas nos últimos 12 meses fora dos cadastros.”
- **Comportamento principal:** controlar despesas pagas antes do consumo, gerar a apropriação mensal e conciliar com o saldo do balanço. _(HANDOFF: 4.7 Despesas Antecipadas (`cfantecip`))_
- **Captura:** `capturas/desktop-cfantecip.png`

### `cfadiant` — Adiantamentos (Fornecedores e Clientes)

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfadiant` (split HTML l.303) · containers: `#cfadiantSub`, `#cfadiantBar`, `#cfadiantSug`, `#cfadiantDet`
- **Render:** `renderCfAdiant` (JS 5540) — chamada em `renderAba`: `seguro(renderCfAdiant, 'cfadiantDet');`
- **Títulos de bloco (h3):** Adiantamentos a Fornecedores e de Clientes · Adiantamentos que podem ser baixados · Saldos por fornecedor e cliente
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfadv` → cfLigar (JS 5708)
- **Estados vazios (texto exato):** “Nenhum adiantamento em aberto com nota ou fatura já lançada para baixar.” | “Nenhum saldo em aberto identificado.”
- **Comportamento principal:** identificar adiantamentos em aberto que já têm nota (fornecedor) ou fatura (cliente) posterior e sugerir a baixa. _(HANDOFF: 4.8 Adiantamentos a Fornecedores e de Clientes (`cfadiant`))_
- **Captura:** `capturas/desktop-cfadiant.png`

### `cfpre` — Pré-fechamento (Balancete e DRE)

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cfpre` (split HTML l.316) · containers: `#cfpreSub`, `#cfpreBar`, `#cfpreAl`, `#cfpreDre`, `#cfpreBal`
- **Render:** `renderCfPre` (JS 5573) — chamada em `renderAba`: `seguro(renderCfPre, 'cfpreAl');`
- **Títulos de bloco (h3):** Pré-fechamento (Balancete e DRE) · Alertas e recomendações · DRE de pré-fechamento · Balancete de pré-fechamento
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Comportamento principal:** ver o balancete e a DRE da competência **somando** a base do Questor e os lançamentos preparados que ainda vão ser importados, antes de importar. Mostra alertas de linearidade e de natureza. _(HANDOFF: 4.9 Pré-fechamento (Balancete e DRE) (`cfpre`))_
- **Captura:** `capturas/desktop-cfpre.png`, `capturas/mobile-cfpre.png`

### `cflanc` — Lotes e Exportação Questor

- **Grupo / subgrupo:** Central de Fechamento › Rotina do mês
- **Área do HTML:** `section#p-cflanc` (split HTML l.323) · containers: `#cflancSub`, `#cflancBar`, `#cflancTbl`, `#cflancAcoes`, `#cflancCfg`
- **Render:** `renderCfLanc` (JS 5685) — chamada em `renderAba`: `seguro(renderCfLanc, 'cflancTbl');`
- **Títulos de bloco (h3):** Lotes e Exportação Questor · Lançamentos preparados · Centro de custo e layouts do Questor
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cfl` → cfLigar (JS 5708); `data-cflf` → cfLigar (JS 5708); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379); `data-lay` → cfLigar (JS 5708); `data-lc` → cfLancSalvarLinha (JS 5701), cfLigar (JS 5708)
- **Comportamento principal:** revisar, aprovar e exportar para o Questor, em arquivo texto, todos os lançamentos preparados da competência. _(HANDOFF: 4.10 Lotes e Exportação Questor (`cflanc`))_
- **Captura:** `capturas/desktop-cflanc.png`, `capturas/mobile-cflanc.png`

### `cflalur` — IRPJ e CSLL · Lalur (Lucro Real)

- **Grupo / subgrupo:** Central de Fechamento › Fiscal
- **Área do HTML:** `section#p-cflalur` (split HTML l.309) · containers: `#cflalurSub`, `#cflalurBar`, `#cflalurAj`, `#cflalurRes`, `#cflalurB`
- **Render:** `renderCfLalur` (JS 5632) — chamada em `renderAba`: `seguro(renderCfLalur, 'cflalurAj');`
- **Títulos de bloco (h3):** IRPJ e CSLL · Lalur · Adições e exclusões · Cálculo · Parte B · controle de valores para períodos futuros
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-cflal` → cfLigar (JS 5708); `data-cflt` → cfLigar (JS 5708); `data-cfpb` → cfLigar (JS 5708); `data-i` → cfLigar (JS 5708), kevLigar (JS 6551), indLigar (JS 7378), vdLigar (JS 8005), imInfoLigar (JS 8032), conselhoLigar (JS 9109), gobTabela (JS 9743), rgLigar (JS 12045); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379); `data-pb` → cfLigar (JS 5708)
- **Comportamento principal:** apuração **trimestral** do IRPJ e da CSLL no **Lucro Real**: Lalur e Lacs com adições e exclusões, compensação de prejuízo, Parte B e lançamentos de ajuste da provisão. _(HANDOFF: 5.1 IRPJ e CSLL · Lalur (`cflalur`))_
- **Captura:** `capturas/desktop-cflalur.png`

### `cfpres` — Validador de Lucro Presumido

- **Grupo / subgrupo:** Central de Fechamento › Fiscal
- **Área do HTML:** `section#p-cfpres` (split HTML l.389) · containers: `#cfpresSub`, `#cfpresBar`, `#cfpresAl`, `#cfpresRes`, `#cfpresAtiv`
- **Render:** `renderCfPres` (JS 6280) — chamada em `renderAba`: `seguro(renderCfPres, 'cfpresRes');`
- **Títulos de bloco (h3):** Validador de Lucro Presumido · Erros e pontos de atenção · Recálculo do trimestre · Atividades e percentuais
- **Etiqueta de bloco (`ind-tag`):** Central · Fiscal
- **Ações (atributos data-* → handler):** `data-cffis` → cf2Ligar (JS 6458); `data-cffistri` → cf2Ligar (JS 6458); `data-cfpres` → cf2Ligar (JS 6458); `data-cfpresv` → cf2Ligar (JS 6458); `data-cs`; `data-ir` → conselhoLigar (JS 9109); `data-per`
- **Comportamento principal:** recalcular o IRPJ e a CSLL do Lucro Presumido do trimestre pela receita contabilizada e comparar com o contabilizado e o declarado. Diagnostica o que ficou de fora. _(HANDOFF: 5.2 Validador de Lucro Presumido (`cfpres`))_
- **Captura:** `capturas/desktop-cfpres.png`

### `cfimp` — Validador de ISS, PIS e COFINS

- **Grupo / subgrupo:** Central de Fechamento › Fiscal
- **Área do HTML:** `section#p-cfimp` (split HTML l.395) · containers: `#cfimpSub`, `#cfimpBar`, `#cfimpAl`, `#cfimpTbl`, `#cfimpAtiv`
- **Render:** `renderCfImp` (JS 6333) — chamada em `renderAba`: `seguro(renderCfImp, 'cfimpTbl');`
- **Títulos de bloco (h3):** Validador de ISS, PIS e COFINS · Erros e pontos de atenção · Apuração mês a mês · Atividades e alíquotas
- **Etiqueta de bloco (`ind-tag`):** Central · Fiscal
- **Ações (atributos data-* → handler):** `data-cffis` → cf2Ligar (JS 6458); `data-cfimp` → cfLigar (JS 5708); `data-cfimpacao` → cf2Ligar (JS 6458); `data-cfimpv` → cf2Ligar (JS 6458); `data-cfmod` → cfLigar (JS 5708)
- **Comportamento principal:** recalcular mês a mês o PIS, a COFINS e o ISS pela receita contabilizada e confrontar com o contabilizado nas deduções e com o declarado (EFD-Contribuições, DCTF, município). _(HANDOFF: 5.3 Validador de ISS, PIS e COFINS (`cfimp`))_
- **Captura:** `capturas/desktop-cfimp.png`

### `cffolha` — Validador e Conciliador da Folha

- **Grupo / subgrupo:** Central de Fechamento › Folha
- **Área do HTML:** `section#p-cffolha` (split HTML l.401) · containers: `#cffolhaSub`, `#cffolhaBar`, `#cfFlDrop`, `#cfFlUpload`, `#cffolhaArq`, `#cffolhaVal`, `#cffolhaAl`, `#cffolhaBal`, `#cffolhaDre`
- **Render:** `renderCfFolha` (JS 6379) — chamada em `renderAba`: `seguro(renderCfFolha, 'cffolhaBal');`
- **Títulos de bloco (h3):** Validador e Conciliador da Folha · Relatórios da folha · Totais da folha (confira) · Testes e alertas · Contas de ativo e passivo × folha · Despesas de pessoal × folha
- **Etiqueta de bloco (`ind-tag`):** Central · Folha
- **Ações (atributos data-* → handler):** `data-a` → setupAC (JS 2862); `data-cffl` → cf2Ligar (JS 6458); `data-flv` → cf2Ligar (JS 6458); `data-k` → ligarEventos (JS 3460), cfLerLinhas (JS 5418), cfRecSalvarLinha (JS 5472), cfAntSalvarLinha (JS 5518), cfLancSalvarLinha (JS 5701), cfLigar (JS 5708), cf2Ligar (JS 6458), tbElegivel (JS 7798), tpLigar (JS 9671), turPassosAba (JS 10310), cdLigar (JS 11379); `data-v` → cdLigar (JS 11379)
- **Estados vazios (texto exato):** “Nenhum relatório enviado para esta competência.” | “Informe ou suba o relatório da folha para ver os testes.”
- **Comportamento principal:** a partir dos relatórios da folha da competência, cruzar os totais com **todas as contas de ativo e passivo da folha** e com as despesas de pessoal, apontar diferenças e gerar o lançamento de ajuste. _(HANDOFF: 5.4 Validador e Conciliador da Folha (`cffolha`))_
- **Captura:** `capturas/desktop-cffolha.png`

### `cfcong` — Períodos Congelados

- **Grupo / subgrupo:** Central de Fechamento › Qualidade da base
- **Área do HTML:** `section#p-cfcong` (split HTML l.449) · containers: `#cgSub`, `#cgAcoes`, `#cgMeses`, `#cgDif`, `#cgLog`
- **Render:** `renderCong` (JS 7856) — chamada em `renderAba`: `seguro(renderCong, 'cgDif');`
- **Títulos de bloco (h3):** Períodos Congelados · Alterações em meses congelados · Registro (quem, quando e por quê)
- **Etiqueta de bloco (`ind-tag`):** Fechamento
- **Ações (atributos data-* → handler):** `data-cg` → congLigar (JS 7872)
- **Estados vazios (texto exato):** “Nenhum período congelado.” | “Sem registros.”
- **Comportamento principal:** proteger meses já fechados. Guarda uma fotografia do movimento de cada conta. Quando a base for reimportada ou reprocessada, lista as mudanças nesses meses, que só viram oficiais depois de **aceitas com motivo**. _(HANDOFF: 6.1 Períodos Congelados (`cfcong`))_
- **Captura:** `capturas/desktop-cfcong.png`, `capturas/mobile-cfcong.png`

### `diag` — Diagnóstico das Bases

- **Grupo / subgrupo:** Central de Fechamento › Qualidade da base
- **Área do HTML:** `section#p-diag` (split HTML l.653) · containers: `#sevsum2`, `#dTbl`, `#okList`
- **Render:** `renderDiagnostico` (JS 3380) — chamada em `renderAba`: `renderDiagnostico();`
- **Títulos de bloco (h3):** Inconsistências · Verificações aprovadas
- **Comportamento principal:** verificar a integridade da base importada no período do filtro. _(HANDOFF: 6.2 Diagnóstico das Bases (`diag`))_
- **Captura:** `capturas/desktop-diag.png`

### `depara` — De-Para do Plano de Contas

- **Grupo / subgrupo:** Central de Fechamento › Qualidade da base
- **Área do HTML:** `section#p-depara` (split HTML l.1083) · containers: `#dpCards`, `#dpFiltro`, `#dpBusca`, `#dpConfTodas`, `#dpSub`, `#dpTbl`, `#dpRegras`
- **Render:** `renderDepara` (JS 10843) — chamada em `renderAba`: `seguro(renderDepara, 'dpTbl');`
- **Títulos de bloco (h3):** Classificação das contas · Regras de-para ativas
- **Ações (atributos data-* → handler):** `data-a` → setupAC (JS 2862); `data-dp` → dpLigar (JS 10882); `data-p` → rhgLigar (JS 4382), socLigar (JS 1233)
- **Estados vazios (texto exato):** “Nenhuma regra ainda. Classifique uma conta acima para criar a primeira.”
- **Comportamento principal:** garantir que toda conta do Questor tenha uma classificação gerencial: _(HANDOFF: 6.3 De-Para do Plano de Contas (`depara`))_
- **Captura:** `capturas/desktop-depara.png`, `capturas/mobile-depara.png`

### `cfvol` — Volumetria do Contrato

- **Grupo / subgrupo:** Central de Fechamento › Qualidade da base
- **Área do HTML:** `section#p-cfvol` (split HTML l.411) · containers: `#cfvolSub`, `#cfvolBar`, `#cfvolCards`, `#cfvolTbl`, `#chVolL`, `#chVolF`, `#cfvolReaj`
- **Render:** `renderCfVol` (JS 6569) — chamada em `renderAba`: `seguro(renderCfVol, 'cfvolTbl');`
- **Títulos de bloco (h3):** Volumetria do Contrato · Ano a ano · Lançamentos por mês · Funcionários na folha por mês · Reajuste de honorários por volume
- **Etiqueta de bloco (`ind-tag`):** Central de Fechamento
- **Ações (atributos data-* → handler):** `data-vol` → cfVolLigar (JS 6601)
- **Estados vazios (texto exato):** “Ajuste o peso e a franquia conforme a cláusula do contrato. Sem aumento de volume (índice negativo) não há reajuste por esta regra.”
- **Comportamento principal:** medir o volume de trabalho do escritório para o cliente (funcionários processados na folha e lançamentos contábeis) e apoiar o reajuste de honorários. _(HANDOFF: 6.4 Volumetria do Contrato (`cfvol`))_
- **Captura:** `capturas/desktop-cfvol.png`

### `gcmapa` — Estrutura e Mapa

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcmapa` (split HTML l.176) · containers: `#gcMapaSub`, `#gcMapaTbl`, `#gcMapaRegras`
- **Render:** `renderGcMapa` (JS 4967) — chamada em `renderAba`: `seguro(renderGcMapa, 'gcMapaTbl');`
- **Títulos de bloco (h3):** Estrutura do pacote e mapa das folhas · Folhas do pacote: o que foi criado e o que ficou de fora
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil
- **Comportamento principal:** Lista das folhas do modelo. Colunas: Folha do pacote, Conteúdo, Situação (criada / não criada), Onde ver, Observação. Dados: Estático. Folhas não criadas: as vazias no modelo, as de controle e as sem dado disponível. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcmapa.png`

### `gcbs` — Balanço (BS)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcbs` (split HTML l.180) · containers: `#gcBsSub`, `#gcBsChk`, `#gcBsTbl`
- **Render:** `renderGcBs` (JS 4735) — chamada em `renderAba`: `seguro(renderGcBs, 'gcBsTbl');`
- **Títulos de bloco (h3):** Balanço patrimonial
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · BS
- **Comportamento principal:** Balanço mensal com os códigos do pacote e drilldown. Conferência: Conferência ativo = passivo + PL por mês. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcbs.png`, `capturas/mobile-gcbs.png`

### `gcpl` — Resultado (PL)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcpl` (split HTML l.184) · containers: `#gcPlSub`, `#gcPlChk`, `#gcPlTbl`
- **Render:** `renderGcPl` (JS 4781) — chamada em `renderAba`: `seguro(renderGcPl, 'gcPlTbl');`
- **Títulos de bloco (h3):** Demonstração do resultado
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · PL
- **Comportamento principal:** Receita, custo (com depreciação), margem, despesas por função, venda de imobilizado líquida em outros resultados operacionais, financeiro, IR e lucro. Conferência: Conferido com a DRE. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcpl.png`

### `gcexps` — Custos por Natureza (EXPS)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcexps` (split HTML l.188) · containers: `#gcExpsSub`, `#gcExpsTbl`, `#chGcExps`
- **Render:** `renderGcExps` (JS 4834) — chamada em `renderAba`: `seguro(renderGcExps, 'gcExpsTbl');`
- **Títulos de bloco (h3):** Custos e despesas por natureza · Maiores naturezas de gasto no ano
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · EXPS
- **Comportamento principal:** Custos e despesas por natureza: pessoal próprio, mão de obra externa, instalações, depreciação, TI, despesas gerais. Dados: Real, conforme o mapeamento por natureza. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcexps.png`

### `gcss` — Patrimônio Líquido (SS)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcss` (split HTML l.193) · containers: `#gcSsSub`, `#gcSsTbl`, `#gcSsNota`
- **Render:** `renderGcSs` (JS 4862) — chamada em `renderAba`: `seguro(renderGcSs, 'gcSsTbl');`
- **Títulos de bloco (h3):** Mutações do patrimônio líquido
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · SS
- **Ações (atributos data-* → handler):** `data-gc` → gcLigar (JS 4975)
- **Comportamento principal:** Mutações do PL: capital, AFAC, reservas e lucros acumulados. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcss.png`

### `gctfa` — Imobilizado (TFA)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gctfa` (split HTML l.197) · containers: `#gcTfaSub`, `#gcTfaCards`, `#gcTfaTbl`, `#gcGrossTbl`
- **Render:** `renderGcTfa` (JS 4874) — chamada em `renderAba`: `seguro(renderGcTfa, 'gcTfaTbl');`
- **Títulos de bloco (h3):** Imobilizado e intangível · Movimentação do valor líquido por classe (TFA) · Posição bruta: custo, depreciação acumulada e líquido (GROSS1)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · TFA / GROSS1
- **Ações (atributos data-* → handler):** `data-gc` → gcLigar (JS 4975)
- **Comportamento principal:** Imobilizado por classe do pacote, movimentação do valor líquido e posição bruta. Dados: Real. Os bens de locação foram classificados em "máquinas, equipamentos e bens de locação" (decisão de mapeamento do piloto). _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gctfa.png`

### `gpifa` — Intangível (IFA)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gpifa` (split HTML l.210) · containers: `#gpifaTit`, `#gpifaSub`, `#gpifaSel`, `#gpifaSo`, `#gpifaRes`, `#gpifaTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpifa'), 'gpifaTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Folha genérica: software e outros intangíveis. Conferência: Fórmulas do modelo. Dados: Real nas linhas com código. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpifa.png`

### `gcbb` — Empréstimos (BB)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gcbb` (split HTML l.202) · containers: `#gcBbSub`, `#gcBbTbl`
- **Render:** `renderGcBb` (JS 4895) — chamada em `renderAba`: `seguro(renderGcBb, 'gcBbTbl');`
- **Títulos de bloco (h3):** Empréstimos e financiamentos
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · BB
- **Comportamento principal:** Empréstimos de curto e longo prazo. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gcbb.png`

### `gccf` — Fluxo de Caixa (CF)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gccf` (split HTML l.206) · containers: `#gcCfSub`, `#gcCfTbl`
- **Render:** `renderGcCf` (JS 4904) — chamada em `renderAba`: `seguro(renderGcCf, 'gcCfTbl');`
- **Títulos de bloco (h3):** Fluxo de caixa (método indireto)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · CF
- **Comportamento principal:** Fluxo indireto mensal, montado das variações do balanço por linha do pacote. Conferência: Conferência com a variação do caixa = 0. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gccf.png`

### `gpcfinfo` — Informações do Fluxo de Caixa (CF_INFO)

- **Grupo / subgrupo:** Pacote de Consolidação › Demonstrações e movimentações
- **Área do HTML:** `section#p-gpcfinfo` (split HTML l.225) · containers: `#gpcfinfoTit`, `#gpcfinfoSub`, `#gpcfinfoSel`, `#gpcfinfoSo`, `#gpcfinfoRes`, `#gpcfinfoTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpcfinfo'), 'gpcfinfoTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Aplicações financeiras: aplicações, resgates e rendimentos. Conferência: Fórmulas do modelo. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpcfinfo.png`

### `gpinvest` — Investimentos (INVEST)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gpinvest` (split HTML l.220) · containers: `#gpinvestTit`, `#gpinvestSub`, `#gpinvestSel`, `#gpinvestSo`, `#gpinvestRes`, `#gpinvestTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpinvest'), 'gpinvestTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Aportes, equivalência, dividendos e baixas. Conferência: Fórmulas do modelo. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpinvest.png`

### `gplease` — Arrendamentos (LEASE)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gplease` (split HTML l.215) · containers: `#gpleaseTit`, `#gpleaseSub`, `#gpleaseSel`, `#gpleaseSo`, `#gpleaseRes`, `#gpleaseTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gplease'), 'gpleaseTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Folha genérica. Dados: **Só estrutura**: sem fonte de dados. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gplease.png`

### `gppldtl` — Detalhe de Outros Resultados (PLDTL)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gppldtl` (split HTML l.250) · containers: `#gppldtlSub`, `#gppldtlTbl`
- **Render:** `renderGpPldtl` (JS 5184) — chamada em `renderAba`: `seguro(renderGpPldtl, 'gppldtlTbl');`
- **Títulos de bloco (h3):** Detalhe de outros resultados (PLDTL)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Comportamento principal:** Detalhe por conta. Dados: Real. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gppldtl.png`

### `gpmgt` — Perdas de Rentabilidade (MGT)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gpmgt` (split HTML l.230) · containers: `#gpmgtTit`, `#gpmgtSub`, `#gpmgtSel`, `#gpmgtSo`, `#gpmgtRes`, `#gpmgtTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpmgt'), 'gpmgtTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Folha genérica. Dados: **Só estrutura** ou parcial. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpmgt.png`

### `gpexcng` — Câmbio (EXCNG)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gpexcng` (split HTML l.254) · containers: `#gpexcngSub`, `#gpexcngTbl`
- **Render:** `renderGpExcng` (JS 5165) — chamada em `renderAba`: `seguro(renderGpExcng, 'gpexcngTbl');`
- **Títulos de bloco (h3):** Câmbio e moeda estrangeira (EXCNG)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Comportamento principal:** Rótulos traduzidos. Dados: **Só estrutura** (sem operações em moeda estrangeira). _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpexcng.png`

### `gpbonus` — Bônus (BONUS)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gpbonus` (split HTML l.258) · containers: `#gpbonusSub`, `#gpbonusTbl`
- **Render:** `renderGpBonus` (JS 5172) — chamada em `renderAba`: `seguro(renderGpBonus, 'gpbonusTbl');`
- **Títulos de bloco (h3):** Bônus de empregados (BONUS1 / BONUS2)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Comportamento principal:** Colunas 1º tri, 1º sem, 3º tri, 2º sem. Dados: **Só estrutura.** _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpbonus.png`

### `gpinfo` — Informações da Empresa (INFO)

- **Grupo / subgrupo:** Pacote de Consolidação › Informações complementares
- **Área do HTML:** `section#p-gpinfo` (split HTML l.262) · containers: `#gpinfoSub`, `#gpinfoTbl`
- **Render:** `renderGpInfo` (JS 5178) — chamada em `renderAba`: `seguro(renderGpInfo, 'gpinfoTbl');`
- **Títulos de bloco (h3):** Informações da empresa (INFO)
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Comportamento principal:** Cadastro de contatos e responsáveis do pacote: empresa, CNPJ, país, moeda, representante legal, CFO, contador, tesouraria, aprovador, contato, endereço, telefone. Dados: CNPJ e responsáveis vêm da configuração da empresa; os demais campos ficam **"a preencher"**: **não há tela de edição**. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpinfo.png`

### `gpint` — Intercompany (INT_BS / INT_PL)

- **Grupo / subgrupo:** Pacote de Consolidação › Intercompany
- **Área do HTML:** `section#p-gpint` (split HTML l.235) · containers: `#gpintTit`, `#gpintSub`, `#gpintSel`, `#gpintSo`, `#gpintRes`, `#gpintTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpint'), 'gpintTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Seletor de folha. Conferência: Linhas "Inter Company" recebem as contas cujo **nome** identifica empresa do grupo ou sócio. Dados: Real, por **heurística de nome**. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpint.png`

### `gpfcst` — Projeção (PL_F)

- **Grupo / subgrupo:** Pacote de Consolidação › Projeção e orçamento
- **Área do HTML:** `section#p-gpfcst` (split HTML l.240) · containers: `#gpfcstTit`, `#gpfcstSub`, `#gpfcstSel`, `#gpfcstSo`, `#gpfcstRes`, `#gpfcstTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpfcst'), 'gpfcstTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Realizado nos meses fechados; meses futuros em branco. Dados: Meses futuros **vazios** (sem projeção). _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpfcst.png`

### `gpbud` — Orçamento (PL_B / EXPS_B)

- **Grupo / subgrupo:** Pacote de Consolidação › Projeção e orçamento
- **Área do HTML:** `section#p-gpbud` (split HTML l.245) · containers: `#gpbudTit`, `#gpbudSub`, `#gpbudSel`, `#gpbudSo`, `#gpbudRes`, `#gpbudTbl`
- **Render:** `renderGp` (JS 5141) — chamada em `renderAba`: `seguro(() => renderGp('gpbud'), 'gpbudTbl');`
- **Títulos de bloco (h3):** 
- **Etiqueta de bloco (`ind-tag`):** Gestão Contábil · ConsA
- **Ações (atributos data-* → handler):** `data-gps` → gpLigar (JS 5195)
- **Comportamento principal:** Folha genérica. Dados: **Só estrutura.** _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-gpbud.png`

### `sucesso` — Acompanhamento das Ações

- **Grupo / subgrupo:** Sucesso do Cliente › 
- **Área do HTML:** `section#p-sucesso` (split HTML l.459) · containers: `#scSub`, `#scCards`, `#scResp`, `#scFiltros`, `#scTbl`
- **Render:** `renderSucesso` (JS 7953) — chamada em `renderAba`: `seguro(renderSucesso, 'scTbl');`
- **Títulos de bloco (h3):** Acompanhamento das Ações · Por responsável
- **Etiqueta de bloco (`ind-tag`):** Sucesso do Cliente
- **Ações (atributos data-* → handler):** `data-scf` → usrLigar (JS 7967); `data-scmail` → usrLigar (JS 7967); `data-scn` → usrLigar (JS 7967); `data-scresp` → usrLigar (JS 7967)
- **Estados vazios (texto exato):** “Nenhuma ação no filtro.” | “Nenhuma ação em aberto.”
- **Comportamento principal:** acompanhar e cobrar as ações em aberto da Ata de Decisões, com resumo por responsável, registro de andamento e cobrança por e-mail. _(HANDOFF §7/§8)_
- **Captura:** `capturas/desktop-sucesso.png`, `capturas/mobile-sucesso.png`
## 3. Catálogo de componentes e estados visuais

Todos os componentes são **strings HTML** geradas no JS e injetadas nos containers da section. No React, cada um vira componente com as **mesmas classes CSS** (portar `prototipo.css` sem renomear classes é o caminho mais fiel).

| Componente | Classe(s) CSS (linha) | Gerado por (JS) | Uso / estados |
|---|---|---|---|
| Painel / bloco | `.panel` (CSS 72), `.sub` (74) | inline nos render | Caixa com borda e fundo `--panel`; `h3` + `.sub` (subtítulo cinza). |
| Bloco temático | `.ind-bloco.emp`, `.ind-head`, `.ind-tag` (CSS 277–307) | render das telas | Bloco com etiqueta dourada (`ind-tag`, ex.: "Central de Fechamento"). |
| Card de indicador | `.card.cxc` + `.al-ok/.al-warn/.al-bad/.al-info` + tamanho `big/md/sm` (CSS 386–421); grades `.cx-grid.g-big` (4 col), `.g-md` (3), `.g-sm` (6) | `rhCard(al, tema, lab, val, vr, nota, tam)` (JS 4031) | Borda esquerda colorida pelo alerta; `.cx-tema` (caixa alta), `.lab`, `.val`, `.var` (variação), nota. `al-bad` pinta o valor de coral; `big` tem gradiente dourado. Ex.: `#cfcockCards`. |
| Badge de situação | `.stc.ok/.warn/.bad/.info` (CSS 461–466) | `chip(al, txt)` (JS 3672), mapa `STC` (JS 3671: ok=Bom, warn=Regular, bad=Ruim, info=—) | Pílula com borda e fundo translúcido: teal / dourado / coral / azul. |
| Selo ABC | `.abc.A/.B/.C` (CSS 467–468) | — | Quadrado 22px. |
| Alertas | `.so-al`, `.so-als` (CSS 328–331); `.warnbar` (CSS 271) | render | Lista de alertas com cor por severidade; faixa de aviso dourada. |
| Severidade (diagnóstico) | `.sev`, `.sevsum`, `.oklist` (CSS 233–241) | `renderDiagnostico` (JS 3380) | Resumo por severidade e lista de itens OK. |
| Barra de competência | `.cf-bar` (CSS 886+) | `cfBar(id, extra)` (JS 5235) | `input type=month` "Competência em fechamento" (`data-cfbar`) + ações + "Base contábil até mmm/aa". Em todas as telas da Central. |
| Passos do fechamento | `.cf-passos`, `.cf-passo` (CSS 886–908) | `renderCfCock` (JS 5274) | Lista numerada com situação e link `data-cfgo` → cfLigar (JS 5708) para a tela do passo. |
| Linha do tempo / upload | `.cf-tl`, `.cf-drop`, `.cf-lays`, `.filebox` | `cfUpload` (JS 5315), `cfLerDoc` (5344) | Área de soltar arquivos (tracejada), layouts por documento. `.spin` durante leitura. |
| Tabela padrão | `.tbl-wrap` + `table` (CSS 87–232) | render | Rola dentro da caixa, cabeçalho fixo, barras de rolagem douradas sempre visíveis; números em `.num` (JetBrains Mono, tabular); negativos `.neg` (coral). |
| Tabela DRE / pacote | `table.dre`, `.gc`, `.vr-*` (CSS 97–232, 864–884) | `renderGcBs` (JS 4735), `renderGp` (5141) | Linhas de total, subtotal e grupo; colunas por mês; recuo por nível. |
| Ordenar / filtrar | `.tb-sort`, `.tb-fbar` (CSS 1023–1024) | `tbDecorar` (JS 7813), `tbLigar` (7824) | Clique no cabeçalho ordena A–Z/Z–A; tabelas com ≥ 8 linhas ganham "Filtrar esta tabela…". Exceto árvore e marcadas. |
| Drilldown | `.dd*` (CSS 964–1001), lupa `.it-lupa` (1007, 1032, 1109) | `ddDecorar` (JS 6693), `ddAbrirRaiz` (6740), lista de exclusão `DD_SKIP` (6604) | Lupa em rótulos de conta/categoria/grupo → modal `#mDd`: contas, lançamentos, resumo por contrapartida. |
| Checklist / suportes | `.ck-*` (CSS 789–805), `.sup-*` (806–815) | `renderChecklist` (JS 11726), `CK_ITENS` (11527), `ckValidar` (11606), `SUP_CAT` (11785) | Tabela `ck-t` com situação por item; suportes por conta abrem `#mSup`. |
| De-para | `.dp-*` (CSS 677–685, 754–788) | `renderDepara` (JS 10843), `dpAplicar` (10754) | Tabela `dp-t` com conta Questor → classificação gerencial; sem classificação em destaque. |
| Volumetria | `.vd-*` (CSS 1026–1029) | `renderCfVol` (JS 6569) | Cards + tabela mensal. |
| Congelamento | `.cg-*` (CSS 1025) | `renderCong` (JS 7856), `congChecar` (7866), `congLigar` (7872) | Lista de meses congelados e diferenças a aceitar com motivo. |
| Pílulas (seletor de folha, abas) | `.pill`, `.pill.on` (CSS 39–41, 125–131) | `GP_TABS` (JS 5130) | Seletor de folha no Pacote + "Só linhas com valor". |
| Botões | `.btn`, `.btn.primary`, `.btn.sq`, `.icon-btn` (CSS 46–52), `.tut-btn` | — | Primário dourado; `sq` compacto. |
| Campos | `.search` (CSS 228), `.fld`, `.form`, `.ac-list` (autocompletar) (CSS 253–266) | — | Fundo `--field`, borda `--line2`; erro `.fld.bad` + `.err`. |
| Ícone de informação | `.it-lupa`/ícone ⓘ (CSS 442–449) | abre `#mInfo` | Explicação do indicador. |
| Toast | `.toasts`, `.toast`, `.toast.ok/.err` (CSS 268–270) | `toast(msg, kind)` (JS 575) | Canto inferior direito, borda esquerda colorida. |
| Estado vazio | `.empty` (CSS 231–232) | inline; `seguro()` (JS 3673) usa para erro | Centralizado, cinza: "Não foi possível calcular: …" em falha; textos por tela na §2. |
| Legenda | `.legend#legend` (CSS 62–63, 441, 1054–1057) | `mostrarAba` | Linha abaixo do título da aba. |

## 4. Modais (HTML l.1150–1293)

Abrir/fechar: `openModal(id)` (JS 583, foca o primeiro campo) / `closeModal(id)` (JS 584). Estrutura comum: `div.modal[hidden][role=dialog][aria-modal=true]` > `div.dlg` (ou `.dlg.sm`, `.dlg.vr-dlg` largo) > título `h2` + corpo + `.actions`. Fundo `rgba(0,8,34,.78)`, caixa `--panel` com borda dourada e raio 16px.

| Id | Título padrão | Uso no módulo contábil |
|---|---|---|
| `mCf` | (dinâmico) | Formulários da Central: documento, recorrente, antecipada, Lalur, regras, configuração (`cfAbrirDoc` JS 5401 etc.). Largo (`vr-dlg`). |
| `mDd` | Composição | Drilldown universal (contas → lançamentos → contrapartida). |
| `mVar` | Lançamentos | Lista de lançamentos de uma linha/variação. |
| `mRc` | Reclassificação gerencial | Reclassificação de conta (de-para). |
| `mSup` | Suporte | Suporte do saldo (checklist). |
| `mFiltros` | Empresa e período | Empresa, De, Até, visão dos custos, R$/R$ mil. Global. |
| `mLanc` | (lançamento) | Lançamento manual (fora do contábil; existe no HTML). |
| `mAta` | (ação) | Ação da Ata, usada por Sucesso do Cliente. |
| `mConf` | (confirmação) | Confirmar/Cancelar (`mConfNo`/`mConfYes`) antes de ações destrutivas. |
| `mExp` | (exportação) | Escolha de empresa para exportar. |
| `mImp` | (importação) | Resultado de importação. |
| `mInfo` | (informação) | Explicação de indicador / ajuda. |
| `mRisco` | (risco) | Fora do contábil. |

## 5. Tema, tipografia e tokens (CSS 1–26)

```css
:root{
  color-scheme:dark;
  --bg:#001044; --panel:#011C69; --field:#0A2A7E; --line:#21408f; --line2:#2b4fa8;
  --fg:#ffffff; --muted:#aab7dc; --gold:#F0B323; --navy:#001044;
  --teal:#2EC4B6; --coral:#FF6B6B; --sky:#5DADEC; --lilac:#B39DDB;
  --good:#2EC4B6; --bad:#FF6B6B; --warn:#F0B323;
  --f-body:'Barlow',…; --f-head:'Barlow Condensed',…; --f-mono:'JetBrains Mono',…;
}
```

Corpo 15px/1.45; `h1–h3` em Barlow Condensed 600; foco `outline 2px var(--gold)`; números `.num/.mono` tabulares. Tema único escuro (o protótipo não tem tema claro).

### 5.1 Mapa de seções do CSS

| Linhas | Seção |
|---|---|
| 1–26 | Tokens, reset, tipografia |
| 27–65 | Cabeçalho, navegação, filtros, pílulas, botões |
| 66–232 | Conteúdo, cards, tabelas, DRE, busca, vazio |
| 233–276 | Diagnóstico, modais, formulários, toasts |
| 277–345 | Blocos temáticos, estrutura societária, alertas |
| 346–437 | Menu lateral, cards de caixa, menu agrupado |
| 437–509 | Título da aba, ícone info, chips, filtros no cabeçalho |
| 558–578 | Subgrupos do menu |
| 594–628 | FAQ e tutorial |
| 677–685, 754–788 | De-para |
| 737–753 | Impressão de seção |
| 789–815 | Checklist e suportes |
| 869–968 | Colunas Gestão Contábil, Variações, **Central (`cf-*`)**, drilldown |
| 1023–1034 | Ordenar/filtrar tabela, volumetria |
| 1035–1072 | Cabeçalho compacto/fixo e submenus (`hdr-nav`) |
| 1073–1155 | Mapão (fora do contábil) |

## 6. Responsividade

78 blocos `@media` no CSS. Pontos de quebra usados (contagem): 600px (18), 900px (16), 1100px (10), print (8), 760px (7), 700px (4), 520px (3), 560px (3), 1000px (3), 640, 480, 1250 (2 cada), 440, 800, 820, 1300, 1400 (1 cada), `min-width:1800px` (1), `prefers-reduced-motion` (3).

Comportamentos principais:

- **≤ 900px:** menu lateral vira gaveta (`#sideTog` + `#sideBg`); cabeçalho compacto; `#hdrNav` rola na horizontal.
- **≤ 1100px / ≤ 760px / ≤ 600px:** `.cx-grid.g-big` 4→2→1 colunas, `g-md` 3→2→1; formulários `.form` passam a 1 coluna; `.cf-bar` quebra em linhas.
- Tabelas nunca estouram a página: rolam dentro de `.tbl-wrap` (grids usam `minmax(0,1fr)`).
- **≥ 1800px:** largura maior do conteúdo.
- **print:** oculta `.no-print` (menu, botões, barras), A4 paisagem, tabelas sem rolagem.
- Margens respeitam `env(safe-area-inset-*)`.

Conferir nas capturas `capturas/mobile-*.png` (390×844).

## 7. Assets e dependências externas

| Item | Origem | Uso |
|---|---|---|
| Barlow 400/500/600/700, Barlow Condensed 500/600/700, JetBrains Mono 400/600 | Google Fonts (`<link>` no HTML) | Corpo, títulos, números. |
| Chart.js 4.4.1 | cdnjs (fallback jsdelivr), carregado sob demanda (`carregarScript`, JS 3537) | Gráficos (pouco usado nas telas contábeis). |
| SheetJS xlsx 0.18.5 | cdnjs / jsdelivr (JS 3540, 5003, 5304, 5484, 5488) | Exportar Excel da aba, ler planilhas na Central, modelos de planilha do recorrente. |
| pdf.js 3.11.174 + worker | cdnjs (JS 5308) | Ler PDFs em Documentos e Classificação. |
| Logo FINEMP | `data:image/png` no `<img id="logo">` → `assets/logo-embutido.png` | Cabeçalho. |
| Ícones | SVG inline (`ICONES`, JS 3595; botões do cabeçalho) | Menu e botões. Sem biblioteca de ícones. |

No React: usar os pacotes npm equivalentes nas mesmas versões ou superiores compatíveis; não carregar por CDN.

## 8. Dependências de runtime do protótipo (não portar como backend)

- `window.claude.use('db' | 'assets' | 'user' | 'downloads' | 'sample')` – banco compartilhado do artefato (29 usos no JS). Fora do claude.ai, cai em `localStorage`. **No FINEMP isso é substituído pela API oficial**; sem endpoint oficial, a tela fica "Em breve".
- Coleções usadas pelas telas contábeis (só para saber o que a tela lê/grava; **não é contrato de API**): `cfdocs`, `cflanc`, `cfrec`, `cfantecip`, `cflalur`, `cfregras`, `cfcfg`, `cffolha`, `cffiscal`, `cfroteiro`, `checklist`, `suportes`, `depara`, `config`, `conselho` (`cong-*`, `acessos-grupo`), `ata`. Detalhes de campos em `docs/HANDOFF_CONTABIL.md` §2.5 e por tela.
- `DADOS_GRUPO` (razão, balancete, plano de contas por empresa) e `DADOS_RH` vêm embutidos no HTML. No FINEMP vêm da cópia sincronizada do Questor no PostgreSQL, via API.
- Documentos lidos do banco vêm congelados (`dbCopia`): detalhe do runtime, irrelevante no React.

## 9. Como reproduzir localmente (outra sessão)

1. Descompactar o pacote **fora** do repositório FINEMP (ou adicionar a pasta ao `.gitignore`).
2. `cd fonte && python3 -m http.server 8790` e abrir `http://localhost:8790/prototipo-split.html` (ou abrir `prototipo-completo.html` direto).
3. No menu, abrir o separador "Contabilidade (equipe Finemp)"; cada código da §2 é o `data-tab` → sideLigar (JS 690), ligarEventos (JS 3460), menuMarcar (JS 3632), usrAplicar (JS 7906), hdrNavLigar (JS 8050), turPassosAba (JS 10310), turGeral (JS 10337), menuFiltrar (JS 12069), menuBuscaLigar (JS 12079) do botão.
4. Para inspecionar uma tela: localizar a função de render pela linha da §2 em `prototipo-app.js` e o CSS pela §5.1.
5. Ao portar: manter rótulos, ordem dos blocos, classes e estados exatamente como no protótipo e nas capturas.
