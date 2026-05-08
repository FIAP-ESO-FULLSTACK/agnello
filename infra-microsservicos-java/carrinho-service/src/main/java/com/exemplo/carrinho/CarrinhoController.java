package com.exemplo.carrinho;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

@RestController
public class CarrinhoController {

    private final RestTemplate restTemplate;
    private final String estoqueServiceUrl;
    private final CarrinhoEventPublisher eventPublisher;

    public CarrinhoController(
            RestTemplate restTemplate,
            @Value("${services.estoque.url:http://estoque-service:8080/estoque}") String estoqueServiceUrl,
            CarrinhoEventPublisher eventPublisher
    ) {
        this.restTemplate = restTemplate;
        this.estoqueServiceUrl = estoqueServiceUrl;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> raiz() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("servico", "carrinho-service");
        resposta.put("mensagem", "Servico de carrinho de compras");
        resposta.put("endpoints", List.of("/carrinho (JWT)", "POST /carrinho/checkout (JWT)", "/health"));

        return resposta;
    }

    @GetMapping("/carrinho")
    public ResponseEntity<Map<String, Object>> visualizarCarrinho(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        List<ItemCarrinho> itensCarrinho = List.of(
                new ItemCarrinho(1L, "VINHO-TINTO-001", "Cabernet Sauvignon Reserva", 2),
                new ItemCarrinho(2L, "VINHO-ROSE-003", "Vinho  Rose", 1)
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorization != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> estoqueResponse = restTemplate.exchange(
                    estoqueServiceUrl,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("servico", "carrinho-service");
            resposta.put("mensagem", "Carrinho da vinheria consultando estoque-service via DNS interno do Docker (JWT propagado)");
            resposta.put("estoqueRecebido", estoqueResponse.getBody());
            resposta.put("itensCarrinho", itensCarrinho);

            return ResponseEntity.ok(resposta);
        } catch (RestClientException exception) {
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("servico", "carrinho-service");
            resposta.put("mensagem", "Falha ao consultar o estoque da vinheria via DNS interno do Docker");
            resposta.put("erro", exception.getMessage());
            resposta.put("itensCarrinho", itensCarrinho);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resposta);
        }
    }

    @PostMapping("/carrinho/checkout")
    public ResponseEntity<Map<String, Object>> checkout() {
        String evento = "{\"tipo\":\"CHECKOUT\",\"itens\":[\"VINHO-TINTO-001\",\"VINHO-ROSE-003\"]}";
        eventPublisher.publicar(evento);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("servico", "carrinho-service");
        resposta.put("mensagem", "Evento de checkout publicado na fila JMS carrinho.eventos");
        resposta.put("eventoPublicado", evento);
        return ResponseEntity.accepted().body(resposta);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "servico", "carrinho-service",
                "status", "UP"
        );
    }

    private record ItemCarrinho(Long id, String sku, String produto, Integer quantidade) {
    }
}
