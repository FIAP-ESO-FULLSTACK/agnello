# Padroes de Arquitetura Aplicaveis

## API Gateway Pattern

Centraliza o acesso aos 4 microsservicos (pagamento, estoque, pedido, carrinho) em um ponto unico de entrada.

**Justificativa:** sem um gateway, o cliente (mobile/desktop) precisaria conhecer o endereco de cada servico individualmente. O API Gateway resolve isso roteando as requisicoes para o servico correto. Alem disso, centraliza a autenticacao via Amazon Cognito, validando o token JWT antes de chegar nos servicos.

**Servico AWS:** AWS API Gateway.

## Database per Service Pattern

Cada microsservico possui seu proprio banco de dados independente. Nenhum servico acessa o banco do outro diretamente.

**Justificativa:** no monolito atual, todos os modulos compartilham o mesmo banco. Se ele cai, tudo para. Com bancos separados, uma falha no banco do pagamento nao afeta o carrinho ou o estoque. Quando um servico precisa de dados de outro, ele pede via API ou recebe via mensagem assincrona (SQS).

**Servico AWS:** DynamoDB (um por servico).

## Async Messaging Pattern

Os microsservicos se comunicam entre si de forma assincrona via filas de mensagens, em vez de chamadas REST diretas.

**Justificativa:** se o servico de pedido chamasse o servico de estoque via REST e o estoque estivesse fora do ar, o pedido falharia junto. Com mensageria, o pedido publica a mensagem na fila e segue. O estoque processa quando estiver disponivel. Isso desacopla os servicos e aumenta a resiliencia.

**Servico AWS:** Amazon SQS FIFO + DLQ.

- **FIFO** porque a ordem importa: um pagamento precisa ser processado antes de um reembolso, uma entrada de estoque antes de uma saida.
- **DLQ** para evitar o poison message problem: mensagens que falham repetidamente sao isoladas em uma fila separada em vez de travar o consumidor.

## Saga Pattern

O Saga coordena a sequencia de execucao dos microsservicos. O FIFO garante que os eventos cheguem na ordem certa pra isso funcionar. Juntos, permitem que o sistema faca estornos, cancelamentos e compensacoes de forma automatica, com tudo registrado.

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

O servico de pedido recebe o cancelamento e publica "pedido_cancelado" na fila FIFO. Cada servico escuta e executa sua compensacao na ordem:

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

## Referencia

- [Saga Pattern para Microservices](https://dev.to/thiagosilva95/saga-pattern-para-microservices-2pb6)
