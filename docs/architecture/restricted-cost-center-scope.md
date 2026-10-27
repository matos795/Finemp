# Escopo restrito e relatórios ainda não segmentados

## FATO CONFIRMADO

O modelo atual possui `CostCenterScope.ALL` e `RESTRICTED`, mas os relatórios/movimentos contábeis existentes ainda não aplicam um filtro efetivo de centro de custo. A reprodução contábil utiliza o Razão sem CC. Um enum persistido não constitui suporte funcional à segmentação.

## DECISÃO técnica de segurança

Grants `RESTRICTED` não autorizam rotas de dados não segmentadas, mesmo quando possuem `DRE_VIEW` ou outra permission. A autorização genérica por empresa e a autorização por permission exigem `ALL` neste estágio. Não há fallback para totais integrais, resultados estimados ou filtro apenas no frontend.

`/api/auth/me` continua mostrando os metadados dos próprios vínculos, sem movimentos/valores. Grants `ALL` continuam exigindo as permissões explícitas previstas por cada operação; administração de contabilidade não cria esses grants automaticamente.

## PONTO ABERTO

Segmentação efetiva depende da presença/identificação do centro de custo na origem e de filtros de dados no backend. Não declarar centros de custo restritos como entregues antes dessa cadeia existir. Isso não bloqueia operações com `ALL` explicitamente concedido nem os módulos independentes do roadmap.

## Verificação

Testes unitários de permission e acesso genérico, mais HTTP/PostgreSQL negando DRE e movimentos para grant restrito. Nenhum dado real foi alterado ou classificado por esse incremento.
