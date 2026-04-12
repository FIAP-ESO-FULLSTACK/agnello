# Stack de Tecnologias - AWS

## Componentes

| Componente | Servico AWS | Funcao |
|---|---|---|
| API Gateway | AWS API Gateway | Ponto unico de entrada, roteamento para os microsservicos |
| Microsservicos | ECS Fargate | Containers serverless rodando cada servico (pagamento, estoque, pedido, carrinho) |
| Mensageria | Amazon SQS FIFO + DLQ | Comunicacao assincrona entre os servicos. FIFO para garantir ordem de processamento (ex: pagamento antes de reembolso, criacao antes de confirmacao de pedido). DLQ (Dead Letter Queue) para evitar o poison message problem: mensagens que falham repetidamente sao isoladas numa fila separada em vez de travar o consumidor em loop infinito |
| Banco de Dados | DynamoDB | Um banco por servico (Database per Service). Suporta consistencia forte (ConsistentRead) e transacoes ACID (TransactWriteItems). Serverless, escala automaticamente e integra nativamente com os demais servicos AWS. Escolhido para todos os servicos pela simplicidade de manter um unico tipo de banco na stack |

## Por que ACID importa no DynamoDB

ACID sao 4 garantias que o banco oferece para manter os dados integros:

- **Atomicidade** - Ou tudo acontece ou nada acontece. Se precisa salvar o pedido e descontar o estoque na mesma operacao, ou os dois salvam ou nenhum salva. (`TransactWriteItems`)
- **Consistencia** - O dado lido e o dado real e atualizado. (`ConsistentRead: true`)
- **Isolamento** - Operacoes simultaneas nao interferem entre si. Dois clientes comprando a ultima garrafa ao mesmo tempo: so um consegue.
- **Durabilidade** - Depois que o banco confirmou, o dado nao se perde. O DynamoDB replica automaticamente em 3 datacenters.

Sem ACID, seria possivel cobrar o cliente e o pedido nao ser salvo, ou dois clientes comprarem a mesma garrafa.
| ETL | AWS Glue + S3 | Pipeline de dados nas camadas Bronze, Silver e Gold |
| Autenticacao | Amazon Cognito | Gestao de usuarios e tokens JWT |
