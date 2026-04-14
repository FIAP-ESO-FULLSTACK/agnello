# Vinheria Agnello

Sistema de e-commerce da Vinheria Agnello, modelado com arquitetura de microsservicos e APIs na AWS.

## Arquitetura

O diagrama completo esta no arquivo [`fiap.excalidraw`](fases/fase_6/fiap.excalidraw) (abrir com a extensao [Excalidraw](https://marketplace.visualstudio.com/items?itemName=pomdtr.excalidraw-editor) no VS Code ou em [excalidraw.com](https://excalidraw.com)).

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

### Microsservicos

| Servico | Funcao |
|---|---|
| **Carrinho** | Gestao do carrinho de compras (web/mobile) |
| **Pedido** | Ciclo de vida dos pedidos |
| **Estoque** | Controle de entrada e saida de produtos |
| **Pagamento** | Processamento de pagamentos e transacoes |

### Stack (AWS)

| Componente | Servico |
|---|---|
| Autenticacao | Amazon Cognito (JWT) |
| API Gateway | AWS API Gateway |
| Microsservicos | ECS Fargate |
| Broker | Amazon SQS FIFO + DLQ |
| Orquestrador | AWS Step Functions (Saga) |
| Banco de Dados | DynamoDB (um por servico) |
| ETL | AWS Glue + S3 (Bronze/Silver/Gold) |

## Documentacao (Fase 6)

- [`entrega.md`](fases/fase_6/entrega.md) - Documento completo para entrega (PDF)
- [`architecture_definition.md`](fases/fase_6/architecture_definition.md) - Definicao da arquitetura e diagrama
- [`architecture_patterns.md`](fases/fase_6/architecture_patterns.md) - Padroes de arquitetura e justificativas
- [`integration.md`](fases/fase_6/integration.md) - Integracao e comunicacao entre servicos
- [`security_governance.md`](fases/fase_6/security_governance.md) - Seguranca, escalabilidade e governanca
- [`tech_stack.md`](fases/fase_6/tech_stack.md) - Stack de tecnologias AWS
- [`fiap.excalidraw`](fases/fase_6/fiap.excalidraw) - Diagrama visual da arquitetura
