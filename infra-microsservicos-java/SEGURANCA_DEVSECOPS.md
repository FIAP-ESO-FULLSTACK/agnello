# Comunicacao Segura e DevSecOps - Vinheria Agnello

Esta documentacao descreve a camada de seguranca adicionada sobre os microsservicos da Fase 7 (`carrinho-service` e `estoque-service`) e as praticas DevSecOps aplicaveis ao pipeline.

## 1. Visao geral da arquitetura segura

```
                                         (1) POST /auth/login (HTTP)
                                              admin / admin
                                  +------------------------------+
                                  v                              |
+------------+   (2) JWT      +------+    (3) GET /carrinho      |
|  cliente   | ------------>  |8001  |       Authorization: Bearer
|  (curl)    | <------------  |      |                            |
+------------+                 |carr- |    (4) GET /estoque       |
                               |inho- |    Authorization: Bearer  |
                               |srv   | --------------------+     |
                               +------+                     |     |
                                 |                          v     |
                                 | (5) JMS publish     +-------+  |
                                 v   carrinho.eventos  |8000   |  |
                          +-------------+              |estoque|  |
                          |  Artemis    |              | -srv  |  |
                          |  broker     |  (6) JMS     +-------+  |
                          |  61616      | <--listener-------+     |
                          +-------------+                          |
                                                                   |
                          (auth-service emite e assina o JWT) ------+
```

| Servico          | Porta host | Endpoint principal        | Protegido por JWT |
|------------------|-----------:|---------------------------|:----------------:|
| auth-service     | 8002       | POST /auth/login          | nao (emite)      |
| estoque-service  | 8000       | GET /estoque              | sim              |
| carrinho-service | 8001       | GET /carrinho             | sim              |
| carrinho-service | 8001       | POST /carrinho/checkout   | sim              |
| artemis-broker   | 61616      | JMS / fila carrinho.eventos | autenticacao Artemis |

`/health` e `/` permanecem publicos em todos os servicos para compatibilidade com a Fase 7.

## 2. Como rodar tudo

```bash
cd infra-microsservicos-java
docker compose up --build
```

Aguardar 30-60s ate todos os containers ficarem `healthy`.

## 3. Fluxo end-to-end (com JWT)

```bash
# 1. obter token
TOKEN=$(curl -s -X POST http://localhost:8002/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

echo "Token: $TOKEN"

# 2. tentar chamar estoque sem token (deve dar 401)
curl -i http://localhost:8000/estoque

# 3. chamar estoque com token (deve dar 200)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8000/estoque

# 4. chamar carrinho com token (carrinho propaga JWT ao estoque)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8001/carrinho

# 5. checkout (publica evento na fila JMS)
curl -i -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8001/carrinho/checkout

# 6. verificar consumo no estoque-service
docker logs estoque-service | grep "Evento recebido"
```

## 4. Demo de Wireshark - "antes e depois"

O proposito e mostrar visualmente a diferenca entre trafego HTTP em claro e cifrado, e o impacto do JWT.

### 4.1 Capturando trafego na rede do Docker

A `lab-network` esta no subnet `172.30.0.0/24`. Para capturar trafego entre containers, identifique a interface bridge:

```bash
# descobrir interface
docker network inspect lab-network -f '{{ .Id }}' | cut -c1-12
# o nome da interface no host fica br-<12 chars>
ifconfig | grep -B1 "inet 172.30"
```

No Wireshark, selecione essa interface (ex: `br-1a2b3c4d5e6f`) e aplique filtro:
```
tcp.port == 8080 or tcp.port == 61616
```

Ou capture pelo loopback se chamar pelos `localhost:800x` mapeados.

### 4.2 Cenario A - HTTP sem autenticacao (vulnerabilidade)

Endpoints `/health` e `/` ficam expostos sem JWT:
```bash
curl http://localhost:8000/health
```
No Wireshark voce vera **payload em texto claro** - qualquer um na rede ve.

### 4.3 Cenario B - HTTP com JWT

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/estoque
```
No Wireshark, em "Follow > HTTP Stream" voce vera o **header Authorization completo** com o JWT visivel. Isso demonstra que **JWT sozinho nao protege contra interceptacao** - so prova autenticacao se a integridade do canal estiver garantida.

Mensagem-chave: `Authorization: Bearer eyJhbG...` aparece em texto puro. Quem capturar este pacote pode reusar o token ate ele expirar (15 min por padrao).

### 4.4 Cenario C - Mensageria JMS em texto claro

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8001/carrinho/checkout
```

Filtro Wireshark: `tcp.port == 61616`. O protocolo Artemis Core/AMQP transmite o payload JSON do evento sem criptografia - novamente em claro.

### 4.5 Cenario D - HTTPS / TLS (opcional, mostra correcao)

