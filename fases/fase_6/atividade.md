# Objetivos da atividade

Modelar a arquitetura de um sistema baseado em APIs e microsserviços, aplicado a vinheria. O foco será na definição da estrutura e das interações entre os serviços.

## 1 - Identificação dos Serviços

Listar os principais serviços do sistema da vinícola que podem ser transformados em microsserviços.

Exemplo de serviços:

- **Gestão de Produção:** registro de colheitas, fermentação, armazenamento.
- **Rastreamento de Vinhos:** controle de lotes, histórico de produção.
- **Gestão de Pedidos:** controle de compras, fornecedores e estoque.
- **Monitoramento de Qualidade:** controle de temperatura, umidade, análises laboratoriais.

## 2 - Definição da Arquitetura

Criar um diagrama de arquitetura de microsserviços, incluindo:

- Os microsserviços identificados.
- A forma de comunicação entre eles (REST, mensagens assíncronas).
- A necessidade de um API Gateway para gerenciar o acesso aos serviços.

## 3 - Padrões de Arquitetura Aplicáveis

Identificar e justificar quais design patterns são adequados para o sistema.

Exemplos de padrões:

- **API Gateway Pattern:** centralizar o gerenciamento das APIs.
- **Service Discovery Pattern:** garantir que os microsserviços possam se encontrar.
- **Database per Service Pattern:** cada serviço possui seu próprio banco de dados.
- **Saga Pattern:** para manter a consistência dos dados entre serviços diferentes.

## 4 - Integração entre Microsserviços

- Definir quais microsserviços precisam se comunicar diretamente.
- Explicar quando a comunicação deve ser síncrona (REST) ou assíncrona (mensageria, como Kafka).

## Segurança e Governança

- Discutir a importância de segurança na API, incluindo autenticação e autorização.
- Como garantir que os serviços sejam escaláveis e resilientes.

## Instruções para a entrega

Um arquivo PDF que deve conter:

- Lista de microsserviços e suas funções.
- Diagrama de arquitetura de microsserviços.
- Justificativa dos padrões de arquitetura escolhidos.
- Explicação sobre a comunicação entre os serviços.
- Estratégias de segurança e governança.
