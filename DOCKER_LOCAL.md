# Como Subir o Docker Local e Testar as Rotas

Este guia sobe localmente os microsservicos da vinheria que estao em:

```text
./infra-microsservicos-java
```

Os servicos sao:

- `estoque-service`
- `carrinho-service`

## Pre-requisitos

- Docker Desktop ou Docker Engine instalado
- Docker Compose habilitado

Para conferir:

```bash
docker --version
docker compose version
```

## Subir os containers

No diretório raiz do projeto:

```bash
cd ./infra-microsservicos-java
docker compose up --build
```

Se quiser rodar em background:

```bash
docker compose up --build -d
```

## Verificar se os containers subiram

```bash
docker ps
docker compose ps
```

Voce deve ver os containers:

- `estoque-service`
- `carrinho-service`

## Testar as rotas no navegador

Abra no navegador:

- [http://localhost:8000/](http://localhost:8000/)
- [http://localhost:8000/estoque](http://localhost:8000/estoque)
- [http://localhost:8000/health](http://localhost:8000/health)
- [http://localhost:8001/](http://localhost:8001/)
- [http://localhost:8001/carrinho](http://localhost:8001/carrinho)
- [http://localhost:8001/health](http://localhost:8001/health)

## Testar as rotas com curl

```bash
curl -sS http://localhost:8000/
curl -sS http://localhost:8000/estoque
curl -sS http://localhost:8000/health
curl -sS http://localhost:8001/
curl -sS http://localhost:8001/carrinho
curl -sS http://localhost:8001/health
```

## O que cada serviço faz

- `estoque-service`: retorna a lista de vinhos disponíveis no estoque
- `carrinho-service`: retorna o carrinho de compras e consulta o `estoque-service` internamente

O `carrinho-service` chama este endereço dentro da rede Docker:

```text
http://estoque-service:8080/estoque
```

Isso demonstra o DNS interno do Docker Compose.

## Testar a comunicação interna entre os containers

Entrar no container do carrinho:

```bash
docker exec -it carrinho-service sh
```

Dentro do container, testar resolução DNS:

```bash
getent hosts estoque-service
```

Resultado esperado: o nome `estoque-service` deve resolver para um IP da rede Docker, por exemplo `172.30.0.x`.

Ainda dentro do container, testar a chamada HTTP interna:

```bash
curl http://estoque-service:8080/estoque
curl http://estoque-service:8080/health
```

Se esses comandos responderem corretamente, a comunicacao entre os microsservicos esta funcionando.

## Verificar IP automático dos containers

O Docker atribui os IPs automaticamente na rede `lab-network`, simulando DHCP.

```bash
docker inspect estoque-service
docker inspect carrinho-service
```

Se quiser ver apenas o IP:

```bash
docker inspect estoque-service --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
docker inspect carrinho-service --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

## Parar os containers

No diretorio `./infra-microsservicos-java`:

```bash
docker compose down
```

Se quiser remover containers antigos nao utilizados:

```bash
docker compose down --remove-orphans
```

## Resumo rápido

```bash
cd ./infra-microsservicos-java
docker compose up --build -d
curl -sS http://localhost:8000/estoque
curl -sS http://localhost:8001/carrinho
docker exec -it carrinho-service sh
```
