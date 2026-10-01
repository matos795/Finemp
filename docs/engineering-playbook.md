
Uma observação: a credencial `finemp_local` **não precisa ir para o README**. O desenvolvedor não precisa aprender a copiar uma senha do documento; o Compose já contém a configuração descartável de desenvolvimento.

---

# 2. Crie `docs/engineering-playbook.md`

Aqui está uma parte especialmente importante para os próximos chats:

```markdown
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