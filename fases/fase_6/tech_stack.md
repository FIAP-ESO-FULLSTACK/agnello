# Stack de Tecnologias - AWS

## Componentes

| Componente | Servico AWS | Funcao |
|---|---|---|
| API Gateway | AWS API Gateway | Ponto unico de entrada, roteamento para os microsservicos |
| Microsservicos | ECS Fargate | Containers serverless rodando cada servico (pagamento, estoque, pedido, carrinho) |
| Mensageria | Amazon SQS FIFO + DLQ | Comunicacao assincrona entre os servicos. FIFO para garantir ordem de processamento (ex: pagamento antes de reembolso, criacao antes de confirmacao de pedido). DLQ (Dead Letter Queue) para evitar o poison message problem: mensagens que falham repetidamente sao isoladas numa fila separada em vez de travar o consumidor em loop infinito |
| Banco de Dados | DynamoDB | Um banco por servico (Database per Service). Suporta consistencia forte (ConsistentRead) e transacoes ACID (TransactWriteItems). Serverless, escala automaticamente e integra nativamente com os demais servicos AWS. Escolhido para todos os servicos pela simplicidade de manter um unico tipo de banco na stack |
| ETL | AWS Glue + S3 | Pipeline de dados nas camadas Bronze, Silver e Gold |
| Autenticacao | Amazon Cognito | Gestao de usuarios e tokens JWT |
