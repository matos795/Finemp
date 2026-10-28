# Categorias financeiras operacionais — Issue124 /38

## FATO CONFIRMADO

Referência executável já homologada, consultada pontualmente em453–468,517–519,1963–1967 e cadastro5460–5506: plano próprio da Empresa com grupos1.n de entradas e2.n de saídas e subcategorias .n. Sufixo é máximo existente+1. Grupo novo tem atividade operacional, DRE REC nas entradas/DADM nas saídas e uma Nova subcategoria. Nova subcategoria começa apenas com nome/código; campos ausentes caem nos padrões do grupo.

Atividade efetiva = sub.nat ou grupo.nat ou operacional. Fixo/variável efetivo = sub.cv ou grupo.cv ou variável para entradas/fixo para saídas; o lançamento pode ter override próprio, tratado na #39. O seletor antigo podia mostrar F quando group.cv estava ausente, embora cvCat computasse V para entradas. O backend preserva o cálculo observado, sem transformar o detalhe visual em regra.

DRE efetiva = sub.dre ou grupo.dre ou NAO. Existe uma diferença observada: o handler de edição Do grupo grava o valor atual de grupo.dre em sub.dre; não remove o campo. Portanto, esse comando cria uma fotografia, enquanto ausência inicial de sub.dre permite fallback dinâmico. A implementação preserva ambos os estados, sem corrigir silenciosamente a referência homologada.

Excluir subcategoria com lançamentos é recusado. Grupo com lançamento em qualquer sub também não pode ser removido; sem uso, grupo e subcategorias são removidos juntos. Não é desativação e não há mínimo de grupos/subcategorias.

## DECISÃO

FinancialCategory é catálogo operacional, separado de AccountingAccount/PlanoQ. Há somente dois níveis tipados GROUP/SUBCATEGORY; direção e código são imutáveis. UUIDs internos e FKs compostas isolam Company. Sequência BIGINT positiva é gerada no backend sob trava da Empresa, com conflito antes de overflow. Nome até255 é limite técnico; sort_key ASCII com collation C ordena numericamente os códigos gerados. Nenhum cálculo monetário é criado.

POST groups recebe direction/name e cria uma subcategoria inicial; permitir o nome na mesma operação equivale ao cadastro seguido da edição de nome existente. POST group/subcategories recebe name e deixa overrides ausentes. PUT recebe name/cashActivity/dreLine/costBehavior/expectedVersion. No grupo, atividade e DRE são obrigatórias e custo pode ficar ausente. Na sub, cashActivity/costBehavior null significam herança; dreLine null significa fotografar a DRE do grupo no momento da escrita, conforme handler homologado. Valor explícito de DRE continua explícito. Resposta distingue raw cashActivity/dreLine/costBehavior de effective calculado pelo servidor. Isso evita delegar regras ao frontend.

Classificações de fluxo: OPERATING/INVESTING/FINANCING, correspondentes a op/inv/fin. Custo FIXED/VARIABLE corresponde F/V. DRE conserva os treze identificadores observados REC,DED,CUS,DCOM,DADM,DPES,DTRI,OUT,DEP,RFIN,DFIN,IR,NAO; não são mapeamento novo do PlanoQ.

Leitura exige FINANCIAL_CATEGORY_VIEW ou MANAGE explícitos ALL; escrita exige MANAGE. Administração não substitui grants. Autorização atual é revalidada depois da trava de Empresa; versões exatas (incluindo zero) e flush antes da resposta evitam sobrescrita silenciosa. Transações READ_COMMITTED/timeout5s serializam sequência, alterações e exclusão. API pagina1–100 (padrão50), busca literal normalizada e filtros por direção/tipo/pai. Categoria e classificação do pai são projetadas como valores escalares pela mesma instrução SQL company-scoped, compartilhando o snapshot MVCC e evitando reutilizar entidades antigas do contexto JPA. Validação do filtro de pai também é escalar. Exclusão concorrente ou grupo previamente carregado não mistura classificações de momentos distintos. Não há N+1 nem cálculo no cliente.

Feature FINEMP_FINANCE_CATALOG_ENABLED continua desligada por padrão. Erros fixos não expõem diagnósticos SQL. Nenhum dado real, secreto ou leitura operacional externa é utilizado.

## DEPENDÊNCIAS ISOLADAS

FinancialCategoryUsageGuard detectará lançamentos na categoria ou em filhos. Adapter real será fornecido pela #39, com FKs tenant-scoped; porta simulada não é integração operacional. Referências físicas posteriores sempre impedem exclusão insegura. Não usar AccountingMovement como substituto.

Plano padrão completo observado é incremento separado, sem aplicar carga retroativa automática a Empresas existentes. Relatórios e overrides de lançamentos continuam nas #39/#41. Mudanças futuras no comportamento de fotografia/herança exigem decisão explícita, não ajuste silencioso de dados existentes. #38 permanece aberta até o restante de seus critérios.

## VALIDAÇÃO

TDD de defaults/herança/fotografia e segurança/versão/uso (RED → GREEN). PostgreSQL/HTTP cobre sequência concorrente com sub inicial, classificação bruta/efetiva após alterações, exclusão transacional, FKs, tenants, grants/CSRF/revogação, busca/páginas e isolamento ambiente. CI usa PostgreSQL real; compilação local sem Docker não conta como teste integrado.
