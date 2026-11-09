package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServicoCheckoutTest {

    private static final String CLIENTE_ID    = "cliente-001";
    private static final String EMAIL         = "cliente@exemplo.com";
    private static final String SKU           = "SKU-ABC";
    private static final int    QUANTIDADE    = 2;
    private static final BigDecimal PRECO     = new BigDecimal("29.90");
    private static final BigDecimal TOTAL     = new BigDecimal("59.80");
    private static final String TRANSACAO_ID  = "txn-999";
    private static final String NUMERO_PEDIDO = "PED-0042";

    @Mock private EstoqueGateway   estoqueGateway;
    @Mock private PagamentoGateway pagamentoGateway;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private Notificador      notificador;

    private ServicoCheckout servico;

    @BeforeEach
    void criarServicoReal() {
        servico = new ServicoCheckout(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    static Stream<Arguments> solicitacoesInvalidas() {
        return Stream.of(
            Arguments.of("solicitação nula", null),
            Arguments.of("clienteId nulo",
                    new SolicitacaoCheckout(null,    EMAIL, SKU, QUANTIDADE, PRECO)),
            Arguments.of("clienteId vazio",
                    new SolicitacaoCheckout("",      EMAIL, SKU, QUANTIDADE, PRECO)),
            Arguments.of("clienteId em branco",
                    new SolicitacaoCheckout("   ",   EMAIL, SKU, QUANTIDADE, PRECO)),
            Arguments.of("email nulo",
                    new SolicitacaoCheckout(CLIENTE_ID, null,  SKU, QUANTIDADE, PRECO)),
            Arguments.of("email vazio",
                    new SolicitacaoCheckout(CLIENTE_ID, "",    SKU, QUANTIDADE, PRECO)),
            Arguments.of("email em branco",
                    new SolicitacaoCheckout(CLIENTE_ID, "  ",  SKU, QUANTIDADE, PRECO)),
            Arguments.of("sku nulo",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, null,  QUANTIDADE, PRECO)),
            Arguments.of("sku vazio",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, "",    QUANTIDADE, PRECO)),
            Arguments.of("sku em branco",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, "  ",  QUANTIDADE, PRECO)),
            Arguments.of("quantidade zero",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, 0,  PRECO)),
            Arguments.of("quantidade negativa",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, -1, PRECO)),
            Arguments.of("precoUnitario nulo",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, QUANTIDADE, null)),
            Arguments.of("precoUnitario zero",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, QUANTIDADE, BigDecimal.ZERO)),
            Arguments.of("precoUnitario negativo",
                    new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, QUANTIDADE, new BigDecimal("-1.00")))
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("solicitacoesInvalidas")
    @DisplayName("R1 — deve lançar IllegalArgumentException e não chamar nenhum colaborador")
    void deveRejeitarEntradaInvalidaSemConsultarColaboradores(
            String descricao, SolicitacaoCheckout solicitacao) {

        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(solicitacao));

        verifyNoInteractions(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    @DisplayName("R2 — quantidade 1 deve manter precoUnitario como total")
    void quantidadeUmaUnidadeDeveManterPrecoComoTotal() {
        BigDecimal precoUnitario = new BigDecimal("49.99");
        SolicitacaoCheckout solicitacao = new SolicitacaoCheckout(
                CLIENTE_ID, EMAIL, SKU, 1, precoUnitario);

        when(estoqueGateway.reservar(SKU, 1)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, precoUnitario))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertEquals(precoUnitario, resultado.total());
        verify(estoqueGateway, times(1)).reservar(SKU, 1);
    }

    @Test
    @DisplayName("R2 — 3 unidades de 19.90 devem produzir total 59.70")
    void calculoUsual_tresUnidadesDeDezenoveReais90() {
        BigDecimal preco    = new BigDecimal("19.90");
        BigDecimal esperado = new BigDecimal("59.70");
        SolicitacaoCheckout solicitacao = new SolicitacaoCheckout(
                CLIENTE_ID, EMAIL, SKU, 3, preco);

        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, esperado))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertEquals(0, esperado.compareTo(resultado.total()),
                "Total deve ser 59.70 sem arredondamento");
    }

    @Test
    @DisplayName("R2 — 3 unidades de 0.005 devem produzir total 0.015 sem arredondamento")
    void calculoPrecisao_tresUnidadesDeMeioCentavo() {
        BigDecimal preco    = new BigDecimal("0.005");
        BigDecimal esperado = new BigDecimal("0.015");
        SolicitacaoCheckout solicitacao = new SolicitacaoCheckout(
                CLIENTE_ID, EMAIL, SKU, 3, preco);

        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, esperado))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertEquals(0, esperado.compareTo(resultado.total()),
                "Total deve ser 0.015 preservando casas decimais extras");
    }

    @Test
    @DisplayName("R3 — estoque insuficiente deve lançar exceção e não chamar pagamento, " +
                 "repositório, notificador nem liberar")
    void semEstoque_deveLancarExcecaoSemCobrarOuLiberar() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(false);

        assertThrows(EstoqueInsuficienteException.class, () -> servico.finalizar(solicitacao));

        verify(estoqueGateway, never()).liberar(any(), eq(QUANTIDADE));
        verifyNoInteractions(pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    @DisplayName("R4 — pagamento recusado deve liberar reserva uma vez e lançar " +
                 "PagamentoRecusadoException, sem registrar pedido ou notificar")
    void pagamentoRecusado_deveLiberarELancarExcecao() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, TOTAL))
                .thenReturn(new Pagamento(false, null));

        assertThrows(PagamentoRecusadoException.class, () -> servico.finalizar(solicitacao));

        verify(estoqueGateway, times(1)).liberar(SKU, QUANTIDADE);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    @Test
    @DisplayName("R5 — FalhaPagamentoException deve liberar reserva e propagar a mesma instância")
    void falhaTecnicaPagamento_deveLiberarEPropagarMesmaInstancia() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();
        FalhaPagamentoException falhaOriginal = new FalhaPagamentoException("timeout no gateway");

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, TOTAL)).thenThrow(falhaOriginal);

        FalhaPagamentoException capturada = assertThrows(
                FalhaPagamentoException.class, () -> servico.finalizar(solicitacao));

        assertSame(falhaOriginal, capturada,
                "Deve propagar a mesma instância de FalhaPagamentoException");

        verify(estoqueGateway, times(1)).liberar(SKU, QUANTIDADE);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    @Test
    @DisplayName("R6 — PedidoConfirmado salvo deve conter todos os seis campos corretos")
    void persistenciaCorreta_todosOsCamposDosPedidoConfirmado() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, TOTAL))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);

        servico.finalizar(solicitacao);

        ArgumentCaptor<PedidoConfirmado> captor = ArgumentCaptor.forClass(PedidoConfirmado.class);
        verify(pedidoRepository).salvar(captor.capture());

        PedidoConfirmado pedido = captor.getValue();
        assertAll("todos os seis campos do PedidoConfirmado",
            () -> assertEquals(CLIENTE_ID,   pedido.clienteId(),   "clienteId"),
            () -> assertEquals(EMAIL,        pedido.email(),       "email"),
            () -> assertEquals(SKU,          pedido.sku(),         "sku"),
            () -> assertEquals(QUANTIDADE,   pedido.quantidade(),  "quantidade"),
            () -> assertEquals(0, TOTAL.compareTo(pedido.total()), "total"),
            () -> assertEquals(TRANSACAO_ID, pedido.transacaoId(), "transacaoId")
        );
    }

    @Test
    @DisplayName("R6 — compra aprovada deve retornar resultado correto e seguir a ordem " +
                 "reservar → cobrar → salvar → enviarConfirmacao, sem liberar")
    void compraAprovada_resultadoOrdemEAusenciaDeLiberacao() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, TOTAL))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertAll("campos do ResultadoCheckout",
            () -> assertEquals(NUMERO_PEDIDO, resultado.numeroPedido(), "numeroPedido"),
            () -> assertEquals(0, TOTAL.compareTo(resultado.total()),   "total"),
            () -> assertTrue(resultado.notificacaoEnviada(),            "notificacaoEnviada")
        );

        verify(notificador, times(1)).enviarConfirmacao(EMAIL, NUMERO_PEDIDO, TOTAL);

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway).reservar(SKU, QUANTIDADE);
        ordem.verify(pagamentoGateway).cobrar(CLIENTE_ID, TOTAL);
        ordem.verify(pedidoRepository).salvar(any());
        ordem.verify(notificador).enviarConfirmacao(EMAIL, NUMERO_PEDIDO, TOTAL);

        verify(estoqueGateway, never()).liberar(any(), eq(QUANTIDADE));
    }

    @Test
    @DisplayName("R7 — FalhaNotificacaoException deve retornar notificacaoEnviada=false sem " +
                 "nova cobrança, nova persistência ou liberação")
    void falhaNotificacao_deveRetornarFalseSemDesfazerCompra() {
        SolicitacaoCheckout solicitacao = solicitacaoValida();

        when(estoqueGateway.reservar(SKU, QUANTIDADE)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE_ID, TOTAL))
                .thenReturn(new Pagamento(true, TRANSACAO_ID));
        when(pedidoRepository.salvar(any())).thenReturn(NUMERO_PEDIDO);
        doThrow(new FalhaNotificacaoException("servidor de e-mail indisponível"))
                .when(notificador).enviarConfirmacao(EMAIL, NUMERO_PEDIDO, TOTAL);

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertAll("resultado com falha de notificação",
            () -> assertEquals(NUMERO_PEDIDO, resultado.numeroPedido(), "numeroPedido"),
            () -> assertEquals(0, TOTAL.compareTo(resultado.total()),   "total"),
            () -> assertFalse(resultado.notificacaoEnviada(), "notificacaoEnviada deve ser false")
        );

        verify(pagamentoGateway, times(1)).cobrar(any(), any());
        verify(pedidoRepository, times(1)).salvar(any());
        verify(estoqueGateway, never()).liberar(any(), eq(QUANTIDADE));
    }

    private static SolicitacaoCheckout solicitacaoValida() {
        return new SolicitacaoCheckout(CLIENTE_ID, EMAIL, SKU, QUANTIDADE, PRECO);
    }
}
