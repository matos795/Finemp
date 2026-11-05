# Frontend oficial no staging (#294 / #53)

## FATO CONFIRMADO

O backend de staging já está isolado no projeto Railway `finemp-staging`, ambiente
`staging`, com PostgreSQL próprio, sessão/CSRF HTTPS, jornada financeira sintética
e isolamento entre Companies existentes validados.

O frontend usa caminhos relativos `/api` e `credentials: include`. O navegador
não alcança a rede privada do Railway; por isso o serviço web precisa fazer o
encaminhamento server-side.

## DECISÃO

Criar um serviço `finemp-web` no mesmo projeto e ambiente do `finemp-api`.

O container:
- compila o React/Vite em build multi-stage;
- serve os arquivos estáticos com NGINX unprivileged;
- usa fallback para `index.html` nas rotas SPA;
- encaminha `/api/*` para o backend pela rede privada;
- não embute URL da API nem segredo no bundle;
- não habilita CORS genérico;
- expõe `/healthz` para health check;
- não adiciona limite de body próprio no proxy; validações de tamanho permanecem no backend;
- descobre o resolver do container e re-resolve o private domain durante a execução, evitando dependência do IP obtido no startup.

A variável runtime do serviço web deve ser uma referência Railway:

```text
FINEMP_API_UPSTREAM=http://${{finemp-api.RAILWAY_PRIVATE_DOMAIN}}:${{finemp-api.PORT}}
```

O guard do container aceita somente `http://*.railway.internal:<porta>`. O
NGINX usa o resolver presente em `/etc/resolv.conf` e força nova resolução do
hostname privado em intervalos curtos; assim um redeploy independente do backend
não exige restart do serviço web apenas para atualizar o IP. Como este staging
foi criado após 16/10/2025, o private DNS Railway oferece A e AAAA; o proxy usa
A/IPv4 nesta configuração. A comunicação interna usa HTTP dentro da rede privada;
o navegador continua usando HTTPS no domínio público do `finemp-web`.

## Configuração Railway

No projeto `finemp-staging`, ambiente `staging`:

1. criar serviço `finemp-web` a partir do mesmo repositório GitHub;
2. configurar Root Directory como `/frontend`;
3. configurar Railway Config File como `/frontend/railway.toml`;
4. definir apenas `FINEMP_API_UPSTREAM` com a referência acima;
5. gerar domínio público para `finemp-web`;
6. manter uma réplica e health `/healthz`;
7. não criar `VITE_API_URL`, senha, token, cookie ou credencial no frontend.

A variável `PORT` é fornecida pela plataforma. Alterações de variáveis no
Railway ficam staged até serem aplicadas/deployadas.

## Segurança e sessão

O browser faz requests somente para a origem pública do `finemp-web`. O NGINX
repassa `/api` para `finemp-api` e devolve ao navegador a resposta do backend,
incluindo `Set-Cookie`. Como a API usa cookie Secure/HttpOnly/SameSite=Lax e não
há Domain explícito, a sessão permanece host-only na origem pública do frontend.

O backend continua com `forward-headers-strategy=none`; os headers
`X-Forwarded-*` enviados pelo proxy são informativos e não mudam decisões de
segurança do Spring neste estágio.

Não remover o domínio público atual da API neste incremento. Essa redução de
superfície deve ser uma decisão operacional separada depois que o frontend
same-origin estiver comprovado.

## Smoke após deploy

Usar somente a credencial sintética já mantida em armazenamento seguro. Não
copiar senha para Issue, terminal compartilhado, documentação ou chat.

Validar, nesta ordem:

1. `GET /healthz` do domínio web retorna 200;
2. abrir diretamente `/login` e uma rota React para provar SPA fallback;
3. no DevTools, `GET /api/auth/csrf` aparece na mesma origem do frontend e retorna 200;
4. login sintético e `/api/auth/me` retornam sessão válida;
5. selecionar a Company autorizada;
6. abrir Contas bancárias;
7. abrir Extratos e conciliação, incluindo preview/importação com fixture sintética;
8. abrir Contas a receber e Contas a pagar, filtros e aging;
9. abrir Fluxo mensal e Fluxo diário;
10. abrir DRE operacional e um drill-down de efeitos;
11. trocar/recarregar rotas para conferir que não há 404 do servidor web;
12. logout e confirmação de 401 em `/api/auth/me`.

A prova de 403 para a segunda Company sintética continua sendo evidência backend
já registrada; não expor o UUID negativo na interface só para repetir essa prova.

## Gate de homologação

Este incremento conclui apenas o empacotamento/conexão do frontend. Depois do
deploy, registrar no #53:

- SHA da `main` publicada;
- domínio público do `finemp-web`;
- health;
- login/logout;
- rotas exercitadas;
- erros encontrados;
- confirmação de que nenhuma request do browser chama diretamente o domínio
  público da API;
- resultado da comparação visual com o protótipo homologado.

Somente depois dessa rodada o recorte pode ser apresentado ao João como primeira
versão homologável. Produção continua fora do escopo.
