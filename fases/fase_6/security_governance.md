# Seguranca e Governanca

## Autenticacao

O sistema utiliza **Amazon Cognito** como provedor de identidade. Nenhum microsservico implementa logica de autenticacao propria.

### Fluxo de Login

```
Cliente (Mobile/Desktop)
  |
  | 1. email + senha
  v
[Cognito User Pool]
  |
  | 2. valida credenciais
  | 3. emite tokens JWT (access_token + refresh_token)
  v
Cliente
  |
  | 4. requisicao + access_token no header Authorization
  v
[API Gateway]
  |
  | 5. valida assinatura e expiracao do JWT
  | 6. roteia para o microsservico correto
  v
Microsservico
```

1. O cliente envia email e senha para o Cognito.
2. O Cognito valida as credenciais contra o User Pool.
3. Se valido, o Cognito retorna dois tokens:
   - **access_token** (curta duracao, ~1h) - usado em toda requisicao.
   - **refresh_token** (longa duracao, ~30d) - usado para renovar o access_token sem pedir senha novamente.
4. O cliente inclui o access_token no header `Authorization: Bearer <token>` de toda requisicao.
5. O API Gateway valida o token (assinatura, expiracao, issuer) antes de rotear.
6. Se o token e invalido ou expirado, o Gateway retorna 401 e a requisicao nao chega no microsservico.

### Renovacao de Token

Quando o access_token expira, o cliente usa o refresh_token para obter um novo access_token sem precisar logar novamente. Se o refresh_token tambem expirar, o usuario precisa inserir email e senha de novo.

## Autorizacao

Autenticacao responde "quem e voce". Autorizacao responde "o que voce pode fazer".

### Scopes e Grupos no Cognito

Os usuarios sao organizados em grupos no Cognito User Pool. Cada grupo define quais operacoes o usuario pode executar:

| Grupo | Permissoes | Exemplo |
|---|---|---|
| `cliente` | Consultar catalogo, gerenciar carrinho, criar pedido, consultar seus proprios pedidos | Cliente da vinheria comprando vinhos |
| `operador` | Gerenciar estoque (entrada/saida), consultar todos os pedidos, atualizar status de pedido | Funcionario do armazem |
| `admin` | Acesso total, gerenciar usuarios, consultar relatorios (Gold layer), configurar sistema | Gestor da vinheria |

O grupo do usuario e incluido como claim no token JWT (`cognito:groups`). O API Gateway e os microsservicos verificam esse claim para decidir se a requisicao e permitida.

### Exemplo Pratico

Um usuario do grupo `cliente` tenta acessar `GET /estoque/relatorio`:

```
Cliente (grupo: cliente)
  |
  | GET /estoque/relatorio + access_token
  v
[API Gateway]
  |
  | valida JWT ✓
  | verifica claim cognito:groups = ["cliente"]
  | endpoint requer grupo "operador" ou "admin"
  |
  | ✗ 403 Forbidden
  v
Requisicao bloqueada (nao chega no microsservico)
```

A validacao acontece no API Gateway. O microsservico de estoque nem recebe a requisicao.

## Seguranca na Comunicacao

### Cliente -> API Gateway (HTTPS/TLS)

Toda comunicacao entre o cliente e o API Gateway e criptografada via HTTPS (TLS 1.2+). O certificado e gerenciado pelo AWS Certificate Manager (ACM). Nenhuma requisicao HTTP (sem TLS) e aceita.

### API Gateway -> Microsservicos (VPC + IAM)

Os microsservicos rodam em ECS Fargate dentro de uma VPC privada. Eles nao possuem IP publico e nao sao acessiveis diretamente pela internet. O unico caminho de entrada e pelo API Gateway, que usa IAM roles para se autenticar com os servicos.

```
Internet ──► API Gateway ──► VPC Privada ──► ECS Fargate (microsservicos)
                                              ↕
                                           DynamoDB
                                              ↕
                                           SQS FIFO
```

### Microsservico -> Microsservico (IAM Roles)

Os microsservicos nao se comunicam via REST entre si para operacoes de negocio. A comunicacao assincrona passa pelo Broker (SQS FIFO) e Orquestrador (Step Functions). O acesso a esses recursos e controlado por IAM Roles atribuidas a cada task do ECS Fargate.

Cada servico tem sua propria IAM Role com permissoes minimas (Principle of Least Privilege):

| Servico | Permissoes IAM |
|---|---|
| Carrinho | Read/Write no DynamoDB do carrinho, invocar API do Pedido |
| Pedido | Read/Write no DynamoDB do pedido, publicar mensagens no SQS FIFO |
| Estoque | Read/Write no DynamoDB do estoque, consumir mensagens do SQS FIFO |
| Pagamento | Read/Write no DynamoDB do pagamento, consumir mensagens do SQS FIFO |

O servico de carrinho nao tem permissao para acessar o banco do pagamento. Se tentar, o IAM bloqueia.

### Dados em Repouso (Encryption at Rest)

Todos os dados armazenados sao criptografados automaticamente:

- **DynamoDB** - Criptografia com AWS KMS (chaves gerenciadas pela AWS).
- **SQS FIFO** - Criptografia server-side (SSE) com KMS.
- **S3 (ETL)** - Criptografia SSE-S3 nas camadas Bronze, Silver e Gold.

### Dados em Transito (Encryption in Transit)

Toda comunicacao entre servicos AWS dentro da VPC usa TLS. As conexoes com DynamoDB, SQS e S3 sao feitas via endpoints HTTPS.

## Protecao contra Ataques

### Rate Limiting e Throttling (API Gateway)

O API Gateway limita a quantidade de requisicoes por segundo por cliente. Isso protege contra:

