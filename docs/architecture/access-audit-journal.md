# Journal transacional de identidade e acessos

## DECISÃO técnica

O recorder só grava dentro de uma transação existente (`MANDATORY`). A operação e o evento confirmam ou revertem juntos. O instante usa o relógio UTC injetado. A aplicação não publica endpoints de inserção/edição/exclusão de eventos.

O schema recebe somente ação tipada, IDs de ator/usuário afetado/escopo, instante e snapshot de permissões convertido de enums. Não existe payload livre: senha, hash de senha, token, e-mail, mensagem do provedor e configuração de conexão não fazem parte do contrato.

O snapshot representa permissões após a operação; revogação usa conjunto vazio. Ator nulo representa operação sem uma sessão administrativa, não uma identidade humana confirmada. A futura ligação de ativação/reset registrará a identidade comprovada pelo link, sem persistir o segredo.

## Preservação

IDs de registros operacionais são metadados sem FK para usuário/empresa/contabilidade, deliberadamente: futura remoção de uma entidade operacional não deve apagar o histórico. Isso também significa que um ID histórico pode não ser mais resolvido. A tabela de permissões depende apenas do evento.

Append-only pela aplicação não é WORM nem proteção contra administrador do banco. Retenção, exportação e controles operacionais de integridade precisam de política própria. Não declarar imutabilidade absoluta ou conformidade regulatória por causa deste journal.

## Estado da entrega

Migration V14 e recorder interno; nenhum evento de domínio é automaticamente presumido como coberto. Ligação aos fluxos de identidade e grants/estado segue em incrementos de #36/#37. Consulta administrativa paginada e outros domínios continuam em #51.

PostgreSQL real verifica commit, snapshot, rollback e recusa fora de transação. Fixtures e limpeza usam somente IDs sintéticos do teste.
