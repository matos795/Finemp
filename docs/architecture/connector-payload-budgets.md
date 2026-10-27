# Orçamentos dos pedidos do Connector — Issue #102

## FATO IMPLEMENTADO

Filtro exclusivo da cadeia de máquina, executado após validar HTTPS/bearer. Demais APIs conservam seu comportamento. Corpo é limitado antes do binding de DTOs: Content-Length excessivo é recusado imediatamente e a leitura efetiva para no limite +1, incluindo pedidos sem comprimento declarado. O corpo aceito fica repetível e limitado em memória para o parser.

Content-Encoding comprimido é recusado; não há descompressão no servidor. Todos os caminhos têm orçamento pequeno, com orçamento de lote somente no futuro POST `/api/connector/v1/work/{attemptId}/complete` com UUID válido. O filtro não cria esse endpoint nem publica dados.

Semáforo sem fila limita pedidos simultâneos, inclusive buffering e processamento. Falha de leitura, rejeição e exceção do handler devolvem a capacidade. Erros 400/413/415/429 são constantes no-store, sem corpo original, header, segredo ou erro livre. 429 de capacidade usa Retry-After 1s; limite de autenticação da #98 continua usando 60s.

## DECISÃO TÉCNICA

Defaults: lote 8 MiB, metadados 2 KiB, concorrência 2. Configuração admite lote 1 KiB–16 MiB, metadados 64 bytes–64 KiB (sem exceder lote), concorrência 1–8. São políticas de recursos reversíveis, não regras contábeis. Buffer limitado evita ler payload arbitrariamente grande; não há persistência/logging de payload.

## PONTO ABERTO / PRÓXIMO PASSO

O parser do endpoint de lote posterior também precisa limitar quantidade de registros antes de materializá-los, validar contexto/identidade observada e reutilizar publicação atômica. Só limitar bytes não é uma promessa de limite total do heap de qualquer DTO futuro.

Timeout de leitura/conexão, limite de ingress e rate limit distribuído são gates de implantação. Semáforo não elimina clientes lentos nem garante encerrar um socket bloqueado; o servidor/proxy precisa de prazo de upload. Nenhum ajuste de infraestrutura foi executado e não há dados reais nos testes.