- **Abuso de API** - Um script automatizado tentando criar milhares de pedidos por segundo.
- **DDoS na camada de aplicacao** - Excesso de requisicoes que sobrecarregaria os microsservicos.

Configuracao no API Gateway:

| Parametro | Valor | Funcao |
|---|---|---|
| Rate limit | 100 req/s por cliente | Limite sustentavel de requisicoes |
| Burst limit | 200 req/s | Pico temporario permitido |
| Throttling response | 429 Too Many Requests | Resposta quando o limite e excedido |

### Validacao de Input (API Gateway + Microsservicos)

O API Gateway valida o schema das requisicoes antes de encaminhar para os servicos. Requisicoes com payload malformado sao rejeitadas com 400 Bad Request sem chegar no microsservico.

Os microsservicos tambem validam os dados recebidos (defesa em profundidade). Exemplo: o servico de pedido valida que a quantidade de garrafas e um numero positivo e que o `produtoId` existe.

### WAF (Web Application Firewall)

O AWS WAF e integrado ao API Gateway para bloquear padroes de ataque conhecidos:

- SQL Injection
- Cross-Site Scripting (XSS)
- Requisicoes de IPs maliciosos (listas de reputacao)
- Payloads excedendo o tamanho maximo

## Escalabilidade

A arquitetura e projetada para escalar cada componente de forma independente.

| Componente | Estrategia de Escalabilidade | Como funciona |
|---|---|---|
| API Gateway | Gerenciado pela AWS | Escala automaticamente conforme a demanda. Sem configuracao manual |
| ECS Fargate | Auto Scaling por servico | Cada microsservico escala independentemente com base em CPU/memoria ou numero de requisicoes. Se o carrinho esta com alta demanda, so ele escala |
| DynamoDB | On-Demand Mode | Ajusta capacidade de leitura/escrita automaticamente. Sem provisionar unidades manualmente |
| SQS FIFO | Gerenciado pela AWS | Escala automaticamente. Suporta ate 3.000 mensagens/s com batching |
| Step Functions | Gerenciado pela AWS | Escala automaticamente. Cada execucao da Saga e independente |

### Exemplo Pratico: Black Friday

Durante a Black Friday, o volume de acessos ao carrinho e pedido aumenta 10x. Com a arquitetura atual:

1. O API Gateway absorve o trafego sem configuracao extra.
2. O ECS Fargate do carrinho escala de 2 para 20 tasks automaticamente (Auto Scaling baseado em CPU).
3. O ECS Fargate do pedido escala de 2 para 15 tasks.
4. O estoque e pagamento nao precisam escalar na mesma proporcao porque recebem mensagens via SQS FIFO (o Broker absorve o pico e entrega em ritmo sustentavel).
5. O DynamoDB ajusta a capacidade automaticamente (On-Demand).

O servico de estoque nao precisa acompanhar o pico do carrinho. O Broker funciona como um amortecedor: absorve a rajada de mensagens e entrega ao consumidor no ritmo que ele consegue processar.

## Resiliencia

### Health Checks

O ECS Fargate verifica periodicamente se cada container esta saudavel. Se um container nao responde ao health check (endpoint `/health`), o ECS o substitui automaticamente por um novo.

### Circuit Breaker

Se um microsservico comeca a falhar repetidamente (ex: o servico de pagamento retorna erro 500 em 50% das requisicoes), o circuit breaker abre e para de enviar requisicoes para aquele servico temporariamente. Apos um periodo de espera, tenta novamente. Isso evita o efeito cascata: uma falha no pagamento nao derruba o carrinho e o pedido.

No fluxo assincrono, o Broker (SQS FIFO) + DLQ ja funciona como um circuit breaker natural: mensagens que falham repetidamente sao isoladas na DLQ e o consumidor continua processando as demais.

### Multi-AZ (Disponibilidade)

Todos os servicos rodam em pelo menos 2 Availability Zones (datacenters fisicamente separados dentro da mesma regiao AWS). Se uma AZ inteira cai, os servicos continuam rodando na outra:

- **ECS Fargate** - Tasks distribuidas entre AZs.
- **DynamoDB** - Replica automaticamente em 3 AZs.
- **SQS** - Redundancia automatica entre AZs.

## Observabilidade

Sem observabilidade, nao e possivel identificar problemas antes que afetem o cliente.

| Pilar | Servico AWS | Funcao |
|---|---|---|
| Logs | CloudWatch Logs | Cada microsservico envia logs estruturados (JSON) para o CloudWatch. Permite buscar por `pedidoId`, `erro`, ou qualquer campo |
| Metricas | CloudWatch Metrics | Metricas de latencia, taxa de erro, CPU, memoria, fila SQS (mensagens visiveis, idade da mensagem mais antiga) |
| Tracing | AWS X-Ray | Rastreamento distribuido: acompanha uma requisicao desde o API Gateway ate o ultimo microsservico. Identifica gargalos e falhas em qualquer ponto do fluxo |
| Alertas | CloudWatch Alarms + SNS | Alertas automaticos quando metricas ultrapassam limiares (ex: taxa de erro > 5%, DLQ com mensagens, latencia > 2s) |

### Exemplo: rastreando um pedido com problema

O cliente reclama que o pedido 042 nao foi confirmado. Com X-Ray, o time busca o `pedidoId=042` e ve o trace completo:

```
API Gateway (12ms) → Pedido (45ms) → SQS (3ms) → Step Functions (120ms) → Estoque (TIMEOUT 5000ms) ✗
```

O trace mostra que o servico de estoque deu timeout. O time investiga o CloudWatch Logs do estoque, encontra o erro, corrige e reprocessa a mensagem da DLQ.
