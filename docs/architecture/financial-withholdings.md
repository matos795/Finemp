# Retenções informadas — #159 / #173

## DECISÃO homologada

Registrar retenções vinculadas ao lançamento/documento e distinguir valores a recolher de valores a compensar. O backend e o PostgreSQL são a fonte de verdade. O frontend não define regras tributárias, cálculos ou persistência.

## Fundação de persistência

FinancialWithholding pertence à Company e aponta para um FinancialEntry persistido da mesma Company. WithholdingValues registra identificador informado do tributo, tratamento explícito PAYABLE (a recolher) ou RECOVERABLE (a compensar), valor canônico exato e referência documental informativa opcional. Não infere tratamento pela direção do lançamento. O texto documental não é uma nova entidade fiscal, emissão de nota ou chave única validada de documento eletrônico.

O estado monetário usa centavos exatos não negativos, até NUMERIC(19,2), sem arredondar entradas com fração residual de centavo. Zero é representável no estado, assim como nos outros valores financeiros canônicos; isso não define um motor de sugestão ou de cálculo tributário. Taxas, base de cálculo e bruto/líquido não são calculados. Não se presume limite pela soma/nominal: essa regra não foi homologada. Múltiplas retenções com mesmo identificador/tratamento não são proibidas por unicidade arbitrária. Reexecuções operacionais serão protegidas por chave idempotente nos comandos seguintes.

V40 contém FK composta Company/entry, referências aos responsáveis, timestamps e versão. A FK restringe exclusão do lançamento enquanto houver retenção; não há cascade que apague fatos silenciosamente. A proteção permanece mesmo quando a captura financeira estiver desabilitada. Atualizar retenção não muda nominal, valor pago, datas, classificações ou saldos do lançamento.

## PONTO ABERTO / próximo incremento

Esta fundação não expõe endpoints nem comandos operacionais. A #159 permanece aberta para escrita com autorização vigente, versões, recibos, auditoria oficial e verificação dos períodos afetados, além do contrato de leitura/API. O domínio não aplica o mock percentual de NFS-e do HANDOFF (p_ctr.js/calcNF/nfCalc) nem suas fórmulas de líquido. Validação legal, calendário fiscal, vencimentos, títulos derivados e contabilização/sugestão automática seguem separados e abertos. Não há acesso ou escrita no Questor.

## Validação

Três testes locais cobrem dinheiro exato/limites, tratamento declarado e entradas textuais inválidas. Seis contratos PostgreSQL verificam persistência exata sem efeito no lançamento, FK de outra Company, consultas paginadas isoladas, versão concorrente, proteção da exclusão e checks monetários/tipo/código. Dados exclusivamente sintéticos; compilação local não substitui execução PostgreSQL na CI.
