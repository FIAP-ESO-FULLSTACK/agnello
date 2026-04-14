# Arquitetura de Microsservicos - Vinheria Agnello

---

## 1. Microsservicos e suas Funcoes

O sistema e composto por 4 microsservicos principais:

| Microsservico | Funcao |
|---|---|
| **Carrinho** | Gestao do carrinho de compras dos clientes na interface web/mobile |
| **Pedido** | Gerenciamento do ciclo de vida dos pedidos dos clientes |
| **Estoque** | Controle de entrada e saida de produtos (garrafas, insumos) |
| **Pagamento** | Processamento de pagamentos e transacoes financeiras |

Cada microsservico possui seu proprio banco de dados independente (Database per Service), roda em containers serverless (ECS Fargate) e se comunica com os demais atraves de pecas intermediarias (Broker e Orquestrador), sem chamadas diretas entre servicos.

### Stack de Tecnologias (AWS)

| Componente | Servico AWS | Funcao |
|---|---|---|
| Autenticacao | Amazon Cognito | Gestao de usuarios e emissao de tokens JWT |
| API Gateway | AWS API Gateway | Ponto unico de entrada, roteamento e validacao do token JWT |
| Microsservicos | ECS Fargate | Containers serverless rodando cada servico |
| Broker | Amazon SQS FIFO + DLQ | Fila de mensagens que desacopla os servicos e garante ordem |
| Orquestrador | AWS Step Functions | Coordena a Saga entre os microsservicos |
| Banco de Dados | DynamoDB | Um banco por servico, com suporte a ACID |
| ETL | AWS Glue + S3 | Pipeline de dados nas camadas Bronze, Silver e Gold |

---

## 2. Diagrama de Arquitetura de Microsservicos

O diagrama completo esta no arquivo `fiap.excalidraw`.

### Estrutura Geral

```
                        [Cognito]
                        JWT/Auth
                           |
Cliente ──► API Gateway ──┬──► Carrinho ──► DB
(Mobile /                 │       │
 Desktop)                 │       │ REST sync
                          │       ▼
                          └──► Pedido ──► DB
                                 │
                           ┌─────┘
                           ▼
                       [Broker]         Fluxo Sucesso (1→2→3→5)
                      (SQS FIFO)        Compensacao   (4→6→7→8)
                           │
                           ▼
                    [Orquestrador]
                       (Saga)
                      ╱        ╲
                     ▼          ▼
                 Estoque    Pagamento
                   │ DB        │ DB
```

### Componentes do Diagrama

**Camada de Apresentacao:**
- User Interface acessada via Mobile e Desktop.

**Autenticacao (Amazon Cognito):**
- Gestao de usuarios e emissao de tokens JWT.
- Toda requisicao passa pela validacao do token antes de chegar nos servicos.

**API Gateway:**
- Ponto unico de entrada que roteia as requisicoes para os microsservicos corretos.
- Valida o token JWT emitido pelo Cognito antes de rotear.

**Microsservicos:**
- Cada servico (carrinho, pedido, estoque, pagamento) possui seus proprios bancos de dados independentes (Database per Service).
- Os servicos NAO se comunicam diretamente entre si. Toda comunicacao assincrona passa pelo Broker e Orquestrador.

**Broker (Amazon SQS FIFO):**
- Peca intermediaria entre os microsservicos. Recebe mensagens do servico de pedido e as encaminha para o orquestrador.
- Garante a ordem de processamento (FIFO) e desacoplamento entre os servicos.
- Possui Dead Letter Queue (DLQ) para mensagens que falham repetidamente.

**Orquestrador (Saga):**
- Coordena a sequencia de execucao entre os microsservicos.
- No fluxo de sucesso, aciona estoque e pagamento na ordem correta.
- No fluxo de erro, executa as compensacoes (estorno, liberacao de estoque) na ordem inversa.

**Pipeline de Dados (ETL):**
- Uma ETL Tool (AWS Glue) consome dados dos microsservicos e os organiza em tres camadas no S3:
  - **Bronze** - Dados brutos ingeridos dos servicos.
  - **Silver** - Dados limpos e transformados.
  - **Gold** - Dados prontos para consumo pelo Business Domain (relatorios e analytics).