Para o "depois" ideal, gerar cert self-signed no auth-service e redirecionar para HTTPS:
```bash
keytool -genkeypair -alias auth -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore auth.p12 -validity 365 \
  -dname "CN=auth-service,OU=fiap,O=agnello,L=SP,C=BR" \
  -storepass changeit
```
Adicionar em `application.properties`:
```
server.ssl.key-store=classpath:auth.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=auth
```

No Wireshark, agora os pacotes aparecem como `TLSv1.3` e o payload e ilegivel - **correcao validada**.

## 5. Conclusoes para a entrega

1. **JWT autentica mas nao cifra.** Ainda e necessario TLS para integridade do transporte.
2. **Mensageria sem TLS expoe o payload.** Artemis suporta SSL/TLS nativo - mesmo principio.
3. **Boas praticas demonstradas no codigo:**
   - Stateless sessions (`SessionCreationPolicy.STATELESS`)
   - Senha armazenada com `BCryptPasswordEncoder`
   - JWT com expiracao curta (15 min)
   - Segredo via variavel de ambiente, nao hard-coded
   - CSRF desabilitado por ser API stateless
   - Filtro JWT executa antes do `UsernamePasswordAuthenticationFilter`

## 6. DevSecOps no pipeline

O `Jenkinsfile` da raiz pode ser estendido com etapas de seguranca. Sugestao de stages adicionais:

```groovy
stage('SAST') {
  steps {
    sh 'mvn -B com.github.spotbugs:spotbugs-maven-plugin:check'
  }
}

stage('SCA - Dependency Check') {
  steps {
    sh 'mvn -B org.owasp:dependency-check-maven:check'
  }
}

stage('Container scan') {
  steps {
    sh 'trivy image --severity HIGH,CRITICAL --exit-code 1 carrinho-service:latest'
    sh 'trivy image --severity HIGH,CRITICAL --exit-code 1 estoque-service:latest'
    sh 'trivy image --severity HIGH,CRITICAL --exit-code 1 auth-service:latest'
  }
}

stage('Secret scan') {
  steps {
    sh 'gitleaks detect --source . --no-banner --redact'
  }
}
```

Pratica adicional: `JWT_SECRET` e `AUTH_PASSWORD` devem vir de um cofre (Vault, AWS Secrets Manager, etc.) em producao - aqui estao em `docker-compose.yml` apenas para fins didaticos.

## 7. Testes automatizados

Cada servico tem suite JUnit 5 que valida:

| Servico          | Teste                              | O que valida |
|------------------|------------------------------------|--------------|
| auth-service     | `JwtServiceTest`                   | Geracao de JWT (subject, expiracao, formato) |
| auth-service     | `AuthControllerTest`               | Login OK / falha / endpoints publicos |
| estoque-service  | `JwtServiceTest`                   | Validacao de token, rejeicao por outra chave / expirado |
| estoque-service  | `JwtAuthFilterTest`                | Cenarios do filtro (sem header, Bearer invalido, valido) |
| estoque-service  | `EstoqueControllerSecurityTest`    | 401 sem token, 200 com token, 401 com chave errada |
| estoque-service  | `CarrinhoEventListenerTest`        | Listener processa mensagem da fila |
| carrinho-service | `JwtServiceTest`                   | Validacao de token |
| carrinho-service | `JwtAuthFilterTest`                | Cenarios do filtro |
| carrinho-service | `CarrinhoEventPublisherTest`       | Publicacao na fila correta |
| carrinho-service | `CarrinhoControllerSecurityTest`   | 401/200 nos endpoints, **propagacao do header Authorization** ao estoque, checkout publica evento |

Rodar tudo:
```bash
cd infra-microsservicos-java
(cd auth-service     && mvn -B test)
(cd estoque-service  && mvn -B test)
(cd carrinho-service && mvn -B test)
```

## 8. O que **nao** foi alterado da Fase 7

- Estrutura `infra-microsservicos-java/{carrinho,estoque}-service` mantida
- `Jenkinsfile`, `DOCKER_LOCAL.md` da Fase 7 intactos
- Endpoints `/`, `/health`, `/carrinho`, `/estoque` continuam respondendo
- Comunicacao REST `carrinho -> estoque` segue funcionando (agora com JWT propagado)
- Workflow `.github/workflows/main_agnelo.yml` nao foi tocado

O que foi **adicionado** (sem remover nada):
- Novo `auth-service`
- Novo container `artemis-broker` no `docker-compose.yml`
- Novas classes `SecurityConfig`, `JwtAuthFilter`, `JwtService` em cada servico backend
- Novo endpoint `POST /carrinho/checkout`
- Novo `CarrinhoEventPublisher` (carrinho) e `CarrinhoEventListener` (estoque)
- Suite de testes JUnit
