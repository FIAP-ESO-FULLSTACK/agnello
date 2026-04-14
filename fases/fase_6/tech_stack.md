# Stack de Tecnologias - AWS

## Componentes

| Componente | Servico AWS | Funcao |
|---|---|---|
| Autenticacao | Amazon Cognito | Gestao de usuarios e emissao de tokens JWT. Toda requisicao e validada antes de chegar nos servicos |
| API Gateway | AWS API Gateway | Ponto unico de entrada, roteamento para os microsservicos, validacao do token JWT |
| Microsservicos | ECS Fargate | Containers serverless rodando cada servico (carrinho, pedido, estoque, pagamento) |
| Broker | Amazon SQS FIFO + DLQ | Fila de mensagens que desacopla os servicos. Recebe mensagens dos produtores e entrega ao Orquestrador. FIFO garante ordem, DLQ isola mensagens problematicas |
| Orquestrador | AWS Step Functions | Coordena a sequencia de execucao da Saga entre os microsservicos. Gerencia o fluxo de sucesso (estoque → pagamento) e as compensacoes em caso de falha |
| Banco de Dados | DynamoDB | Um banco por servico (Database per Service). Suporta consistencia forte (ConsistentRead) e transacoes ACID (TransactWriteItems). Serverless, escala automaticamente |
| ETL | AWS Glue + S3 | Pipeline de dados nas camadas Bronze, Silver e Gold |

## Broker (SQS FIFO + DLQ)

O Broker e implementado como filas Amazon SQS FIFO. Ele e a peca intermediaria entre os servicos no fluxo assincrono:

- **Recebe** mensagens dos produtores (ex: Pedido publica `pedido_criado`)
- **Garante ordem** de processamento (FIFO) - essencial para a Saga funcionar
- **Entrega** as mensagens ao Orquestrador
- **Isola falhas** via DLQ - mensagens que falham repetidamente nao travam o consumidor

Sem o Broker, os servicos precisariam chamar uns aos outros diretamente, criando acoplamento e fragilidade.

## Orquestrador (Step Functions)

O Orquestrador e implementado como AWS Step Functions. Ele coordena a Saga:

- Recebe eventos do Broker
- Aciona Estoque e Pagamento na ordem correta
- Monitora o resultado de cada passo
- Em caso de falha, dispara compensacoes na ordem inversa

Step Functions foi escolhido porque oferece maquina de estados visual, retry automatico, tratamento de erros nativo e integracao direta com SQS e ECS.

## Por que ACID importa no DynamoDB

ACID sao 4 garantias que o banco oferece para manter os dados integros:

- **Atomicidade** - Ou tudo acontece ou nada acontece. Se precisa salvar o pedido e descontar o estoque na mesma operacao, ou os dois salvam ou nenhum salva. (`TransactWriteItems`)
- **Consistencia** - O dado lido e o dado real e atualizado. (`ConsistentRead: true`)
- **Isolamento** - Operacoes simultaneas nao interferem entre si. Dois clientes comprando a ultima garrafa ao mesmo tempo: so um consegue.
- **Durabilidade** - Depois que o banco confirmou, o dado nao se perde. O DynamoDB replica automaticamente em 3 datacenters.

Sem ACID, seria possivel cobrar o cliente e o pedido nao ser salvo, ou dois clientes comprarem a mesma garrafa.
