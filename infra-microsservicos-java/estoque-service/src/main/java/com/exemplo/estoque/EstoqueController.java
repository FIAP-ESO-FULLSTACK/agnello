package com.exemplo.estoque;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EstoqueController {

    @GetMapping({"", "/"})
    public Map<String, Object> raiz() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("servico", "estoque-service");
        resposta.put("mensagem", "Servico de estoque.");
        resposta.put("endpoints", List.of("/estoque", "/health"));

        return resposta;
    }

    @GetMapping("/estoque")
    public Map<String, Object> listarEstoque() {
        List<ItemEstoque> estoque = List.of(
                new ItemEstoque("VINHO-TINTO-001", "Cabernet Sauvignon Reserva", 12),
                new ItemEstoque("VINHO-BRANCO-002", "Chardonnay Safra Especial", 20),
                new ItemEstoque("VINHO-ROSE-003", "Vinho  Rose", 15)
        );

        return Map.of(
                "servico", "estoque-service",
                "estoque", estoque
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "servico", "estoque-service",
                "status", "UP"
        );
    }

    private record ItemEstoque(String sku, String produto, Integer quantidade Disponível) {
    }
}
