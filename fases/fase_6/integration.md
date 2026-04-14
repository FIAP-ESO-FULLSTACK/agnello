# Integracao entre Microsservicos

## Visao Geral

O sistema possui 4 microsservicos (carrinho, pedido, estoque, pagamento) que se comunicam atraves de duas pecas intermediarias explicitas:

- **Broker (SQS FIFO)** - Fila de mensagens que desacopla os servicos e garante a ordem de processamento.
- **Orquestrador (Saga)** - Coordena a sequencia de execucao e gerencia as compensacoes em caso de falha.

Nenhum microsservico chama outro diretamente via REST para operacoes de negocio. A unica comunicacao sincrona e a do Carrinho com o Pedido (finalizacao de compra) e a consulta de estoque.

## Mapa de Comunicacao

| Origem | Destino | Caminho | Tipo | Evento/Endpoint | Justificativa |
|---|---|---|---|---|---|
| Carrinho | Pedido | Direto (via API GW) | Sincrona (REST) | `POST /pedidos` | O cliente clica em "finalizar compra" e precisa de resposta imediata |
| Estoque | Pedido | Direto (via API GW) | Sincrona (REST) | `GET /estoque/{produtoId}` | Consulta de disponibilidade antes de confirmar. O cliente precisa saber na hora |
| Pedido | Broker | Passo 1 | Assincrona (SQS FIFO) | `pedido_criado` | Pedido publica no Broker. Nao precisa esperar os outros servicos |
| Broker | Orquestrador | Passo 2 | Assincrona (SQS FIFO) | Encaminhamento | Broker entrega ao Orquestrador que decide o proximo passo |
| Orquestrador | Estoque | Passo 3 | Assincrona (SQS FIFO) | `reservar_estoque` | Orquestrador aciona reserva. Se estoque indisponivel, compensacao e disparada |
| Orquestrador | Pagamento | Passo 5 | Assincrona (SQS FIFO) | `processar_pagamento` | Apos confirmacao do estoque, Orquestrador aciona pagamento |
| Estoque | Orquestrador | Passo 4 (erro) | Assincrona (SQS FIFO) | `estoque_falha` | Estoque reporta falha na reserva. Orquestrador inicia compensacao |
| Pagamento | Orquestrador | Passo 6 (erro) | Assincrona (SQS FIFO) | `pagamento_falha` | Pagamento reporta falha. Orquestrador inicia compensacao |
| Orquestrador | Broker | Passo 7 (erro) | Assincrona (SQS FIFO) | `compensacao` | Orquestrador envia evento de compensacao via Broker |
| Broker | Pedido | Passo 8 (erro) | Assincrona (SQS FIFO) | `pedido_cancelado` | Pedido recebe compensacao e atualiza status para CANCELADO |

## Comunicacao Sincrona (REST)

Usada quando o cliente esta esperando uma resposta na tela.

**Carrinho -> Pedido:** o cliente clicou em "finalizar compra". Ele esta olhando para a tela esperando a confirmacao. Nao faz sentido colocar isso numa fila e dizer "seu pedido sera criado em breve". A resposta precisa ser imediata.

**Estoque -> Pedido (consulta):** antes de montar o pedido, o servico de pedido consulta o estoque para validar se os itens estao disponiveis. Essa consulta e uma leitura simples (GET) que precisa de resposta imediata para nao travar o fluxo de compra.

Todas as chamadas sincronas passam pelo API Gateway, que valida o token JWT (Cognito) antes de rotear para o servico correto.

## Comunicacao Assincrona (Broker + Orquestrador)

Usada quando o desacoplamento entre os servicos e mais importante que a resposta imediata. Toda mensagem assincrona passa por duas pecas explicitas:

### Broker (SQS FIFO)

O Broker e a fila de mensagens entre os servicos. Ele:

- Recebe mensagens dos produtores (ex: Pedido publica `pedido_criado`)
- Garante a ordem de processamento (FIFO)
- Entrega as mensagens ao consumidor (Orquestrador)
- Isola mensagens problematicas na DLQ

**Por que FIFO e nao Standard:** a ordem dos eventos importa. Exemplos:

- Um `estoque_reservado` precisa chegar antes de um `estoque_liberado` para o mesmo lote
- Um `pagamento_confirmado` precisa chegar antes de um `pagamento_estornado` para o mesmo pedido
- Um `pedido_criado` precisa chegar antes de um `pedido_cancelado`

Se a ordem se inverte, o sistema fica inconsistente: tenta estornar um pagamento que ainda nao foi cobrado, ou liberar um estoque que ainda nao foi reservado.

### Orquestrador (Saga)

O Orquestrador recebe as mensagens do Broker e coordena a execucao nos servicos de destino:

- No fluxo de sucesso, aciona Estoque (passo 3) e depois Pagamento (passo 5) na ordem correta
- No fluxo de erro, recebe a falha do servico (passos 4 ou 6) e dispara as compensacoes na ordem inversa (passos 7 e 8)

O Orquestrador conhece a sequencia completa da Saga e sabe quais compensacoes disparar dependendo de qual passo falhou.

## Diagrama de Fluxo

### Fluxo de Compra (caminho feliz - passos 1→2→3→5)

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

### Fluxo de Cancelamento (compensacao via Saga - passos 4→6→7→8)

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

## Resiliencia na Integracao

### Dead Letter Queue (DLQ)

Toda fila SQS FIFO (no Broker) tem uma DLQ associada. Se uma mensagem falha 3 vezes consecutivas (configuravel), ela e movida para a DLQ em vez de travar o consumidor em loop.

Exemplo: o servico de pagamento esta fora do ar. A mensagem `processar_pagamento` falha 3 vezes. Ela vai para a DLQ. O Broker continua processando outras mensagens normalmente. Quando o time identifica o problema, reprocessa as mensagens da DLQ manualmente ou via automacao.

### Retry com Backoff

Antes de ir para a DLQ, cada tentativa de processamento espera um tempo crescente (1s, 5s, 15s). Isso evita sobrecarregar um servico que esta se recuperando de uma falha temporaria.

### Timeout nas Chamadas Sincronas

As chamadas REST pelo API Gateway tem timeout configurado. Se o servico de estoque nao responde em 5 segundos, o gateway retorna erro 504 para o cliente em vez de ficar esperando indefinidamente.

### Idempotencia

Toda mensagem carrega o `pedidoId` como chave de deduplicacao no SQS FIFO. Se a mesma mensagem for entregue duas vezes (falha de rede, retry), o servico identifica que ja processou aquele evento e ignora a duplicata. Sem idempotencia, o cliente poderia ser cobrado duas vezes pelo mesmo pedido.
