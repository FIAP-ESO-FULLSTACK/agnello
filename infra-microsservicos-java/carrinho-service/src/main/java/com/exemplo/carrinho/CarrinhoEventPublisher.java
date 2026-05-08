package com.exemplo.carrinho;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class CarrinhoEventPublisher {

    static final String QUEUE = "carrinho.eventos";

    private final JmsTemplate jmsTemplate;

    public CarrinhoEventPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publicar(String payload) {
        jmsTemplate.convertAndSend(QUEUE, payload);
    }
}