### Fluxo Numerado

**Fluxo de Sucesso (laranja):**

| Passo | Origem | Destino | Descricao |
|-------|--------|---------|-----------|
| 1 | Pedido | Broker | Pedido publica `pedido_criado` na fila SQS FIFO |
| 2 | Broker | Orquestrador | Broker encaminha a mensagem para o Orquestrador |
| 3 | Orquestrador | Estoque | Orquestrador aciona o Estoque para reservar as garrafas |
| 5 | Orquestrador | Pagamento | Apos confirmacao do estoque, Orquestrador aciona o Pagamento |

**Fluxo de Compensacao (vermelho):**

| Passo | Origem | Destino | Descricao |
|-------|--------|---------|-----------|
| 4 | Estoque | Orquestrador | Estoque reporta falha na reserva |
| 6 | Pagamento | Orquestrador | Pagamento reporta falha na cobranca |
| 7 | Orquestrador | Broker | Orquestrador envia evento de compensacao |
| 8 | Broker | Pedido | Pedido recebe a compensacao e atualiza status para CANCELADO |

---

## 3. Padroes de Arquitetura Escolhidos

### API Gateway Pattern

Centraliza o acesso aos 4 microsservicos (carrinho, pedido, estoque, pagamento) em um ponto unico de entrada.

**Justificativa:** sem um gateway, o cliente (mobile/desktop) precisaria conhecer o endereco de cada servico individualmente. O API Gateway resolve isso roteando as requisicoes para o servico correto. Alem disso, centraliza a autenticacao via Amazon Cognito, validando o token JWT antes de chegar nos servicos.

**Servico AWS:** AWS API Gateway.

### Database per Service Pattern

Cada microsservico possui seu proprio banco de dados independente. Nenhum servico acessa o banco do outro diretamente.

**Justificativa:** no monolito atual, todos os modulos compartilham o mesmo banco. Se ele cai, tudo para. Com bancos separados, uma falha no banco do pagamento nao afeta o carrinho ou o estoque. Quando um servico precisa de dados de outro, ele pede via API ou recebe via mensagem assincrona (SQS).

**Servico AWS:** DynamoDB (um por servico).

### Async Messaging Pattern (Broker)

Os microsservicos se comunicam entre si de forma assincrona via filas de mensagens, em vez de chamadas REST diretas. O **Broker (SQS FIFO)** atua como peca intermediaria explicita entre os servicos: recebe mensagens, garante a ordem e entrega ao destino.

**Justificativa:** se o servico de pedido chamasse o servico de estoque via REST e o estoque estivesse fora do ar, o pedido falharia junto. Com o Broker, o pedido publica a mensagem na fila e segue. O estoque processa quando estiver disponivel. O Broker desacopla os servicos e aumenta a resiliencia.

**Servico AWS:** Amazon SQS FIFO + DLQ.

- **FIFO** porque a ordem importa: um pagamento precisa ser processado antes de um reembolso, uma entrada de estoque antes de uma saida.
- **DLQ** para evitar o poison message problem: mensagens que falham repetidamente sao isoladas em uma fila separada em vez de travar o consumidor.

### Saga Pattern (Orquestrador)

O **Orquestrador** e a peca que coordena a sequencia de execucao entre os microsservicos. Ele recebe as mensagens do Broker e aciona os servicos (estoque, pagamento) na ordem correta. Se algum passo falha, o Orquestrador dispara as compensacoes na ordem inversa.

O FIFO do Broker garante que os eventos cheguem na ordem certa para o Orquestrador funcionar. Juntos (Broker + Orquestrador), permitem que o sistema faca estornos, cancelamentos e compensacoes de forma automatica, com tudo registrado.

**Servico AWS:** AWS Step Functions.

**Fluxo de sucesso (passos 1→2→3→5):**

1. Pedido publica `pedido_criado` no Broker
2. Broker encaminha para o Orquestrador
3. Orquestrador aciona Estoque (reserva garrafas)
5. Orquestrador aciona Pagamento (cobra o cliente)

