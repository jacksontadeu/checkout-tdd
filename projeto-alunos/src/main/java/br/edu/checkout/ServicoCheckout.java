package br.edu.checkout;

import java.math.BigDecimal;
import java.util.Objects;

public final class ServicoCheckout {

    private final EstoqueGateway estoqueGateway;
    private final PagamentoGateway pagamentoGateway;
    private final PedidoRepository pedidoRepository;
    private final Notificador notificador;

    public ServicoCheckout(
            EstoqueGateway estoqueGateway,
            PagamentoGateway pagamentoGateway,
            PedidoRepository pedidoRepository,
            Notificador notificador) {
        this.estoqueGateway = Objects.requireNonNull(estoqueGateway);
        this.pagamentoGateway = Objects.requireNonNull(pagamentoGateway);
        this.pedidoRepository = Objects.requireNonNull(pedidoRepository);
        this.notificador = Objects.requireNonNull(notificador);
    }

    public ResultadoCheckout finalizar(SolicitacaoCheckout solicitacao) {
        validar(solicitacao);

        BigDecimal total = solicitacao.precoUnitario()
                .multiply(BigDecimal.valueOf(solicitacao.quantidade()));

        boolean reservado = estoqueGateway.reservar(solicitacao.sku(), solicitacao.quantidade());
        if (!reservado) {
            throw new EstoqueInsuficienteException();
        }

        Pagamento pagamento;
        try {
            pagamento = pagamentoGateway.cobrar(solicitacao.clienteId(), total);
        } catch (FalhaPagamentoException e) {
            estoqueGateway.liberar(solicitacao.sku(), solicitacao.quantidade());
            throw e;
        }

        if (!pagamento.aprovado()) {
            estoqueGateway.liberar(solicitacao.sku(), solicitacao.quantidade());
            throw new PagamentoRecusadoException();
        }

        PedidoConfirmado pedido = new PedidoConfirmado(
                solicitacao.clienteId(),
                solicitacao.email(),
                solicitacao.sku(),
                solicitacao.quantidade(),
                total,
                pagamento.transacaoId());

        String numeroPedido = pedidoRepository.salvar(pedido);

        boolean notificacaoEnviada;
        try {
            notificador.enviarConfirmacao(solicitacao.email(), numeroPedido, total);
            notificacaoEnviada = true;
        } catch (FalhaNotificacaoException e) {
            notificacaoEnviada = false;
        }

        return new ResultadoCheckout(numeroPedido, total, notificacaoEnviada);
    }

    private static void validar(SolicitacaoCheckout solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("Solicitação não pode ser nula");
        }
        if (isBlank(solicitacao.clienteId())) {
            throw new IllegalArgumentException("clienteId não pode ser nulo, vazio ou em branco");
        }
        if (isBlank(solicitacao.email())) {
            throw new IllegalArgumentException("email não pode ser nulo, vazio ou em branco");
        }
        if (isBlank(solicitacao.sku())) {
            throw new IllegalArgumentException("sku não pode ser nulo, vazio ou em branco");
        }
        if (solicitacao.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        if (solicitacao.precoUnitario() == null
                || solicitacao.precoUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("precoUnitario deve ser maior que zero");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
