package com.piggu.finance.domain;

import com.piggu.common.web.Meses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * O que merece aviso hoje: orcamento em 80% ou 100%, fatura de cartao vencendo, conta
 * fixa vencendo ou vencida e dinheiro a receber que passou da data combinada. E o mesmo "Para ficar de olho" do painel, so que como dado,
 * para o identity mandar por e-mail ou push quando o app esta fechado.
 *
 * <p>Cada aviso tem uma chave que muda quando a situacao muda (80% e 100% sao chaves
 * diferentes; a fatura de cada vencimento tambem). Quem envia guarda as chaves ja
 * mandadas e nunca repete o mesmo aviso.</p>
 */
@Service
public class AvisosDoDia {

    /** Fatura entra no aviso quando faltam ate 3 dias; conta fixa, ate 2. */
    static final int DIAS_ANTES_DA_FATURA = 3;
    static final int DIAS_ANTES_DA_CONTA = 2;

    public enum Tipo {
        ORCAMENTO_ATENCAO, ORCAMENTO_ESTOUROU, FATURA_VENCENDO, CONTA_VENCENDO, CONTA_VENCIDA, A_RECEBER_ATRASADO
    }

    /**
     * @param chave     identifica o aviso para nao repetir
     * @param assunto   categoria, cartao ou conta
     * @param valor     gasto da categoria, total da fatura ou valor da conta
     * @param limite    so no orcamento
     * @param percentual so no orcamento
     * @param data      vencimento (fatura e conta)
     */
    public record Aviso(String chave, Tipo tipo, String assunto, BigDecimal valor, BigDecimal limite,
                        BigDecimal percentual, LocalDate data) {
    }

    private final OrcamentoService orcamentos;
    private final ContasECartoes cartoes;
    private final ContasFixasService contasFixas;
    private final AReceberService aReceber;
    private final Clock relogio;

    @Autowired
    public AvisosDoDia(OrcamentoService orcamentos, ContasECartoes cartoes, ContasFixasService contasFixas,
                       AReceberService aReceber) {
        this(orcamentos, cartoes, contasFixas, aReceber, Clock.system(Meses.BRASILIA));
    }

    AvisosDoDia(OrcamentoService orcamentos, ContasECartoes cartoes, ContasFixasService contasFixas,
                AReceberService aReceber, Clock relogio) {
        this.orcamentos = orcamentos;
        this.cartoes = cartoes;
        this.contasFixas = contasFixas;
        this.aReceber = aReceber;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Aviso> deHoje() {
        LocalDate hoje = LocalDate.now(relogio);
        YearMonth mes = YearMonth.from(hoje);
        List<Aviso> avisos = new ArrayList<>();

        for (OrcamentoService.Situacao orcamento : orcamentos.doMes(mes)) {
            if (orcamento.alerta() == OrcamentoService.Alerta.OK) {
                continue;
            }
            boolean estourou = orcamento.alerta() == OrcamentoService.Alerta.ESTOUROU;
            avisos.add(new Aviso("orcamento:" + orcamento.id() + ":" + mes + (estourou ? ":100" : ":80"),
                    estourou ? Tipo.ORCAMENTO_ESTOUROU : Tipo.ORCAMENTO_ATENCAO, orcamento.categoria(),
                    orcamento.gasto(), orcamento.limite(), orcamento.percentual(), null));
        }

        for (ContasECartoes.Conta cartao : cartoes.listar()) {
            ContasECartoes.Fatura fatura = cartao.faturaAPagar();
            if (fatura != null && !fatura.paga() && fatura.total().signum() > 0
                    && !fatura.vencimento().isAfter(hoje.plusDays(DIAS_ANTES_DA_FATURA))) {
                avisos.add(new Aviso("fatura:" + cartao.id() + ":" + fatura.vencimento(), Tipo.FATURA_VENCENDO,
                        cartao.nome(), fatura.total(), null, null, fatura.vencimento()));
            }
        }

        List<ContasFixasService.ContaDoMes> contas = new ArrayList<>(contasFixas.doMes(mes));
        // A conta do dia 1 ou 2 do mes que vem tambem avisa 2 dias antes, ainda neste mes.
        YearMonth mesDoAviso = YearMonth.from(hoje.plusDays(DIAS_ANTES_DA_CONTA));
        if (mesDoAviso.isAfter(mes)) {
            contas.addAll(contasFixas.doMes(mesDoAviso));
        }
        for (ContasFixasService.ContaDoMes conta : contas) {
            // O mes da chave e o do vencimento: o aviso dado em 30/10 para 01/11 nao se repete em novembro.
            YearMonth doVencimento = YearMonth.from(conta.vencimento());
            if (conta.situacao() == ContasFixasService.Situacao.VENCIDA) {
                avisos.add(new Aviso("conta:" + conta.conta().getId() + ":" + doVencimento + ":vencida", Tipo.CONTA_VENCIDA,
                        conta.conta().getDescription(), conta.conta().getAmount(), null, null, conta.vencimento()));
            } else if (conta.situacao() == ContasFixasService.Situacao.PENDENTE
                    && !conta.vencimento().isAfter(hoje.plusDays(DIAS_ANTES_DA_CONTA))) {
                avisos.add(new Aviso("conta:" + conta.conta().getId() + ":" + doVencimento + ":vencendo",
                        Tipo.CONTA_VENCENDO, conta.conta().getDescription(), conta.conta().getAmount(), null, null,
                        conta.vencimento()));
            }
        }

        for (Receivable conta : aReceber.pendentes()) {
            if (conta.getDueDate().isBefore(hoje)) {
                avisos.add(new Aviso("receber:" + conta.getId(), Tipo.A_RECEBER_ATRASADO, conta.getDebtor(),
                        conta.getAmount(), null, null, conta.getDueDate()));
            }
        }
        return avisos;
    }
}
