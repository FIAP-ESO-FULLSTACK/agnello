# Descobertas

## Estado Atual do Repositorio

Hoje o repositorio utiliza uma arquitetura monolitica. Toda a aplicacao (pagamento, estoque, pedido, carrinho) esta empacotada em um unico arquivo WAR que faz deploy em um unico servidor.

## Problemas Identificados

- **Ponto unico de falha:** se algo cair, como o modulo de pagamento, tudo e perdido. Todos os modulos compartilham o mesmo processo e o mesmo banco de dados.
- **Alta dependencia:** os modulos estao fortemente acoplados entre si. Uma mudanca no estoque pode quebrar o pedido, porque tudo roda junto e acessa os mesmos dados diretamente.
- **Nao escala:** nao e possivel escalar um modulo individualmente. Se o carrinho esta com alta demanda, voce precisa escalar a aplicacao inteira, desperdicando recursos nos modulos que nao precisam.
- **Banco de dados compartilhado:** um unico banco atende todos os modulos. Se o banco fica lento ou cai, o sistema inteiro para.
- **Sem DLQ (Dead Letter Queue):** sem uma fila de mensagens mortas, uma mensagem que falha repetidamente trava o consumidor em loop infinito (poison message problem). Com DLQ, mensagens problematicas sao isoladas e o servico continua funcionando.
- **Ordem de processamento:** servicos como pagamento, pedido e estoque dependem da ordem em que as mensagens sao processadas. Um reembolso antes do pagamento ou uma saida de estoque antes da entrada gera inconsistencia. Por isso a escolha de SQS FIFO em vez de Standard.
