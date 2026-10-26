# Ativação e recuperação pela API

## Operações implementadas

Estas rotas somente existem quando `finemp.identity.mail.enabled=true`, com o transporte devidamente configurado. Não expor recuperação sem um canal de entrega funcional.

| Operação | Resultado |
| --- | --- |
| `POST /api/auth/password-reset/request` | 202 vazio para e-mail conhecido, desconhecido, inativo ou limitado |
| `POST /api/auth/password-reset/complete` | 204 na conclusão; 400 para link inválido/expirado/consumido ou senha incompatível |
| `POST /api/auth/activate` | 204 para convite válido; 400 nos demais casos |

Todas exigem CSRF, inclusive sem login. Obter o token com `GET /api/auth/csrf`; enviar cookies e cabeçalho nas operações mutáveis. Tentativas excessivas de conclusão recebem 429. Dados de entrada malformados recebem 400 sem consultar a existência da conta.

O corpo de conclusão contém `token` e `newPassword`. A senha precisa de pelo menos 8 caracteres, uma letra ASCII e um dígito; limite técnico de 72 bytes UTF-8. O segredo vem do fragmento do link entregue por e-mail, nunca da resposta da API. DTOs redigem segredo/senha em representações de diagnóstico.

Segredos são validados pelo serviço, sem incluí-los em mensagens de valor rejeitado do Bean Validation. Os adapters de identidade/login tratam payload malformado e falhas de validação com 400 vazio, sem registrar a exceção ou devolver detalhes que possam conter credenciais.

## Sessões e consumidor

Uma conclusão válida invalida a sessão da própria requisição, se houver. Outras sessões são revogadas pela versão das credenciais na próxima requisição. O consumidor deve obter novo CSRF e fazer novo login; o link não inicia sessão automaticamente.

O frontend lê o fragmento e o remove do histórico antes de telemetria ou navegação. Não guarda token/senha no armazenamento do navegador. Links usam base definida pelo servidor, não o cabeçalho Host da requisição.

## Segurança e limites

Finalidade, expiração, consumo único, versão das credenciais e estado da conta são validados na transação que muda a senha. A resposta neutra de recuperação não promete tempo de execução constante: infraestrutura/borda deve monitorar abuso e considerar envio assíncrono em escala. Limites atuais são locais à instância; SMTP real depende dos secrets/configurações do ambiente, não de lógica do frontend.

Testes HTTP com PostgreSQL cobrem neutralidade, rate limit sem reemitir link, replay, finalidade errada, revogação de sessão, ativação, CSRF e rotas desabilitadas.
