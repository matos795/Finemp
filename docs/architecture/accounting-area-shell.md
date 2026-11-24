# Área da Contabilidade: shell do protótipo — #452 (M2)

Referência visual: [FRONTEND_CONTABIL_REFERENCE](../reference/FRONTEND_CONTABIL_REFERENCE.md).
Mapa de telas: [accounting-area-map](../reference/accounting-area-map.md).

## DECISÃO

- **Visual:** o CSS do protótipo (`fonte/prototipo.css`, v105) é portado **sem renomear
  classes** para `frontend/src/styles/accounting-prototype.css`, gerado por
  `frontend/scripts/scope-accounting-css.mjs`. Todo seletor fica sob `.ctbp` (raiz da
  Área da Contabilidade): `:root`/`html`/`body` viram `.ctbp`; `body.x Y` vira `.ctbp.x Y`.
  A Área da Empresa (`.fin`) não é afetada. Complementos mínimos em `accounting-area.css`.
- **Shell:** mesma marcação do protótipo — `div.app#app`, `button.side-tog`, `div.side-bg`,
  `aside.side` (busca de aba, `nav-sep`, `nav-g`, `nav-sg`, pílulas `data-tab`),
  `div.hdr-fixo` (marca, `pg-row` com título do grupo, `hdr-nav` e Imprimir), legenda e
  `section#p-{código}`. Comportamento de `sideLigar`, `menuMarcar`, `menuFiltrar`,
  `hdrNav` e `hdrMedir` reproduzido em React (menu fecha ao escolher aba; gaveta ≤ 900px;
  `--hdrH`).
- **Navegação:** Contabilidade → Cliente → Empresa vem só de
  `GET /api/auth/accounting-portfolio` (#435), no modal de contexto (padrão `#mFiltros`).
  A escolha guardada é revalidada contra a carteira a cada carga. Nenhum endpoint
  administrativo de hierarquia é usado para navegar (teste automatizado).
- **Entrada:** membro de ao menos uma contabilidade ativa (`organization-access`);
  SYSTEM_ADMIN recebe todas do backend. Quem não tem empresa e é membro cai na área
  contábil após o login; os demais continuam no Painel.
- **Dados:** escolher a empresa não libera dados. A legenda informa se o usuário tem
  grant na empresa (`/api/auth/me`); cada leitura continua autorizada no backend.
- **Telas:** as 39 telas, na ordem e com os códigos do protótipo. Bloco de cabeçalho
  (`.ind-bloco.emp`) onde o protótipo tem; painéis com os títulos do protótipo e
  "Em breve" enquanto não houver contrato oficial. Nenhum mock, `DADOS_GRUPO`,
  `window.claude` ou `localStorage` do protótipo como dado.
- **Competência:** barra `cf-bar` nas telas da Central; competência só na memória da
  sessão (como no protótipo), padrão = mês seguinte à base. "Base contábil até" vem da
  última sincronização concluída (#440); sem base ou sem `QUESTOR_SYNC`, o texto diz isso.

## Diferenças conhecidas em relação ao protótipo

- Cabeçalho: no lugar de "Logo do cliente" (sem backend) ficam o seletor de área
  Empresa | Contabilidade e "Sair". O botão de filtro abre o contexto
  Contabilidade/Cliente/Empresa; o período fica na barra de competência.
- Excel e Tutorial da aba ainda não portados (sem dependência nova neste incremento).
- As capturas de referência foram geradas sem a fonte Barlow Condensed carregada; o CSS
  do protótipo pede Barlow Condensed nos títulos e é isso que o React usa (conferido
  renderizando o protótipo localmente).

## PONTO ABERTO

- Integração de M3–M6 dentro das telas (fontes/sincronização, De-Para, drilldown, DRE).
- Telas da Central/Pacote dependem de C1/C2 e de O1–O21 do GAP.
