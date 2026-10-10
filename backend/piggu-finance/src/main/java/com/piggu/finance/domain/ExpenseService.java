package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Gastos: o centro do Piggu.
 *
 * <p>Porte de salvarGastos_, atualizarGasto_, excluirGasto_ e validarItens_.</p>
 */
@Service
public class ExpenseService {

    private static final String TIPO_PADRAO = "Variavel";
    private static final String ORIGEM_PADRAO = "Manual";
    private static final String ORIGEM_EXTRATO = "Extrato";

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository repositorio;
    private final CategoryService categorias;
    private final ProductMemoryService memoriaDeProdutos;
    private final RegrasDeCategoria regras;
    private final PaymentAccountRepository contas;
    private final DivisaoDeGastos divisao;
    private final TrilhaDeAuditoria trilha;

    public ExpenseService(ExpenseRepository repositorio,
                          CategoryService categorias,
                          ProductMemoryService memoriaDeProdutos,
                          RegrasDeCategoria regras,
                          PaymentAccountRepository contas,
                          DivisaoDeGastos divisao,
                          TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.categorias = categorias;
        this.memoriaDeProdutos = memoriaDeProdutos;
        this.regras = regras;
        this.contas = contas;
        this.divisao = divisao;
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listar() {
        return repositorio.findAllByOrderByExpenseDateDescCreatedAtDesc().stream()
                .map(ExpenseResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listarDoMes(String mes) {
        YearMonth referencia = YearMonth.parse(mes);
        return repositorio.findByExpenseDateBetweenOrderByExpenseDateDesc(
                        referencia.atDay(1), referencia.atEndOfMonth()).stream()
                .map(ExpenseResponse::de)
                .toList();
    }

    /**
     * Grava um lancamento inteiro.
     *
     * <p>Tudo acontece em uma transacao: ou todos os itens do recibo entram, ou nenhum.
     * O Apps Script dependia de um LockService e de uma escrita em bloco para chegar
     * perto disso.</p>
     */
    @Transactional
    public List<ExpenseResponse> salvar(SaveExpensesRequest pedido, String emailUsuario) {
        UUID reciboId = pedido.reciboId() == null ? UUID.randomUUID() : pedido.reciboId();
        String estabelecimento = Texto.limitar(pedido.estabelecimento(), 200);
        String origem = Texto.limitarOuPadrao(pedido.origem(), 30, ORIGEM_PADRAO);

        // A conta precisa ser da familia: o filtro do tenant faz o findById nao achar a dos outros.
        if (pedido.contaId() != null && !contas.existsById(pedido.contaId())) {
            throw new NotFoundException("Conta ou cartao nao encontrado.");
        }
        int parcelas = pedido.parcelas() == null ? 1 : pedido.parcelas();
        List<CategoryRule> regrasDaFamilia = regras.carregar();
        List<Expense> gastos = pedido.itens().stream()
                .flatMap(item -> montar(item, pedido.data(), reciboId, estabelecimento, origem, emailUsuario,
                        regrasDaFamilia, parcelas).stream())
                .toList();
        gastos.forEach(gasto -> gasto.pagarCom(pedido.contaId()));

        List<Expense> salvos = repositorio.saveAll(gastos);
        if (pedido.dividirCom() != null) {
            divisao.dividir(salvos, pedido.dividirCom().stream()
                    .map(email -> email.trim().toLowerCase(Locale.ROOT)).distinct().toList());
        }
        // ponytail: parcela nao entra na memoria de precos (o valor seria o da parcela, nao o do produto).
        if (parcelas == 1) {
            memoriaDeProdutos.registrar(salvos);
        }
        salvos.forEach(gasto -> trilha.criou("gasto", gasto.getId(), Resumos.gasto(gasto)));
        log.info("Gastos lancados: itens={} recibo={} origem={}", salvos.size(), reciboId, origem);

        return salvos.stream().map(ExpenseResponse::de).toList();
    }

    /** Busca global: ate 20 gastos cujo item ou estabelecimento contem o termo. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> buscar(String termo) {
        // % e _ sao curingas do LIKE: fora da busca, o termo e procurado como texto.
        String limpo = termo.trim().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        if (limpo.length() < 2) {
            return List.of();
        }
        return repositorio.buscar("%" + limpo + "%", Limit.of(20)).stream().map(ExpenseResponse::de).toList();
    }

    /** Linha do extrato com a categoria que ela vai receber e se ja foi importada antes. */
    public record LinhaDaPrevia(LocalDate data, String descricao, BigDecimal valor, String idExterno,
                                String categoria, boolean jaImportada) {
    }

    /** Le o extrato e mostra o que entraria, sem gravar nada. */
    @Transactional(readOnly = true)
    public List<LinhaDaPrevia> previaDoExtrato(String conteudo) {
        List<LeitorDeExtrato.Linha> linhas = LeitorDeExtrato.ler(conteudo);
        Set<String> jaImportadas = new HashSet<>(repositorio.idsExternosJaImportados(
                linhas.stream().map(LeitorDeExtrato.Linha::idExterno).toList()));
        List<CategoryRule> regrasDaFamilia = regras.carregar();
        return linhas.stream().map(l -> new LinhaDaPrevia(l.data(), l.descricao(), l.valor(), l.idExterno(),
                categoriaDe(new ExpenseItemRequest(l.descricao(), null, l.valor(), null), "", ORIGEM_EXTRATO,
                        regrasDaFamilia),
                jaImportadas.contains(l.idExterno()))).toList();
    }

    /**
     * Grava as linhas escolhidas na previa. As ja importadas (pelo id externo) sao puladas.
     *
     * @return quantas entraram
     */
    @Transactional
    public int importar(List<LinhaImportada> linhas, UUID contaId, String emailUsuario) {
        if (contaId != null && !contas.existsById(contaId)) {
            throw new NotFoundException("Conta ou cartao nao encontrado.");
        }
        Set<String> jaImportadas = new HashSet<>(repositorio.idsExternosJaImportados(
                linhas.stream().map(LinhaImportada::idExterno).toList()));
        List<CategoryRule> regrasDaFamilia = regras.carregar();
        UUID lote = UUID.randomUUID();
        List<Expense> novos = linhas.stream()
                .filter(l -> jaImportadas.add(l.idExterno()))
                .map(l -> {
                    ExpenseItemRequest item = new ExpenseItemRequest(l.descricao(), l.categoria(), l.valor(), null);
                    // A categoria escolhida na previa vale como escolha manual.
                    String origem = Texto.vazio(l.categoria()) ? ORIGEM_EXTRATO : ORIGEM_PADRAO;
                    Expense gasto = new Expense(l.data(), lote, "", Texto.limitar(l.descricao(), 200),
                            categoriaDe(item, "", origem, regrasDaFamilia), l.valor(), TIPO_PADRAO, ORIGEM_EXTRATO,
                            emailUsuario);
                    gasto.importadoDe(l.idExterno());
                    gasto.pagarCom(contaId);
                    return gasto;
                })
                .toList();
        memoriaDeProdutos.registrar(repositorio.saveAll(novos));
        if (!novos.isEmpty()) {
            trilha.registrar(TrilhaDeAuditoria.Acao.IMPORTOU, "gasto", lote, null,
                    novos.size() + " gastos do extrato");
        }
        log.info("Extrato importado: linhas={} novas={}", linhas.size(), novos.size());
        return novos.size();
    }

    public record LinhaImportada(LocalDate data, String descricao, BigDecimal valor, String idExterno,
                                 String categoria) {
    }

    /** Gastos em CSV (separador ;), do mes ou de tudo; abre no Excel e no Google Planilhas. */
    @Transactional(readOnly = true)
    public String exportarCsv(String mes) {
        List<Expense> gastos = Texto.vazio(mes)
                ? repositorio.findAllByOrderByExpenseDateDescCreatedAtDesc()
                : repositorio.findByExpenseDateBetweenOrderByExpenseDateDesc(
                        YearMonth.parse(mes).atDay(1), YearMonth.parse(mes).atEndOfMonth());
        StringBuilder csv = new StringBuilder("data;item;categoria;valor;estabelecimento;origem;quem lancou\n");
        for (Expense g : gastos) {
            csv.append(g.getExpenseDate()).append(';')
                    .append(celulaCsv(g.getItem())).append(';')
                    .append(celulaCsv(g.getCategory())).append(';')
                    .append(g.getAmount().toPlainString().replace('.', ',')).append(';')
                    .append(celulaCsv(g.getMerchant())).append(';')
                    .append(celulaCsv(g.getSource())).append(';')
                    .append(celulaCsv(g.getUserEmail())).append('\n');
        }
        return csv.toString();
    }

    /**
     * Celula segura: aspas escapadas e, contra injecao de formula na planilha, um apostrofo
     * antes de =, +, - e @.
     */
    static String celulaCsv(String valor) {
        String texto = valor == null ? "" : valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public ExpenseResponse atualizar(UUID id, UpdateExpenseRequest pedido) {
        Expense gasto = buscar(id);
        String antes = Resumos.gasto(gasto);
        gasto.editar(
                Texto.limitar(pedido.item(), 200),
                categorias.normalizar(pedido.categoria()),
                pedido.valor()
        );
        log.info("Gasto editado: id={}", id);
        Expense salvo = repositorio.save(gasto);
        trilha.editou("gasto", id, antes, Resumos.gasto(salvo));
        return ExpenseResponse.de(salvo);
    }

    @Transactional
    public void excluir(UUID id) {
        Expense gasto = buscar(id);
        repositorio.delete(gasto);
        trilha.apagou("gasto", id, Resumos.gasto(gasto));
        log.info("Gasto apagado: id={}", id);
    }

    /** Remocao silenciosa, usada quando uma nota vinculada e apagada. */
    @Transactional
    public void excluirSeExistir(UUID id) {
        if (id != null) {
            repositorio.findById(id).ifPresent(gasto -> {
                repositorio.delete(gasto);
                trilha.apagou("gasto", id, Resumos.gasto(gasto));
            });
        }
    }

    private Expense buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Gasto nao encontrado."));
    }

    /**
     * Categoria escolhida a mao no lancamento manual e respeitada. Sem escolha, ou vinda da
     * leitura de nota, valem as regras da familia; sem regra e sem categoria, a da ultima compra
     * do mesmo produto.
     */
    private String categoriaDe(ExpenseItemRequest item, String estabelecimento, String origem,
                               List<CategoryRule> regrasDaFamilia) {
        boolean escolhidaAMao = ORIGEM_PADRAO.equals(origem) && !Texto.vazio(item.categoria());
        if (escolhidaAMao) {
            return categorias.normalizar(item.categoria());
        }
        Optional<String> automatica = RegrasDeCategoria.porRegra(regrasDaFamilia, item.item(), estabelecimento);
        if (automatica.isEmpty() && Texto.vazio(item.categoria())) {
            automatica = regras.daMemoria(item.item());
        }
        return categorias.normalizar(automatica.orElse(item.categoria()));
    }

    /** Um item vira uma linha; parcelado, uma linha por mes com o valor dividido (o centavo que sobra vai na ultima). */
    private List<Expense> montar(ExpenseItemRequest item, LocalDate data, UUID reciboId,
                                 String estabelecimento, String origem, String emailUsuario,
                                 List<CategoryRule> regrasDaFamilia, int parcelas) {
        if ((item.moedaOriginal() == null) != (item.valorOriginal() == null)) {
            throw new BusinessException("Informe a moeda e o valor original juntos.");
        }
        String categoria = categoriaDe(item, estabelecimento, origem, regrasDaFamilia);
        BigDecimal parcela = item.valor().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal ultima = item.valor().subtract(parcela.multiply(BigDecimal.valueOf(parcelas - 1L)));
        return IntStream.range(0, parcelas).mapToObj(i -> {
            Expense gasto = new Expense(
                    data.plusMonths(i),
                    reciboId,
                    estabelecimento,
                    Texto.limitar(item.item(), 200),
                    categoria,
                    i == parcelas - 1 ? ultima : parcela,
                    Texto.limitarOuPadrao(item.tipo(), 30, TIPO_PADRAO),
                    origem,
                    emailUsuario
            );
            if (parcelas > 1) {
                gasto.parcela(i + 1, parcelas);
            }
            if (item.moedaOriginal() != null) {
                // ponytail: o original tambem e dividido pelas parcelas, com o centavo na ultima.
                BigDecimal original = item.valorOriginal().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
                gasto.valorOriginal(item.moedaOriginal(), i == parcelas - 1
                        ? item.valorOriginal().subtract(original.multiply(BigDecimal.valueOf(parcelas - 1L)))
                        : original);
            }
            return gasto;
        }).toList();
    }
}
