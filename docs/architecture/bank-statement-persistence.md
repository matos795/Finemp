# Persistência de extratos — #40 / #181

## FATO / DECISÃO

A referência deduplica por conta usando FITID quando fornecido; sem FITID usa data, valor e histórico normalizado. V42 armazena movimentos por Company e conta, com data, sinal e valor exato em centavos, histórico/documento/identificador informados, responsável, data e versão. Zero e fração residual de centavo são recusados sem arredondamento nesta representação canônica. A política de entrada bruta/subcentavo permanece aberta na #128.

Uma chave SHA-256 combina campos canônicos com separadores não ambíguos: FITID aparado e sensível a maiúsculas tem precedência; sem ele, usa data, valor com sinal e memo normalizado por decomposição, remoção de marcas U+0300–036F, minúsculas e espaços externos. Com FITID, a unicidade é por Company e conta. Sem FITID, a chave é apenas um indicador de possível duplicidade e pode repetir-se, pois movimentos legítimos podem ser semelhantes. A representação da chave é interna, não precisa ser igual aos textos salvos pelo protótipo. Diferenças de versões Unicode ou de arredondamento binário permanecem pontos de atenção em comparação com o legado.

O estado PENDING/IGNORED/RECONCILED e o vínculo opcional a FinancialEntry pertencem à mesma Company; constraint exige vínculo apenas em RECONCILED, e índice único impede que um lançamento seja vinculado duas vezes. FKs restringem exclusão de conta ou lançamento referenciados. O guard da conta financeira permanece ativo mesmo com o módulo de captura desabilitado. O banco não calcula saldos, não cria lançamentos nem modifica o Questor.

## PONTO ABERTO / próximos incrementos

Este incremento de persistência não confirma importação nem efetua conciliação. O contrato futuro deverá manter arquivo → parsing → validação/prévia → confirmação atômica, com autorização, auditoria, idempotência, isolamento e verificação dos períodos efetivamente afetados. A prévia deve mostrar possíveis duplicidades, inclusive as dentro do arquivo. A segurança de repetição do comando de importação dependerá de recibo idempotente próprio, não da chave de semelhança. O tratamento de FITID reutilizado permanece aberto. Conciliação/desconciliação não pode contornar fechamento.

## Validação

Testes locais cobrem FITID, fallback, sinal, acentos, precisão e limites. Seis contratos PostgreSQL sintéticos cobrem exatidão sem efeitos em outras contas, unicidade por Company/conta, FKs entre Companies, vínculo único, proteção de conta com recurso desligado, estados/versão e concorrência da mesma identidade. A execução PostgreSQL é realizada na CI.