**Fluxo de compensacao (passos 4→6→7→8):**

4. Estoque reporta falha ao Orquestrador
6. Pagamento reporta falha ao Orquestrador
7. Orquestrador envia compensacao via Broker
8. Broker notifica Pedido (status → CANCELADO)

### Exemplo pratico: cliente compra 5 garrafas e depois cancela

**Fase 1 - Pedido criado com sucesso:**

Banco do pedido:

| id  | status     | data     |
|-----|------------|----------|
| 042 | PENDENTE   | 10:00:01 |
| 042 | CONFIRMADO | 10:00:04 |

Banco do estoque:

| lote | acao    | qtd | data     |
|------|---------|-----|----------|
| 042  | RESERVA | 5   | 10:00:02 |

Banco do pagamento:

| id  | acao     | valor  | data     |
|-----|----------|--------|----------|
| 042 | COBRANCA | R$ 500 | 10:00:03 |

**Fase 2 - Cliente cancela o pedido:**

O servico de pedido recebe o cancelamento e publica `pedido_cancelado` no Broker. O Orquestrador recebe via Broker e aciona cada servico para executar sua compensacao na ordem:

Banco do pedido:

| id  | status     | data     |
|-----|------------|----------|
| 042 | PENDENTE   | 10:00:01 |
| 042 | CONFIRMADO | 10:00:04 |
| 042 | CANCELADO  | 11:00:01 |

Banco do estoque:

| lote | acao      | qtd | data     | motivo                 |
|------|-----------|-----|----------|------------------------|
| 042  | RESERVA   | 5   | 10:00:02 |                        |
| 042  | LIBERACAO | 5   | 11:00:02 | cancelamento do pedido |

Banco do pagamento:

| id  | acao     | valor  | data     | motivo                 |
|-----|----------|--------|----------|------------------------|
| 042 | COBRANCA | R$ 500 | 10:00:03 |                        |
| 042 | ESTORNO  | R$ 500 | 11:00:03 | cancelamento do pedido |

Nada foi apagado. As 5 garrafas voltaram pro estoque, o dinheiro foi estornado, e tudo esta registrado com motivo e data. Isso e compensacao, nao rollback. O historico completo fica preservado.

---

## 4. Comunicacao entre os Servicos

### Visao Geral

O sistema possui 4 microsservicos que se comunicam atraves de duas pecas intermediarias explicitas:

- **Broker (SQS FIFO)** - Fila de mensagens que desacopla os servicos e garante a ordem de processamento.
- **Orquestrador (Saga)** - Coordena a sequencia de execucao e gerencia as compensacoes em caso de falha.

Nenhum microsservico chama outro diretamente via REST para operacoes de negocio. A unica comunicacao sincrona e a do Carrinho com o Pedido (finalizacao de compra) e a consulta de estoque.

### Mapa de Comunicacao

| Origem | Destino | Caminho | Tipo | Evento/Endpoint | Justificativa |
|---|---|---|---|---|---|
| Carrinho | Pedido | Direto (via API GW) | Sincrona (REST) | `POST /pedidos` | O cliente clica em "finalizar compra" e precisa de resposta imediata |
| Estoque | Pedido | Direto (via API GW) | Sincrona (REST) | `GET /estoque/{produtoId}` | Consulta de disponibilidade antes de confirmar |
| Pedido | Broker | Passo 1 | Assincrona (SQS FIFO) | `pedido_criado` | Pedido publica no Broker. Nao precisa esperar os outros servicos |
| Broker | Orquestrador | Passo 2 | Assincrona (SQS FIFO) | Encaminhamento | Broker entrega ao Orquestrador que decide o proximo passo |
| Orquestrador | Estoque | Passo 3 | Assincrona (SQS FIFO) | `reservar_estoque` | Orquestrador aciona reserva |
| Orquestrador | Pagamento | Passo 5 | Assincrona (SQS FIFO) | `processar_pagamento` | Apos confirmacao do estoque, Orquestrador aciona pagamento |
| Estoque | Orquestrador | Passo 4 (erro) | Assincrona (SQS FIFO) | `estoque_falha` | Estoque reporta falha na reserva |
| Pagamento | Orquestrador | Passo 6 (erro) | Assincrona (SQS FIFO) | `pagamento_falha` | Pagamento reporta falha |
| Orquestrador | Broker | Passo 7 (erro) | Assincrona (SQS FIFO) | `compensacao` | Orquestrador envia evento de compensacao via Broker |
| Broker | Pedido | Passo 8 (erro) | Assincrona (SQS FIFO) | `pedido_cancelado` | Pedido recebe compensacao e atualiza status para CANCELADO |

