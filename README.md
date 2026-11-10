# Finemp

SaaS contábil voltado inicialmente à apresentação e análise gerencial de DRE a partir de dados contábeis sincronizados.

> O projeto está em desenvolvimento e o repositório é privado.

## Stack atual

### Backend

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Flyway
- Spring Data JPA
- Testcontainers

### Infraestrutura de desenvolvimento

- Docker
- Docker Compose
- GitHub Actions

## Estrutura do repositório

```text
finemp/
├── backend/
├── docs/
├── .github/
└── compose.yaml
```

O projeto utiliza inicialmente uma arquitetura de monólito modular.

## Requisitos locais

- Java 21
- Docker
- Git

Não é necessário instalar Maven globalmente, pois o projeto utiliza Maven Wrapper.

## Banco de dados local

A senha do PostgreSQL local não fica no repositório. Antes do primeiro uso, na raiz do repositório:

```bash
cp .env.example .env
```

Preencha `FINEMP_LOCAL_DB_PASSWORD` no `.env` com um valor só seu (o `.env` é ignorado pelo Git). Se você já tem o volume `finemp-postgres-data` criado, use a mesma senha com que ele foi inicializado.

Depois:

```bash
docker compose up -d
```

O PostgreSQL local fica disponível em:

```text
localhost:5433
```

A porta `5432` continua sendo utilizada internamente pelo container.

Para verificar o ambiente:

```bash
docker compose ps
```

Para encerrar:

```bash
docker compose down
```

## Executando o backend

No PowerShell:

```powershell
cd backend

$env:SPRING_PROFILES_ACTIVE="local"
$env:FINEMP_LOCAL_DB_PASSWORD="<mesmo valor do .env>"
.\mvnw.cmd spring-boot:run
```

A aplicação fica disponível em:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/actuator/health
```

Ao terminar:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE
Remove-Item Env:FINEMP_LOCAL_DB_PASSWORD
```

## Testes

No Windows:

```powershell
cd backend
.\mvnw.cmd test
```

Verificação equivalente à utilizada no CI:

```powershell
.\mvnw.cmd verify
```

Os testes de integração utilizam PostgreSQL real por meio de Testcontainers.

Eles não dependem do PostgreSQL iniciado pelo Docker Compose.

## Banco e migrations

O schema da aplicação é controlado pelo Flyway.

As migrations ficam em:

```text
backend/src/main/resources/db/migration
```

O Hibernate está configurado com:

```text
ddl-auto=validate
```

Portanto, alterações de schema devem ser feitas por migrations e não geradas automaticamente pelo Hibernate.

## Processo de desenvolvimento

O fluxo padrão de engenharia está documentado em:

```text
docs/engineering-playbook.md
```