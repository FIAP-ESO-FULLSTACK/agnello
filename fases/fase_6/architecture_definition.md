# Definicao da Arquitetura - Vinheria Agnello

## Microsservicos Identificados

O sistema e composto por 4 microsservicos principais:

- **Pagamento** - Responsavel pelo processamento de pagamentos e transacoes financeiras.
- **Estoque** - Controle de entrada e saida de produtos (garrafas, insumos).
- **Pedido** - Gerenciamento do ciclo de vida dos pedidos dos clientes.
- **Carrinho** - Gestao do carrinho de compras dos clientes na interface web/mobile.

## Diagrama de Arquitetura

O diagrama completo esta no arquivo `WhatsApp Image 2026-04-10 at 22.57.27.jpeg`.

### Estrutura Geral

```
Mobile / Desktop
       |
  User Interface
       |
   API Gateway
   /   |   |   \
pagamento  estoque  pedido  carrinho
```

### Componentes do Diagrama

**Camada de Apresentacao:**
- User Interface acessada via Mobile e Desktop.

**API Gateway:**
- Ponto unico de entrada que roteia as requisicoes para os microsservicos corretos.

**Microsservicos:**
- Cada servico (pagamento, estoque, pedido, carrinho) possui seus proprios bancos de dados independentes
- Os servicos se comunicam entre si de forma assincrona via eventos (Event Async), representados pelas setas entre os servicos no diagrama.

**Pipeline de Dados (ETL):**
- Uma ETL Tool consome dados dos microsservicos e os organiza em tres camadas:
  - **Bronze** - Dados brutos ingeridos dos servicos.
  - **Silver** - Dados limpos e transformados.
  - **Gold** - Dados prontos para consumo pelo Business Domain (relatorios e analytics).

## Forma de Comunicacao

- **Cliente -> Servicos:** Comunicacao sincrona via REST/HTTPS passando pelo API Gateway.
- **Entre microsservicos:** Comunicacao assincrona via eventos (Event Async), permitindo desacoplamento entre os servicos.

## API Gateway

O API Gateway centraliza o acesso aos 4 microsservicos, sendo responsavel por:
- Roteamento das requisicoes para o servico correto.
- Ponto unico de entrada para as interfaces Mobile e Desktop.