### Comunicacao Sincrona (REST)

Usada quando o cliente esta esperando uma resposta na tela.

**Carrinho -> Pedido:** o cliente clicou em "finalizar compra". Ele esta olhando para a tela esperando a confirmacao. Nao faz sentido colocar isso numa fila e dizer "seu pedido sera criado em breve". A resposta precisa ser imediata.

**Estoque -> Pedido (consulta):** antes de montar o pedido, o servico de pedido consulta o estoque para validar se os itens estao disponiveis. Essa consulta e uma leitura simples (GET) que precisa de resposta imediata para nao travar o fluxo de compra.

Todas as chamadas sincronas passam pelo API Gateway, que valida o token JWT (Cognito) antes de rotear para o servico correto.

### Comunicacao Assincrona (Broker + Orquestrador)

Usada quando o desacoplamento entre os servicos e mais importante que a resposta imediata. Toda mensagem assincrona passa por duas pecas explicitas:

**Broker (SQS FIFO):** recebe mensagens dos produtores, garante a ordem de processamento (FIFO), entrega as mensagens ao consumidor (Orquestrador) e isola mensagens problematicas na DLQ.

**Por que FIFO e nao Standard:** a ordem dos eventos importa. Um `estoque_reservado` precisa chegar antes de um `estoque_liberado` para o mesmo lote. Se a ordem se inverte, o sistema fica inconsistente.

**Orquestrador (Saga):** recebe as mensagens do Broker e coordena a execucao nos servicos de destino. No fluxo de sucesso, aciona Estoque e depois Pagamento na ordem correta. No fluxo de erro, dispara as compensacoes na ordem inversa.

### Diagramas de Fluxo

**Fluxo de Compra (caminho feliz - passos 1→2→3→5):**

```
Cliente
  |
  | POST /pedidos (REST sincrono via API Gateway + Cognito)
  v
Carrinho ────► Pedido
                 |
                 | 1. pedido_criado (SQS FIFO)
                 v
              [Broker]
                 |
                 | 2. encaminha ao Orquestrador
                 v
           [Orquestrador]
              /       \
             | 3.      | 5.
             v         v
          Estoque   Pagamento
          (reserva)  (cobranca)
                 \     /
                  v   v
           Pedido (status → CONFIRMADO)
                 |
                 | pedido_confirmado (SQS FIFO)
                 v
           Carrinho (limpa itens)
```

**Fluxo de Cancelamento (compensacao via Saga - passos 4→6→7→8):**

```
Estoque/Pagamento reporta falha
  |
  | 4/6. falha (SQS FIFO)
  v
[Orquestrador] (identifica passo que falhou)
  |
  |── Estoque (LIBERACAO das garrafas, se ja reservou)
  |── Pagamento (ESTORNO do valor, se ja cobrou)
  |
  | 7. compensacao (SQS FIFO)
  v
[Broker]
  |
  | 8. pedido_cancelado (SQS FIFO)
  v
Pedido (status → CANCELADO)
```

### Resiliencia na Integracao

**Dead Letter Queue (DLQ):** toda fila SQS FIFO tem uma DLQ associada. Se uma mensagem falha 3 vezes consecutivas, ela e movida para a DLQ em vez de travar o consumidor em loop.

**Retry com Backoff:** antes de ir para a DLQ, cada tentativa espera um tempo crescente (1s, 5s, 15s). Isso evita sobrecarregar um servico que esta se recuperando.

