# Catálogo de acesso e presets — #80 / #37

## FATO CONFIRMADO

`GET /api/auth/access-catalog` exige sessão e retorna metadados versionados:
permissions de plataforma, contabilidade e empresa, além de presets view/edit
das operações já implementadas. Não retorna grants de usuários nem dados
financeiros; não é autorização. `/me` e `/organization-access` continuam sendo
as fontes do acesso efetivo do usuário autenticado.

## DECISÃO técnica

| Preset | View | Edit |
| --- | --- | --- |
| accounting-ledger | ACCOUNTING_MOVEMENT_VIEW | indisponível |
| accounting-dre | DRE_VIEW | indisponível |
| account-classification | ACCOUNT_CLASSIFICATION_VIEW | VIEW + ACCOUNT_CLASSIFICATION_MANAGE |
| questor-import | indisponível | QUESTOR_SYNC; exige integração habilitada |

Os identificadores são de capabilities headless, não nomes definitivos de tela.
O frontend pode usar os conjuntos como sugestão ao editar explicitamente um
grant para uma empresa selecionada; o servidor continua validando ator, cadeia
de propriedade, destinatário, permissions, CSRF e escopo. Nenhum preset é
aplicado automaticamente por role/cargo. Edit indisponível tem conjunto vazio;
o consumidor não deve interpretar vazio como permissão irrestrita.

Permissões legadas enumeradas não significam que todos os respectivos módulos
já existam. Novos presets só serão adicionados com os endpoints protegidos do
respectivo módulo. DRE contábil não equivale à DRE operacional da referência.

## Produção

O perfil `prod` configura cookie de sessão Secure, HttpOnly e SameSite=Lax.
Ativar `SPRING_PROFILES_ACTIVE=prod` e usar HTTPS na implantação. Não executar
perfil de produção por HTTP para “corrigir” o cookie. O teste verifica as
propriedades versionadas; validação de Set-Cookie no ambiente real/TLS permanece
no roteiro de staging da #53. CSRF segue obrigatório; SameSite não o substitui.
Sessão/limites em memória requerem planejamento de implantação para múltiplas
instâncias; não se alega coordenação distribuída implementada.

## Fechamento da #37

Login/recuperação possuem limites, mudanças de credencial invalidam sessões,
ativação/desativação e administração nos três escopos estão implementadas,
com auditoria transacional e testes de isolamento/mass assignment. Escopo
restrito por centro de custo permanece negado até filtro efetivo; não é
funcionalidade afirmada como pronta. Presets evoluem junto aos demais módulos.
