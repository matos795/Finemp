# Finemp Engineering Playbook

Este documento define o processo padrão de engenharia utilizado no Finemp.

## Princípios

O código de produção deve ser compreendido pelo desenvolvedor que o mantém.

Decisões relevantes devem registrar não apenas o que foi escolhido, mas também o motivo.

Hipóteses de negócio não devem ser transformadas em regras permanentes sem validação.

Credenciais, dados reais de clientes e outras informações sensíveis não devem ser versionados, documentados publicamente ou incluídos em exemplos.

## Fluxo padrão de desenvolvimento

Toda mudança relevante segue preferencialmente:

```text
GitHub Issue
    ↓
branch curta
    ↓
TDD seletivo
    ↓
implementação
    ↓
testes locais
    ↓
commits pequenos
    ↓
Pull Request
    ↓
CI
    ↓
revisão
    ↓
merge somente com CI verde
```

## GitHub Issues

Uma Issue deve descrever o resultado desejado, e não apenas uma lista de arquivos a modificar.

Quando aplicável, deve conter:

- contexto;
- objetivo;
- escopo;
- critérios de aceite;
- itens fora de escopo;
- decisões já confirmadas.

Issues devem permanecer pequenas o suficiente para produzir Pull Requests revisáveis.

## Branches

Branches devem ser curtas e representar uma mudança específica.

Exemplos:

```text
chore/backend-foundation
feat/company-persistence
feat/accounting-movements
fix/sync-cursor
```

Prefixos utilizados inicialmente:

- `feat`: funcionalidade;
- `fix`: correção;
- `chore`: infraestrutura ou manutenção;
- `test`: mudanças predominantemente relacionadas a testes;
- `docs`: documentação;
- `ci`: integração contínua.

## TDD

O Finemp utiliza TDD de forma seletiva.

Priorizar TDD em:

- regras financeiras;
- persistência;
- isolamento de dados;
- sincronização;
- classificação;
- integrações;
- regras cuja regressão possa alterar resultados contábeis.

Evitar testes sem valor significativo, como testes exclusivos de getters e setters.

Quando TDD for adequado:

```text
RED
→ teste descreve comportamento esperado e falha

GREEN
→ implementação mínima faz o teste passar

REFACTOR
→ código é melhorado mantendo testes verdes
```

Todo teste importante deve responder:

> Qual comportamento ou regra este teste protege?

## Testes de persistência

Testes de integração relacionados ao banco devem utilizar PostgreSQL real através de Testcontainers.

Não utilizar H2 como substituto do PostgreSQL para testes de persistência.

Isso reduz diferenças entre ambiente de teste e produção e permite testar recursos específicos do PostgreSQL.

## Banco de dados

Flyway é responsável pela evolução do schema.

Hibernate deve validar o schema, não criá-lo automaticamente.

Configuração padrão:

```text
spring.jpa.hibernate.ddl-auto=validate
```

Alterações estruturais devem ser feitas através de migrations versionadas.

## Commits

Commits devem ser pequenos e representar uma mudança coerente.

Exemplos:

```text
chore: bootstrap Spring Boot backend
chore: add local PostgreSQL environment
test: verify PostgreSQL integration with Testcontainers
ci: add backend verification workflow
```

Antes de um commit:

```text
alterar
→ testar
→ git status
→ git diff
→ git add
→ git diff --cached
→ commit
```

Não versionar mudanças que o autor não compreende.

## Pull Requests

Todo Pull Request deve explicar:

- o problema ou objetivo;
- as principais mudanças;
- decisões relevantes;
- como a mudança foi testada;
- Issue relacionada.

Mesmo quando houver apenas um desenvolvedor principal, o Pull Request funciona como ponto formal de revisão antes do merge.

## CI

A integração contínua deve validar automaticamente o código enviado para Pull Requests.

Para o backend, a verificação principal é:

```bash
./mvnw --batch-mode verify
```

A CI deve utilizar Java 21 e executar os testes de integração com Testcontainers.

## CD

Continuous Deployment não é obrigatório nesta etapa.

A estratégia de deploy será definida quando houver um ambiente de hospedagem escolhido.

Até essa decisão:

```text
CI = adotado
CD = ponto aberto
```

## Merge

Código só deve entrar na branch principal quando:

- critérios de aceite relevantes estiverem atendidos;
- testes locais estiverem verdes;
- CI estiver verde;
- Pull Request tiver sido revisado;
- não houver credenciais ou dados sensíveis versionados.

## Registro de decisões

Usar estas classificações durante descoberta e arquitetura:

### FATO CONFIRMADO

Informação validada por código, dados, documentação ou responsável pelo negócio.

### DECISÃO

Escolha já adotada pelo projeto.

### PROPOSTA

Solução sugerida, ainda sujeita a revisão.

### PONTO ABERTO

Questão que ainda precisa ser investigada ou validada.