**Timeout nas Chamadas Sincronas:** as chamadas REST pelo API Gateway tem timeout de 5 segundos. Se o servico nao responde, o gateway retorna 504.

**Idempotencia:** toda mensagem carrega o `pedidoId` como chave de deduplicacao no SQS FIFO. Se a mesma mensagem for entregue duas vezes, o servico ignora a duplicata. Sem idempotencia, o cliente poderia ser cobrado duas vezes.

---

## 5. Seguranca e Governanca

### Autenticacao

O sistema utiliza **Amazon Cognito** como provedor de identidade. Nenhum microsservico implementa logica de autenticacao propria.

**Fluxo de Login:**

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

Quando o access_token expira, o cliente usa o refresh_token para obter um novo access_token sem precisar logar novamente. Se o refresh_token tambem expirar, o usuario precisa inserir email e senha de novo.

### Autorizacao

Autenticacao responde "quem e voce". Autorizacao responde "o que voce pode fazer".

Os usuarios sao organizados em grupos no Cognito User Pool. Cada grupo define quais operacoes o usuario pode executar:

| Grupo | Permissoes | Exemplo |
|---|---|---|
| `cliente` | Consultar catalogo, gerenciar carrinho, criar pedido, consultar seus proprios pedidos | Cliente da vinheria comprando vinhos |
| `operador` | Gerenciar estoque (entrada/saida), consultar todos os pedidos, atualizar status de pedido | Funcionario do armazem |
| `admin` | Acesso total, gerenciar usuarios, consultar relatorios (Gold layer), configurar sistema | Gestor da vinheria |

O grupo do usuario e incluido como claim no token JWT (`cognito:groups`). O API Gateway e os microsservicos verificam esse claim para decidir se a requisicao e permitida.

**Exemplo pratico:** um usuario do grupo `cliente` tenta acessar `GET /estoque/relatorio`. O API Gateway valida o JWT, verifica que o grupo e `cliente` e que o endpoint requer `operador` ou `admin`, e retorna 403 Forbidden. O microsservico de estoque nem recebe a requisicao.

### Seguranca na Comunicacao

**Cliente -> API Gateway (HTTPS/TLS):** toda comunicacao entre o cliente e o API Gateway e criptografada via HTTPS (TLS 1.2+). O certificado e gerenciado pelo AWS Certificate Manager (ACM). Nenhuma requisicao HTTP (sem TLS) e aceita.

**API Gateway -> Microsservicos (VPC + IAM):** os microsservicos rodam em ECS Fargate dentro de uma VPC privada. Eles nao possuem IP publico e nao sao acessiveis diretamente pela internet. O unico caminho de entrada e pelo API Gateway, que usa IAM roles para se autenticar com os servicos.

```
Internet ──► API Gateway ──► VPC Privada ──► ECS Fargate (microsservicos)
                                              ↕
                                           DynamoDB
                                              ↕
                                           SQS FIFO
```

**Microsservico -> Microsservico (IAM Roles):** a comunicacao assincrona passa pelo Broker (SQS FIFO) e Orquestrador (Step Functions). O acesso a esses recursos e controlado por IAM Roles atribuidas a cada task do ECS Fargate. Cada servico tem sua propria IAM Role com permissoes minimas (Principle of Least Privilege):

| Servico | Permissoes IAM |
|---|---|
| Carrinho | Read/Write no DynamoDB do carrinho, invocar API do Pedido |
| Pedido | Read/Write no DynamoDB do pedido, publicar mensagens no SQS FIFO |
| Estoque | Read/Write no DynamoDB do estoque, consumir mensagens do SQS FIFO |
| Pagamento | Read/Write no DynamoDB do pagamento, consumir mensagens do SQS FIFO |

O servico de carrinho nao tem permissao para acessar o banco do pagamento. Se tentar, o IAM bloqueia.

**Dados em Repouso (Encryption at Rest):** todos os dados armazenados sao criptografados automaticamente - DynamoDB com AWS KMS, SQS FIFO com SSE/KMS, S3 (ETL) com SSE-S3.

**Dados em Transito (Encryption in Transit):** toda comunicacao entre servicos AWS dentro da VPC usa TLS. As conexoes com DynamoDB, SQS e S3 sao feitas via endpoints HTTPS.

