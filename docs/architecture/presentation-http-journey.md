# Jornada HTTP sintética — #53 / #256

## DECISÃO

Complementar o cenário de leitura #252 com uma jornada em servidor Spring Boot real, porta aleatória e PostgreSQL Testcontainers. Não é MockMvc com usuário injetado. Não inicia staging permanente, não altera dados operacionais e não conecta a interface.

O teste `PresentationHttpJourneyIntegrationTest` cria somente no fixture um usuário sintético com senha aleatória em memória, grant ALL e cadastros mínimos. O fluxo financeiro acontece pela API:

1. Leitura sem sessão retorna 401.
2. Obter CSRF e efetuar login; cookie mantido pelo client HTTP.
3. Criar receita nominal 3,00 por POST idempotente e repetir a mesma chave; recibo idêntico.
4. Baixar em 02/07/2026 por 3,10, com CSRF, chave e versão.
5. Consultar snapshot julho/2026, data-base 31/07/2026, escopo ALL: resultado e geração de caixa 3,10; a receber zero.
6. Conferir RFIN 0,10 e negar leitura da Company alheia (403).
7. Logout invalida acesso (401).

## Reprodução

Java 21 e Docker são necessários. A partir de `backend`:

```sh
./mvnw -Dtest=PresentationHttpJourneyIntegrationTest test
```

O teste integra a suíte do CI. O bootstrap e a limpeza são restritos aos UUIDs sintéticos criados no banco descartável. Nenhuma credencial precisa ser fornecida, gravada em arquivo ou publicada. Não converter esse fixture em seed de produção.

## Limites e próximo gate

O teste comprova o caminho de sessão e comandos ao backend e a leitura financeira resultante, quando verde. Não demonstra a tela, proxy/TLS de implantação, provisionamento real de usuários ou operação em produção. O teste #252 mantém a cobertura conjunta de títulos vencidos/futuros e somas por linha.

PROPOSTA para #53: executar a mesma jornada em um staging sintético isolado e conectar a Apresentação através do client tipado; registrar paridade visual, limites de cobertura e aceite de João. A implantação e o acesso ao ambiente permanecem entregas próprias. #41/#53, #197/#128 e as fontes ausentes não são encerrados por este teste.
