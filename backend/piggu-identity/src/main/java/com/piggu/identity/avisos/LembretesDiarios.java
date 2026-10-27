package com.piggu.identity.avisos;

import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.TokenService;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Lembrete diario: fatura chegando, orcamento em 80/100% e conta fixa vencendo, por
 * e-mail ou push, para quem ligou o canal no perfil.
 *
 * <p>Como na exclusao em cascata, sem credencial de servico: o identity emite um token
 * de cinco minutos da propria pessoa e pede ao financeiro os avisos de hoje, que vem
 * filtrados pela familia dela. Cada aviso tem uma chave; as ja mandadas ficam em
 * alerts_sent e nao repetem. Sem canal ligado ou com falha na entrega, nada e marcado
 * e o aviso tenta de novo no dia seguinte.</p>
 */
@Service
public class LembretesDiarios {

    private static final Logger log = LoggerFactory.getLogger(LembretesDiarios.class);

    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final TokenService tokens;
    private final AvisosEnviados enviados;
    private final Aparelhos aparelhos;
    private final CanalDeEmail email;
    private final CanalDePush push;
    private final AvisosDoFinanceiro financeiro;

    public LembretesDiarios(UserAccountRepository usuarios, FamiliaService familias, TokenService tokens,
                            AvisosEnviados enviados, Aparelhos aparelhos, CanalDeEmail email, CanalDePush push,
                            AvisosDoFinanceiro financeiro) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.tokens = tokens;
        this.enviados = enviados;
        this.aparelhos = aparelhos;
        this.email = email;
        this.push = push;
        this.financeiro = financeiro;
    }

    @Scheduled(cron = "${piggu.avisos.cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
    public void agendado() {
        enviados.limparAntigos();
        int avisadas = enviarDeHoje();
        log.info("Lembretes do dia: pessoasAvisadas={}", avisadas);
    }

    /** @return quantas pessoas receberam algo */
    public int enviarDeHoje() {
        if (!financeiro.ligado()) {
            return 0;
        }
        int avisadas = 0;
        for (UserAccount conta : usuarios.comAvisosLigados()) {
            try {
                if (avisar(conta)) {
                    avisadas++;
                }
            } catch (RuntimeException erro) {
                // Uma pessoa com problema (financeiro fora, familia apagada) nao para as outras.
                log.warn("Lembrete nao saiu para a conta {}: {}", conta.getId(), erro.getMessage());
            }
        }
        return avisadas;
    }

    private boolean avisar(UserAccount conta) {
        Set<String> jaMandados = enviados.jaMandados(conta.getId());
        List<Aviso> novos = buscar(conta).stream().filter(aviso -> !jaMandados.contains(aviso.chave())).toList();
        if (novos.isEmpty()) {
            return false;
        }
        Mensagem mensagem = Mensagem.de(novos, conta.getCurrency());
        boolean entregue = false;
        if (conta.isAlertsByEmail() && email.ligado()) {
            entregue = email.enviar(conta.getEmail(), mensagem);
        }
        if (conta.isAlertsByPush() && push.ligado()) {
            // So o iPhone por enquanto: o Android ja registra o aparelho, mas o envio pelo FCM ainda nao existe.
            for (String aparelho : aparelhos.doUsuario(conta.getId(), "IOS")) {
                CanalDePush.Resultado resultado = push.enviar(aparelho, mensagem);
                if (resultado == CanalDePush.Resultado.ENVIADO) {
                    entregue = true;
                } else if (resultado == CanalDePush.Resultado.TOKEN_INVALIDO) {
                    aparelhos.esquecer(aparelho);
                }
            }
        }
        if (entregue) {
            enviados.marcar(conta.getId(), novos.stream().map(Aviso::chave).toList());
        }
        return entregue;
    }

    private List<Aviso> buscar(UserAccount conta) {
        return financeiro.deHoje(tokens.gerarTokenDoLembrete(conta, familias.daConta(conta)));
    }
}
