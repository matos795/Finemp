# Hierarquia SaaS do FINEMP

## Status

- **DECISÃO:** o domínio representa níveis distintos para Contabilidade, Cliente, Empresa e Fonte Contábil.
- **DECISÃO:** um Cliente pode possuir várias Empresas/CNPJs.
- **DECISÃO:** uma Empresa pode possuir várias fontes contábeis.
- **DECISÃO:** uma futura visão consolidada pertence ao Cliente e recebe uma seleção explícita de empresas.
- **DECISÃO:** a API de [administração SaaS](saas-administration.md) associa controladamente empresas legadas ao Cliente, com autorização dupla, lock e auditoria. A escolha do Cliente correto para cada empresa real ainda exige validação; não há saneamento automático.

## Modelo

```text
FINEMP
└── AccountingFirm (Contabilidade)
    └── Client (Cliente atendido)
        └── Company (Empresa/CNPJ)
            └── AccountingSource (origem contábil externa)
```

Os níveis são tabelas e entidades distintas. Isso evita misturar responsabilidades e permite aplicar isolamento e administração no nível correto.

## Compatibilidade da migration V10

A migration cria `accounting_firms` e `clients` e adiciona `companies.client_id` como vínculo opcional durante a transição.

O vínculo é inicialmente anulável por um motivo deliberado: bancos já implantados podem conter empresas, mas o backend não possui informação suficiente para decidir a qual Cliente elas pertencem. Criar automaticamente um Cliente por empresa ou colocar todas em um Cliente genérico inventaria uma relação de negócio.

Consequências:

- empresas existentes permanecem intactas e acessíveis pelos grants atuais;
- novas APIs administrativas deverão exigir Cliente para novas Empresas;
- o saneamento futuro associa as empresas existentes ao Cliente validado;
- depois do saneamento e de uma verificação de ausência de nulos, uma migration futura poderá tornar `client_id` obrigatório;
- relatórios consolidados não serão implementados enquanto a seleção e o acesso às empresas participantes não estiverem definidos.

Essa transição é explícita e reversível; não altera CNPJ, movimentos, contas, classificações, fontes ou grants existentes.