### Protecao contra Ataques

**Rate Limiting e Throttling (API Gateway):** o API Gateway limita a quantidade de requisicoes por segundo por cliente. Protege contra abuso de API e DDoS na camada de aplicacao.

| Parametro | Valor | Funcao |
|---|---|---|
| Rate limit | 100 req/s por cliente | Limite sustentavel de requisicoes |
| Burst limit | 200 req/s | Pico temporario permitido |
| Throttling response | 429 Too Many Requests | Resposta quando o limite e excedido |

**Validacao de Input (API Gateway + Microsservicos):** o API Gateway valida o schema das requisicoes antes de encaminhar para os servicos. Requisicoes com payload malformado sao rejeitadas com 400 Bad Request. Os microsservicos tambem validam os dados recebidos (defesa em profundidade).

**WAF (Web Application Firewall):** o AWS WAF e integrado ao API Gateway para bloquear SQL Injection, Cross-Site Scripting (XSS), requisicoes de IPs maliciosos e payloads excedendo o tamanho maximo.

### Escalabilidade

A arquitetura e projetada para escalar cada componente de forma independente.

| Componente | Estrategia de Escalabilidade | Como funciona |
|---|---|---|
| API Gateway | Gerenciado pela AWS | Escala automaticamente conforme a demanda |
| ECS Fargate | Auto Scaling por servico | Cada microsservico escala independentemente com base em CPU/memoria |
| DynamoDB | On-Demand Mode | Ajusta capacidade de leitura/escrita automaticamente |
| SQS FIFO | Gerenciado pela AWS | Escala automaticamente, suporta ate 3.000 mensagens/s com batching |
| Step Functions | Gerenciado pela AWS | Escala automaticamente, cada execucao da Saga e independente |

**Exemplo pratico (Black Friday):** durante a Black Friday, o volume de acessos aumenta 10x. O API Gateway absorve o trafego sem configuracao extra. O ECS Fargate do carrinho escala de 2 para 20 tasks automaticamente. O estoque e pagamento nao precisam escalar na mesma proporcao porque recebem mensagens via SQS FIFO - o Broker funciona como um amortecedor que absorve a rajada e entrega ao consumidor no ritmo que ele consegue processar.

### Resiliencia

**Health Checks:** o ECS Fargate verifica periodicamente se cada container esta saudavel via endpoint `/health`. Se nao responde, o ECS substitui automaticamente por um novo.

**Circuit Breaker:** se um microsservico comeca a falhar repetidamente, o circuit breaker abre e para de enviar requisicoes temporariamente. No fluxo assincrono, o Broker + DLQ ja funciona como um circuit breaker natural.

**Multi-AZ (Disponibilidade):** todos os servicos rodam em pelo menos 2 Availability Zones. Se uma AZ inteira cai, os servicos continuam rodando na outra. O DynamoDB replica automaticamente em 3 AZs.

### Observabilidade

| Pilar | Servico AWS | Funcao |
|---|---|---|
| Logs | CloudWatch Logs | Logs estruturados (JSON) por microsservico. Busca por `pedidoId`, `erro`, etc |
| Metricas | CloudWatch Metrics | Latencia, taxa de erro, CPU, memoria, fila SQS |
| Tracing | AWS X-Ray | Rastreamento distribuido de ponta a ponta |
| Alertas | CloudWatch Alarms + SNS | Alertas automaticos quando metricas ultrapassam limiares |

**Exemplo:** o cliente reclama que o pedido 042 nao foi confirmado. Com X-Ray, o time busca o `pedidoId=042` e ve o trace completo:

```
API Gateway (12ms) → Pedido (45ms) → SQS (3ms) → Step Functions (120ms) → Estoque (TIMEOUT 5000ms) ✗
```

O trace mostra que o servico de estoque deu timeout. O time investiga os logs, corrige e reprocessa a mensagem da DLQ.

---

## Referencias

- [Saga Pattern para Microservices](https://dev.to/thiagosilva95/saga-pattern-para-microservices-2pb6)
