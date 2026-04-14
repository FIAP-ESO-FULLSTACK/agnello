# Definicao da Arquitetura - Vinheria Agnello

## Microsservicos Identificados

O sistema e composto por 4 microsservicos principais:

- **Carrinho** - Gestao do carrinho de compras dos clientes na interface web/mobile.
- **Pedido** - Gerenciamento do ciclo de vida dos pedidos dos clientes.
- **Estoque** - Controle de entrada e saida de produtos (garrafas, insumos).
- **Pagamento** - Responsavel pelo processamento de pagamentos e transacoes financeiras.

## Diagrama de Arquitetura

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

## Forma de Comunicacao

- **Cliente -> Servicos:** Comunicacao sincrona via REST/HTTPS passando pelo API Gateway (que valida JWT via Cognito).
- **Carrinho -> Pedido:** Comunicacao sincrona via REST. O cliente clica em "finalizar compra" e precisa de resposta imediata.
- **Pedido -> Broker -> Orquestrador -> Estoque/Pagamento:** Comunicacao assincrona via SQS FIFO. O Broker recebe a mensagem, o Orquestrador coordena a execucao nos servicos de destino.

## Fluxo Numerado (conforme diagrama)

### Fluxo de Sucesso (laranja)

| Passo | Origem | Destino | Descricao |
|-------|--------|---------|-----------|
| 1 | Pedido | Broker | Pedido publica `pedido_criado` na fila SQS FIFO |
| 2 | Broker | Orquestrador | Broker encaminha a mensagem para o Orquestrador |
| 3 | Orquestrador | Estoque | Orquestrador aciona o Estoque para reservar as garrafas |
| 5 | Orquestrador | Pagamento | Apos confirmacao do estoque, Orquestrador aciona o Pagamento |

### Fluxo de Compensacao (vermelho)

| Passo | Origem | Destino | Descricao |
|-------|--------|---------|-----------|
| 4 | Estoque | Orquestrador | Estoque reporta falha na reserva |
| 6 | Pagamento | Orquestrador | Pagamento reporta falha na cobranca |
| 7 | Orquestrador | Broker | Orquestrador envia evento de compensacao |
| 8 | Broker | Pedido | Pedido recebe a compensacao e atualiza status para CANCELADO |
