package com.exemplo.carrinho;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
public class CarrinhoController {

    private final RestTemplate restTemplate;
    private final String estoqueServiceUrl;

    public CarrinhoController(
            RestTemplate restTemplate,
            @Value("${services.estoque.url:http://estoque-service:8080/estoque}") String estoqueServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.estoqueServiceUrl = estoqueServiceUrl;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> raiz() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("servico", "carrinho-service");
        resposta.put("mensagem", "Servico de carrinho de compras");
        resposta.put("endpoints", List.of("/carrinho", "/health"));

        return resposta;
    }

    @GetMapping("/carrinho")
    public ResponseEntity<Map<String, Object>> visualizarCarrinho() {
        List<ItemCarrinho> itensCarrinho = List.of(
                new ItemCarrinho(1L, "VINHO-TINTO-001", "Cabernet Sauvignon Reserva", 2),
                new ItemCarrinho(2L, "VINHO-ROSE-003", "Vinho  Rose", 1)
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> estoqueRecebido = restTemplate.getForObject(estoqueServiceUrl, Map.class);

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("servico", "carrinho-service");
            resposta.put("mensagem", "Carrinho da vinheria consultando estoque-service via DNS interno do Docker");
            resposta.put("estoqueRecebido", estoqueRecebido);
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
