package com.exemplo.estoque;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CarrinhoEventListenerTest {

    @Test
    void deveProcessarMensagemSemErro() {
        CarrinhoEventListener listener = new CarrinhoEventListener();

        assertThatCode(() -> listener.onEvento("{\"tipo\":\"CHECKOUT\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void filaConfiguradaDeveSerCarrinhoEventos() {
        org.assertj.core.api.Assertions.assertThat(CarrinhoEventListener.QUEUE)
                .isEqualTo("carrinho.eventos");
    }
}
