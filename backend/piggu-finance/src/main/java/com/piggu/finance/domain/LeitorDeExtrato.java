package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.web.Texto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le extrato de banco em OFX ou CSV e devolve os debitos como gastos.
 *
 * <p>Cada linha ganha um id externo: o FITID do OFX, a coluna id do CSV, ou um hash de
 * data + descricao + valor (com a ordem de repeticao no arquivo, para duas compras iguais
 * no mesmo dia nao virarem uma so). E esse id que evita importar duas vezes.</p>
 */
public final class LeitorDeExtrato {

    public static final int MAXIMO_DE_LINHAS = 2000;

    private static final Pattern TRANSACAO_OFX = Pattern.compile("<STMTTRN>(.*?)(</STMTTRN>|(?=<STMTTRN>)|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_OFX = DateTimeFormatter.ofPattern("yyyyMMdd");

    private LeitorDeExtrato() {
    }

    public record Linha(LocalDate data, String descricao, BigDecimal valor, String idExterno) {
    }

    public static List<Linha> ler(String conteudo) {
        if (Texto.vazio(conteudo)) {
            throw new BusinessException("O arquivo esta vazio.");
        }
        List<Linha> linhas = conteudo.toUpperCase().contains("<OFX>") ? ofx(conteudo) : csv(conteudo);
        if (linhas.size() > MAXIMO_DE_LINHAS) {
            throw new BusinessException("Extrato grande demais: no maximo " + MAXIMO_DE_LINHAS + " lancamentos por vez.");
        }
        return linhas;
    }

    static List<Linha> ofx(String conteudo) {
        List<Linha> linhas = new ArrayList<>();
        Matcher transacao = TRANSACAO_OFX.matcher(conteudo);
        while (transacao.find()) {
            String bloco = transacao.group(1);
            BigDecimal valor = new BigDecimal(campo(bloco, "TRNAMT").replace(',', '.'));
            if (valor.signum() >= 0) {
                continue; // credito: nao e gasto
            }
            String data = campo(bloco, "DTPOSTED");
            String descricao = campo(bloco, "MEMO").isEmpty() ? campo(bloco, "NAME") : campo(bloco, "MEMO");
            String id = campo(bloco, "FITID");
            LocalDate dia = LocalDate.parse(data.substring(0, 8), DATA_OFX);
            linhas.add(new Linha(dia, limpar(descricao), valor.negate(), id.isEmpty() ? null : "ofx:" + id));
        }
        if (linhas.isEmpty() && !TRANSACAO_OFX.matcher(conteudo).find()) {
            throw new BusinessException("Nao achei lancamentos nesse OFX.");
        }
        return comIdsGerados(linhas);
    }

    /** CSV com cabecalho: data, descricao e valor (e id, se houver). Separador ; ou ,. */
    static List<Linha> csv(String conteudo) {
        String[] linhasDoArquivo = conteudo.replace("﻿", "").split("\\r?\\n");
        String cabecalho = linhasDoArquivo[0];
        String separador = cabecalho.contains(";") ? ";" : ",";
        List<String> colunas = List.of(Texto.semAcento(cabecalho.toLowerCase()).split(separador)).stream()
                .map(String::trim).map(c -> c.replace("\"", "")).toList();
        int data = indice(colunas, "data", "date");
        int descricao = indice(colunas, "descricao", "historico", "description", "item", "lancamento");
        int valor = indice(colunas, "valor", "value", "amount", "quantia");
        int id = colunas.indexOf("id");
        if (data < 0 || descricao < 0 || valor < 0) {
            throw new BusinessException("O CSV precisa das colunas data, descricao e valor.");
        }
        List<Linha> lidas = new ArrayList<>();
        for (int i = 1; i < linhasDoArquivo.length; i++) {
            if (linhasDoArquivo[i].isBlank()) {
                continue;
            }
            String[] celulas = linhasDoArquivo[i].split(separador + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            try {
                lidas.add(new Linha(dataDe(celula(celulas, data)), limpar(celula(celulas, descricao)),
                        valorDe(celula(celulas, valor)), id >= 0 && !celula(celulas, id).isEmpty()
                        ? "csv:" + celula(celulas, id) : null));
            } catch (DateTimeParseException | NumberFormatException | ArrayIndexOutOfBoundsException invalida) {
                throw new BusinessException("Linha " + (i + 1) + " do CSV nao pode ser lida.");
            }
        }
        // Extrato com sinal: so os negativos sao gastos. Planilha sem sinal: tudo e gasto.
        boolean temSinal = lidas.stream().anyMatch(l -> l.valor().signum() < 0);
        List<Linha> gastos = lidas.stream()
                .filter(l -> !temSinal || l.valor().signum() < 0)
                .map(l -> new Linha(l.data(), l.descricao(), l.valor().abs(), l.idExterno()))
                .toList();
        return comIdsGerados(gastos);
    }

    private static List<Linha> comIdsGerados(List<Linha> linhas) {
        Map<String, Integer> repeticoes = new HashMap<>();
        return linhas.stream().map(l -> {
            if (l.idExterno() != null) {
                return l;
            }
            String base = l.data() + "|" + l.descricao().toLowerCase() + "|" + l.valor().stripTrailingZeros().toPlainString();
            int vez = repeticoes.merge(base, 1, Integer::sum);
            return new Linha(l.data(), l.descricao(), l.valor(), "hash:" + sha256(base + "|" + vez).substring(0, 32));
        }).toList();
    }

    private static String campo(String bloco, String nome) {
        Matcher m = Pattern.compile("<" + nome + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(bloco);
        return m.find() ? m.group(1).trim() : "";
    }

    private static int indice(List<String> colunas, String... nomes) {
        for (String nome : nomes) {
            int i = colunas.indexOf(nome);
            if (i >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String celula(String[] celulas, int i) {
        return celulas[i].trim().replaceAll("^\"|\"$", "").trim();
    }

    private static LocalDate dataDe(String texto) {
        return texto.contains("/") ? LocalDate.parse(texto, DATA_BR) : LocalDate.parse(texto.substring(0, 10));
    }

    /** "1.234,56" ou "1,234.56": o ultimo separador e o decimal; aceita "R$" e espacos. */
    private static BigDecimal valorDe(String texto) {
        String limpo = texto.replaceAll("[^0-9,.\\-]", "");
        return new BigDecimal(limpo.lastIndexOf(',') > limpo.lastIndexOf('.')
                ? limpo.replace(".", "").replace(',', '.')
                : limpo.replace(",", ""));
    }

    private static String limpar(String descricao) {
        String texto = Texto.limitar(Texto.espacoUnico(descricao), 200);
        return texto.isEmpty() ? "Sem descricao" : texto;
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
