# Limitação de tentativas de identidade

## DECISÃO técnica

O limite é aplicado antes da verificação de senha. Login excessivo recebe 429, sem desativar a conta. A chave combina endereço remoto e e-mail normalizado, além do limite por endereço; outro endereço não herda um bloqueio global de login imposto ao usuário.

Recuperação possui limites por endereço e por e-mail para evitar envio repetitivo. A futura operação HTTP sempre responderá 202, inclusive quando limitada ou quando a conta não existe. A conclusão de links possui limite por endereço, sem usar o token bruto como chave.

As chaves são hashes SHA-256 em memória. Não são registradas em logs. Usa-se `request.getRemoteAddr()`, não cabeçalhos arbitrários enviados pelo cliente. Configuração de proxy confiável deve ser revisada no ambiente de publicação.

## Configuração inicial

Todos os limites são técnicos, configuráveis e não alteram regras financeiras:

| Propriedade `finemp.identity.rate-limit.*` | Padrão |
| --- | --- |
| `window` | `PT15M` |
| `capacity` | 10000 chaves |
| `login-ip` | 60 |
| `login-pair` | 10 |
| `reset-ip` | 20 |
| `reset-email` | 3 |
| `completion-ip` | 30 |
| `invitation-actor` | 30 |

Janela fixa iniciada na primeira tentativa; limites contam tentativas bem-sucedidas e malsucedidas. Rejeita excesso até a expiração. A capacidade é limitada; entradas ativas não são removidas para permitir evasão por novas chaves. Ao atingir a capacidade, novas chaves são rejeitadas até haver expiração.

## Limites conhecidos

Este adapter é local a uma instância e reinicia com o processo. Antes de múltiplas réplicas, usar limitação compartilhada ou na borda e revisar endereços de proxy. Não declarar proteção distribuída como concluída. Nenhum estado de limitação é convertido em bloqueio permanente do usuário.

## Testes

Janela/limite exatos, capacidade, concorrência, normalização, separação por endereço e HTTP 429. A senha do DTO de login é redigida no `toString()`.

A verificação de backend roda em PRs de qualquer base, inclusive branches empilhadas, mantendo o gatilho de push restrito ao `main`.
