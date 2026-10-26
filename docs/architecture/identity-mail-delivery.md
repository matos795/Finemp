# Entrega de links de identidade

## Contrato e transação

`AccountActionMailSender` é o port de envio. O adapter SMTP usa o suporte de e-mail do Spring e recebe host/autenticação por configuração externa. Desenvolvimento e testes podem fornecer um double do port.

`AccountActionMailDelivery` escuta o evento de emissão somente em `AFTER_COMMIT`. Um rollback não envia e-mail; uma falha de envio não desfaz uma conta/token já confirmados. A falha é registrada por finalidade, sem destinatário, token, conteúdo da mensagem ou exceção do provedor. O usuário pode solicitar um novo link.

Não há retry automático de entrega com resultado desconhecido. Nenhum outbox com token bruto é persistido. O segredo existe em memória até ser entregue ao transportador de e-mail.

## Link do frontend

- convite: `<base>/activate#token=<segredo>`;
- recuperação: `<base>/reset-password#token=<segredo>`.

O fragmento é lido pelo frontend e enviado no corpo da operação de conclusão. Ele não integra a query enviada ao servidor ao abrir a página. O consumidor deve removê-lo do histórico após a leitura e não enviá-lo a analytics/telemetria.

A base vem exclusivamente da configuração do servidor. Rejeita userinfo, query, fragmento e protocolo diferente de HTTPS. HTTP só é permitido quando `require-https=false` é explicitamente configurado para desenvolvimento.

## Configuração

A entrega fica desabilitada por padrão. Para habilitar, fornecer:

```properties
finemp.identity.mail.enabled=true
finemp.identity.mail.from=nao-responda@example.invalid
finemp.identity.mail.frontend-base-uri=https://app.example.invalid
spring.mail.host=${FINEMP_SMTP_HOST}
spring.mail.port=${FINEMP_SMTP_PORT:587}
spring.mail.username=${FINEMP_SMTP_USERNAME}
spring.mail.password=${FINEMP_SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
```

Os valores acima são ilustrativos. Senhas/chaves reais ficam no mecanismo de secrets do ambiente e nunca no Git. Habilitar a entrega sem os beans/configurações requeridos provoca falha de inicialização, em vez de descartar silenciosamente todos os links.

O adapter define timeouts padrão de 5 segundos para conexão/leitura/escrita e exige STARTTLS para SMTP por padrão. Configuração de SMTPS pode usar o protocolo correspondente. O debug do protocolo é desabilitado para impedir registro do corpo contendo links. Alterar timeouts/TLS só deve ocorrer por configuração explícita e coerente com o ambiente.

## Estado da entrega

Este incremento fornece transporte e testes de commit/rollback. Os endpoints públicos e administrativos serão publicados junto com validação de sessão, rate limiting, DTOs e OpenAPI no próximo incremento da Issue #36/#37.
