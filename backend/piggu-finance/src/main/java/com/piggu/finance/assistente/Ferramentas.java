package com.piggu.finance.assistente;

import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.PiggyBankResponse;
import com.piggu.finance.domain.ContasFixasService;
import com.piggu.finance.domain.ExpenseService;
import com.piggu.finance.domain.MonthlyGoalService;
import com.piggu.finance.domain.ObjetivosService;
import com.piggu.finance.domain.OrcamentoService;
import com.piggu.finance.domain.PiggyBankService;
import com.piggu.finance.domain.RelatorioService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que o assistente pode consultar: so leitura, pelos mesmos servicos das telas.
 *
 * <p>Cada ferramenta roda no pedido da propria pessoa, entao o filtro da familia vale e a
 * IA nunca ve mais do que a pessoa veria. Os totais saem somados daqui: a IA nao faz conta
 * de cabeca. Nada de e-mail nem nome de pessoa vai para fora; quem lancou nao entra.</p>
 */
@Component
public class Ferramentas {

    /** Gastos por lista: o bastante para responder, pouco para nao mandar o mes inteiro para fora. */
    static final int GASTOS_NA_LISTA = 25;

    /** Telas que o assistente pode abrir; rota do front. */
    static final Set<String> TELAS = Set.of("painel", "gastos", "receitas", "relatorios", "orcamentos",
            "contas-fixas", "contas-e-cartoes", "calendario", "planejar", "plano");

    private static final Map<String, Object> MES = Map.of("type", "string",
            "description", "Mes no formato AAAA-MM. Sem mes, o atual.");

    private final RelatorioService relatorios;
    private final ExpenseService gastos;
    private final OrcamentoService orcamentos;
    private final MonthlyGoalService limites;
    private final ContasFixasService contasFixas;
    private final PiggyBankService cofrinho;
    private final ObjetivosService objetivos;

    public Ferramentas(RelatorioService relatorios, ExpenseService gastos, OrcamentoService orcamentos,
                       MonthlyGoalService limites, ContasFixasService contasFixas, PiggyBankService cofrinho,
                       ObjetivosService objetivos) {
        this.relatorios = relatorios;
        this.gastos = gastos;
        this.orcamentos = orcamentos;
        this.limites = limites;
        this.contasFixas = contasFixas;
        this.cofrinho = cofrinho;
        this.objetivos = objetivos;
    }

    /** Resultado de uma ferramenta; {@code tela} so quando a pessoa pediu para abrir uma. */
    public record Resultado(Object dados, String tela) {

        static Resultado de(Object dados) {
            return new Resultado(dados, null);
        }
    }

    /** As declaracoes no formato de function calling do Gemini. */
    public List<Map<String, Object>> declaracoes() {
        return List.of(
                declarar("resumo_do_mes",
                        "Receitas, gastos, sobra, taxa de poupanca, comparacao com o mes anterior, projecao do "
                                + "fim do mes, gasto por categoria e o limite total do mes, se houver.",
                        Map.of("mes", MES), List.of()),
                declarar("gastos_do_mes",
                        "Gastos de um mes, opcionalmente so de uma categoria. Devolve total, quantidade e os "
                                + GASTOS_NA_LISTA + " maiores.",
                        Map.of("mes", MES, "categoria", Map.of("type", "string",
                                "description", "Nome da categoria, como aparece no resumo do mes.")),
                        List.of()),
                declarar("buscar_gastos",
                        "Procura gastos de qualquer data pelo nome do item ou do estabelecimento (ate 20, os "
                                + "mais recentes) e devolve o total do que achou.",
                        Map.of("texto", Map.of("type", "string", "description", "O que procurar, ex.: uber, padaria.")),
                        List.of("texto")),
                declarar("orcamentos",
                        "Limite por categoria no mes, quanto ja foi gasto, percentual e alerta (OK, ATENCAO a "
                                + "partir de 80%, ESTOUROU a partir de 100%).",
                        Map.of("mes", MES), List.of()),
                declarar("contas_fixas",
                        "Contas fixas do mes (aluguel, luz, internet) com valor, vencimento e situacao "
                                + "(PAGA, PENDENTE, VENCIDA).",
                        Map.of("mes", MES), List.of()),
                declarar("cofrinho",
                        "Saldo do cofrinho da familia e os cofrinhos por objetivo: alvo, guardado, quanto falta, "
                                + "prazo e quanto guardar por mes.",
                        Map.of(), List.of()),
                declarar("abrir_tela",
                        "Abre uma tela do app quando a pessoa pede para ir a algum lugar ou para lancar, mudar "
                                + "ou apagar algo (o assistente nao grava nada).",
                        Map.of("tela", Map.of("type", "string", "enum", TELAS.stream().sorted().toList())),
                        List.of("tela"))
        );
    }

