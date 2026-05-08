package com.exemplo.carrinho;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

@ExtendWith(MockitoExtension.class)
class CarrinhoEventPublisherTest {

    @Mock private JmsTemplate jmsTemplate;
    @InjectMocks private CarrinhoEventPublisher publisher;

    @Test
    void devePublicarNaFilaCarrinhoEventos() {
        String payload = "{\"tipo\":\"CHECKOUT\"}";

        publisher.publicar(payload);

        verify(jmsTemplate).convertAndSend("carrinho.eventos", payload);
    }

    @Test
    void filaConstanteDeveSerCarrinhoEventos() {
        org.assertj.core.api.Assertions.assertThat(CarrinhoEventPublisher.QUEUE)
                .isEqualTo("carrinho.eventos");
    }
}
