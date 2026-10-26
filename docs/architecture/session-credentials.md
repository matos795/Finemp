# Sessões e versão das credenciais

## DECISÃO técnica

O FINEMP continua usando sessão de servidor, cookie HttpOnly/Secure/SameSite e CSRF. Não há migração para tokens persistidos no navegador.

Cada principal autenticado guarda a versão das credenciais observada no login. Antes de autorizar uma requisição, o filtro da cadeia de segurança consulta se a conta continua ativa e se a versão ainda é a mesma. Troca de senha, recuperação ou desativação incrementam a versão e revogam as sessões antigas na próxima requisição. Conta excluída também invalida a sessão.

O filtro não é registrado como filtro de servlet independente. Quando a sessão é revogada, invalida a sessão e limpa o contexto; as regras normais retornam 401 para rotas protegidas. Rotas públicas continuam utilizáveis, inclusive com cookie antigo. Falha da consulta não concede acesso.

O login bem-sucedido troca o identificador de uma sessão anônima existente antes de salvar a autenticação, protegendo contra fixação de sessão.

## Verificação

- testes de filtro: anônimo, sessão válida, sessão revogada;
- testes HTTP/PostgreSQL: sessão após troca de senha e desativação recebe 401;
- teste HTTP: identificador da sessão muda no login;
- isolamento e permissões de empresa continuam sendo verificados separadamente.

## Limites conhecidos

Uma requisição já autorizada não é cancelada retroativamente. O incremento acrescenta uma consulta curta por requisição autenticada; cache não foi introduzido para não atrasar revogação. Sessões distribuídas e limitação de tentativas são incrementos separados, não requisitos implicitamente concluídos aqui.
