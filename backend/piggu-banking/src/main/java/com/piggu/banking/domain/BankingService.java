package com.piggu.banking.domain;

import com.piggu.banking.api.dto.BankAccountResponse;
import com.piggu.banking.api.dto.ConnectTokenResponse;
import com.piggu.banking.config.PluggyProperties;
import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bancos conectados via Pluggy.
 *
 * <p>O fluxo: o front pede um connect token, abre o widget da Pluggy, o usuario
 * conecta o banco la e o widget devolve o id do item. O front registra esse id aqui,
 * e a partir dai o backend le contas e saldos direto da Pluggy.</p>
 *
 * <p>O id do item chega pelo navegador, entao nao e prova de nada. Quem garante que
 * o item e de quem o registra e o clientUserId: o connect token e emitido com o id do
 * usuario logado, a Pluggy grava esse valor no item, e o registro confere.</p>
 */
@Service
public class BankingService {

    private static final Logger log = LoggerFactory.getLogger(BankingService.class);

    private final PluggyClient pluggy;
    private final PluggyProperties propriedades;
    private final BankConnectionRepository conexoes;
    private final BankAccountRepository contas;

    public BankingService(PluggyClient pluggy, PluggyProperties propriedades,
                          BankConnectionRepository conexoes, BankAccountRepository contas) {
        this.pluggy = pluggy;
        this.propriedades = propriedades;
        this.conexoes = conexoes;
        this.contas = contas;
    }

    public boolean habilitado() {
        return propriedades.habilitado();
    }

    public ConnectTokenResponse gerarConnectToken(CurrentUser usuario) {
        return new ConnectTokenResponse(pluggy.criarConnectToken(usuario.id().toString()), propriedades.sandbox());
    }

    @Transactional
    public List<BankAccountResponse> registrar(String itemId, CurrentUser usuario) {
        PluggyClient.Item item = pluggy.buscarItem(itemId);
        if (!usuario.id().toString().equals(item.clientUserId())) {
            log.warn("Registro de item Pluggy de outra conta recusado: item={}", item.id());
            throw new ForbiddenException("Esta conexao bancaria nao foi feita pela sua conta.");
        }

        BankConnection conexao = conexoes.findByPluggyItemId(item.id())
                .orElseGet(() -> conexoes.save(new BankConnection(item.id(), usuario.id())));
        sincronizar(conexao, item);
        return listar(usuario);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listar(CurrentUser usuario) {
        return contas.listarDoUsuario(usuario.id()).stream().map(BankAccountResponse::de).toList();
    }

    /**
     * Desconecta um banco: apaga o item na Pluggy e as contas guardadas aqui.
     *
     * <p>Nunca e Premium: revogar o consentimento tem de ser gratis e a qualquer momento.
     * Se a Pluggy falhar, nada e apagado aqui, para nao sobrar um item vivo la sem
     * registro do lado de ca; a pessoa tenta de novo. Item que a Pluggy ja nao conhece
     * (404) conta como desconectado.</p>
     */
    @Transactional
    public List<BankAccountResponse> desconectar(UUID conexaoId, CurrentUser usuario) {
        BankConnection conexao = conexoes.findById(conexaoId)
                .filter(encontrada -> usuario.id().equals(encontrada.getUserId()))
                .orElseThrow(() -> new NotFoundException("Banco conectado nao encontrado."));
        try {
            pluggy.apagarItem(conexao.getPluggyItemId());
        } catch (NotFoundException jaApagado) {
            log.info("Item ja nao existia na Pluggy: conexao={}", conexao.getId());
        }
        conexoes.delete(conexao);
        log.info("Banco desconectado: conexao={}", conexao.getId());
        return listar(usuario);
    }

    /** Quanto para tras se pode buscar de uma vez; a Pluggy guarda bem mais, a revisao nao. */
    public static final int MAXIMO_DE_DIAS = 90;

    /** Debito de um banco conectado, no formato da previa do importador de extrato. */
    public record GastoDoBanco(LocalDate data, String descricao, BigDecimal valor, String idExterno,
                               String conta, String moeda) {
    }

    /**
     * Debitos confirmados das contas da pessoa desde a data pedida, para revisar e importar
     * como gastos. Nada e gravado aqui: o financeiro recebe as linhas escolhidas e pula as
     * que ja entraram pelo id externo ("pluggy:" + id do lancamento). Sem transacao em volta: as
     * chamadas a Pluggy nao seguram uma conexao do banco (D-074).
     */
    public List<GastoDoBanco> gastosDesde(LocalDate desde, CurrentUser usuario) {
        LocalDate hoje = LocalDate.now();
        if (desde.isAfter(hoje) || desde.isBefore(hoje.minusDays(MAXIMO_DE_DIAS))) {
            throw new BusinessException("Escolha uma data dos ultimos " + MAXIMO_DE_DIAS + " dias.");
        }
        List<GastoDoBanco> gastos = new ArrayList<>();
        List<BankAccount> doUsuario = contas.listarDoUsuario(usuario.id());
        for (BankAccount conta : doUsuario) {
            String nome = conta.getConnection().getInstitution() + " · " + conta.getName();
            for (PluggyClient.Transacao t : pluggy.listarTransacoes(conta.getPluggyAccountId(), desde)) {
                if (t.debito() && t.confirmada() && t.valor().signum() > 0) {
                    gastos.add(new GastoDoBanco(t.data(), Texto.limitar(t.descricao(), 200), t.valor(),
                            "pluggy:" + t.id(), nome, t.moeda()));
                }
            }
        }
        gastos.sort(Comparator.comparing(GastoDoBanco::data).reversed());
        if (!doUsuario.isEmpty()) {
            conexoes.marcarUso(usuario.id(), Instant.now());
        }
        log.info("Gastos lidos dos bancos: contas={} gastos={}", doUsuario.size(), gastos.size());
        return gastos;
    }

    // ponytail: le o que a Pluggy ja tem (ela atualiza sozinha todo dia). Forcar ida ao
    // banco agora exige PATCH /items/{id} e esperar o webhook; fazer quando pedirem.
    @Transactional
    public List<BankAccountResponse> sincronizarTudo(CurrentUser usuario) {
        for (BankConnection conexao : conexoes.findByUserId(usuario.id())) {
            sincronizar(conexao, pluggy.buscarItem(conexao.getPluggyItemId()));
        }
        return listar(usuario);
    }

    private void sincronizar(BankConnection conexao, PluggyClient.Item item) {
        conexao.sincronizado(item.instituicao(), item.status());

        Map<String, BankAccount> existentes = new HashMap<>();
        contas.findByConnection(conexao).forEach(conta -> existentes.put(conta.getPluggyAccountId(), conta));

        for (PluggyClient.Conta remota : pluggy.listarContas(item.id())) {
            BankAccount conta = existentes.remove(remota.id());
            if (conta == null) {
                conta = new BankAccount(conexao, remota.id());
            }
            conta.atualizar(remota.nome(), remota.tipo(), remota.numero(), remota.saldo(), remota.moeda());
            contas.save(conta);
        }
        // Conta encerrada no banco some da Pluggy; aqui tambem.
        contas.deleteAll(existentes.values());
        log.info("Banco sincronizado: conexao={} status={} contasRemovidas={}",
                conexao.getId(), item.status(), existentes.size());
    }
}
