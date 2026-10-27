# Journal transacional de identidade e acessos

## DECISÃO técnica

O recorder só grava dentro de uma transação existente (`MANDATORY`). A operação e o evento confirmam ou revertem juntos. O instante usa o relógio UTC injetado. A aplicação não publica endpoints de inserção/edição/exclusão de eventos.

O schema recebe somente ação tipada, IDs de ator/usuário afetado/escopo, instante e snapshot de permissões convertido de enums. Não existe payload livre: senha, hash de senha, token, e-mail, mensagem do provedor e configuração de conexão não fazem parte do contrato.

O snapshot representa permissões após a operação; revogação usa conjunto vazio. Ator nulo representa operação sem uma sessão administrativa, não uma identidade humana confirmada. A futura ligação de ativação/reset registrará a identidade comprovada pelo link, sem persistir o segredo.

## Preservação

IDs de registros operacionais são metadados sem FK para usuário/empresa/contabilidade, deliberadamente: futura remoção de uma entidade operacional não deve apagar o histórico. Isso também significa que um ID histórico pode não ser mais resolvido. A tabela de permissões depende apenas do evento.

Append-only pela aplicação não é WORM nem proteção contra administrador do banco. Retenção, exportação e controles operacionais de integridade precisam de política própria. Não declarar imutabilidade absoluta ou conformidade regulatória por causa deste journal.

## Estado da entrega

Migration V14 e recorder interno. A ligação de identidade registra emissão de convite/reset, ativação, senha alterada e concessão administrativa por convite. O ator administrativo é propagado no convite; recuperação pública não fabrica ator de sessão. Ativação/reset concluídos registram a identidade comprovada pelo link. Replays/falhas não geram evento de sucesso. Snapshot de convite representa as permissões administrativas finais, sem grant financeiro implícito.

Demais mutações de grants/estado seguem #37; consulta administrativa paginada e outros domínios continuam em #51. Nenhum evento não ligado explicitamente ao recorder é presumido como coberto.

PostgreSQL real verifica commit, snapshot, rollback e recusa fora de transação. Fixtures e limpeza usam somente IDs sintéticos do teste.
