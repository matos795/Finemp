# Recibos de importação de extratos

## FATO / DECISÃO

A prévia é temporária e não cria movimentos. A confirmação será uma operação distinta. Um recibo por empresa, ator e identificador do comando permitirá que a futura operação compare o fingerprint e devolva o resultado já confirmado; não usa similaridade de movimentos como prova de duplicidade. O recibo e os movimentos devem ser gravados na mesma transação, depois das verificações de autorização e fechamento. A estrutura não armazena arquivo ou prévia.

A V44 prepara a persistência, valida o vínculo entre empresa e conta e protege a conta referenciada. Nenhum comando de importação é disponibilizado por esta fundação. Testes PostgreSQL verificam isolamento, chave única concorrente e rollback conjunto sintético.

## PONTO ABERTO

O cadastro de conta não possui identificadores bancários suficientes para presumir correspondência com o arquivo. A confirmação de conta e alertas será tratada explicitamente na operação futura. A definição do conteúdo mínimo do recibo, a política de seleção de linhas e o fluxo de conciliação permanecem separados deste incremento.
