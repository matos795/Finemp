# Oracle de paridade da referência FINEMP

Este utilitário extrai metadados e executa somente funções de cálculo revisadas do `FINEMP_web_1.zip` homologado. O HTML, configurações, dados demo e adapters da aplicação não são copiados para o repositório.

## Preparar

Requer Node.js22 ou superior:

```sh
cd tools/reference
npm ci --ignore-scripts
npm test
```

Extraia o ZIP fornecido pelo negócio para uma pasta local fora do Git. Em seguida:

```sh
npm run inventory -- /caminho/web/index.html ../../docs/reference/frontend-backend-parity.md
npm run oracle -- /caminho/web/index.html ../../docs/reference/frontend-backend-parity.md
```

O hash do HTML é obrigatório e fixado em `reference.mjs`. Uma nova referência exige revisão explícita do código e da matriz antes de atualizar esse hash.

`inventory` analisa o AST e identifica todas as telas e declarações de funções. A validação opcional da matriz confere que cada tela extraída está documentada.

`oracle` extrai somente os bindings da allowlist de cálculo e os executa com fixtures sintéticas de `cases.mjs`. O bootstrap do aplicativo, DOM, storage, autenticação e integrações externas não são inicializados. O contexto VM é uma conveniência de execução; não é uma fronteira de segurança para código arbitrário. A proteção depende do hash fixo e da seleção revisada de funções.

## CI e evidência local

A CI executa os testes do extrator com HTML sintético. O arquivo real não deve ser enviado como artifact público nem adicionado ao Git. A captura inicial de18 casos foi executada localmente contra a referência fixada e está registrada na matriz.

Cada porta Java deve usar os mesmos inputs/expectations, comparar todos os campos relevantes e acrescentar testes de autorização, persistência, transação, concorrência e idempotência. Resultados do oracle não certificam uma implementação Java inexistente.

## Números

O runner preserva números e arredondamento do JavaScript. Os casos `legacy-binary-rounding-1005` e `legacy-negative-tie` documentam resultados que diferem de uma política decimal `HALF_UP`. Uma mudança deliberada deve ter teste, justificativa e exemplos na documentação do respectivo cálculo.

