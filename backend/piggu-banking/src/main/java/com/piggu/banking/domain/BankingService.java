package com.piggu.banking.domain;

import com.piggu.banking.api.dto.BankAccountResponse;
import com.piggu.banking.api.dto.ConnectTokenResponse;
import com.piggu.banking.config.PluggyProperties;
import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                .orElseGet(() -> conexoes.save(new BankConnection(item.id(), usuario.email())));
        sincronizar(conexao, item);
        return listar(usuario);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listar(CurrentUser usuario) {
        return contas.listarDoUsuario(usuario.email()).stream().map(BankAccountResponse::de).toList();
    }

    // ponytail: le o que a Pluggy ja tem (ela atualiza sozinha todo dia). Forcar ida ao
    // banco agora exige PATCH /items/{id} e esperar o webhook; fazer quando pedirem.
    @Transactional
    public List<BankAccountResponse> sincronizarTudo(CurrentUser usuario) {
        for (BankConnection conexao : conexoes.findByUserEmail(usuario.email())) {
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
