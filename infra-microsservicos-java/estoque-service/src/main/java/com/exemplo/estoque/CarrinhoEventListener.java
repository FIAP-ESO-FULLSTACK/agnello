package com.exemplo.estoque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class CarrinhoEventListener {

    static final String QUEUE = "carrinho.eventos";

    private static final Logger log = LoggerFactory.getLogger(CarrinhoEventListener.class);

    @JmsListener(destination = QUEUE)
    public void onEvento(String payload) {
        log.info("[estoque-service] Evento recebido da fila {}: {}", QUEUE, payload);
    }
}