    public Resultado executar(String nome, JsonNode argumentos, CurrentUser usuario) {
        return switch (nome) {
            case "resumo_do_mes" -> Resultado.de(resumo(mes(argumentos)));
            case "gastos_do_mes" -> Resultado.de(gastosDoMes(mes(argumentos), texto(argumentos, "categoria")));
            case "buscar_gastos" -> Resultado.de(buscar(texto(argumentos, "texto")));
            case "orcamentos" -> Resultado.de(orcamentos.doMes(mes(argumentos)));
            case "contas_fixas" -> Resultado.de(contasDoMes(mes(argumentos)));
            case "cofrinho" -> Resultado.de(cofrinho(usuario));
            case "abrir_tela" -> abrir(texto(argumentos, "tela"));
            default -> Resultado.de(Map.of("erro", "Ferramenta desconhecida: " + nome));
        };
    }

    private Map<String, Object> resumo(YearMonth mes) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("resumo", relatorios.resumo(mes));
        BigDecimal limite = limites.listar().get(mes.toString());
        dados.put("limiteDoMes", limite);
        return dados;
    }

    private Map<String, Object> gastosDoMes(YearMonth mes, String categoria) {
        List<ExpenseResponse> doMes = gastos.listarDoMes(mes.toString()).stream()
                .filter(g -> categoria.isBlank() || g.categoria().equalsIgnoreCase(categoria))
                .toList();
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("mes", mes.toString());
        if (!categoria.isBlank()) {
            dados.put("categoria", categoria);
        }
        dados.put("total", somar(doMes));
        dados.put("quantidade", doMes.size());
        dados.put("maiores", doMes.stream()
                .sorted(Comparator.comparing(ExpenseResponse::valor).reversed())
                .limit(GASTOS_NA_LISTA)
                .map(Ferramentas::linha)
                .toList());
        return dados;
    }

    private Map<String, Object> buscar(String texto) {
        List<ExpenseResponse> achados = gastos.buscar(texto);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("texto", texto);
        dados.put("total", somar(achados));
        dados.put("quantidade", achados.size());
        dados.put("gastos", achados.stream().map(Ferramentas::linha).toList());
        return dados;
    }

    private List<Map<String, Object>> contasDoMes(YearMonth mes) {
        return contasFixas.doMes(mes).stream().map(conta -> {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("descricao", conta.conta().getDescription());
            linha.put("categoria", conta.conta().getCategory());
            linha.put("valor", conta.conta().getAmount());
            linha.put("vencimento", conta.vencimento());
            linha.put("situacao", conta.situacao());
            return linha;
        }).toList();
    }

    private Map<String, Object> cofrinho(CurrentUser usuario) {
        PiggyBankResponse geral = cofrinho.consultar(usuario);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("saldo", geral.saldo());
        dados.put("totalDepositado", geral.totalDepositos());
        dados.put("gastoDesdeOPrimeiroDeposito", geral.totalGastos());
        dados.put("objetivos", objetivos.listar());
        return dados;
    }

    private static Resultado abrir(String tela) {
        if (!TELAS.contains(tela)) {
            return Resultado.de(Map.of("erro", "Tela desconhecida."));
        }
        return new Resultado(Map.of("aberta", tela), tela);
    }

    /** O que vai para a IA de cada gasto: sem quem lancou nem ids. */
    private static Map<String, Object> linha(ExpenseResponse gasto) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("data", gasto.data());
        linha.put("item", gasto.item());
        if (gasto.estabelecimento() != null && !gasto.estabelecimento().isBlank()) {
            linha.put("estabelecimento", gasto.estabelecimento());
        }
        linha.put("categoria", gasto.categoria());
        linha.put("valor", gasto.valor());
        return linha;
    }

    private static BigDecimal somar(List<ExpenseResponse> lista) {
        return lista.stream().map(ExpenseResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Mes invalido ou ausente vira o atual: a IA as vezes manda "setembro". */
    private static YearMonth mes(JsonNode argumentos) {
        String mes = texto(argumentos, "mes");
        try {
            return mes.isBlank() ? YearMonth.now(Meses.BRASILIA) : YearMonth.parse(mes);
        } catch (DateTimeParseException erro) {
            return YearMonth.now(Meses.BRASILIA);
        }
    }

    private static String texto(JsonNode argumentos, String campo) {
        if (argumentos == null || !argumentos.hasNonNull(campo)) {
            return "";
        }
        return argumentos.path(campo).asString("").strip();
    }

    private static Map<String, Object> declarar(String nome, String descricao, Map<String, Object> propriedades,
                                                List<String> obrigatorios) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("type", "object");
        parametros.put("properties", propriedades);
        if (!obrigatorios.isEmpty()) {
            parametros.put("required", obrigatorios);
        }
        return Map.of("name", nome, "description", descricao, "parameters", parametros);
    }
}
