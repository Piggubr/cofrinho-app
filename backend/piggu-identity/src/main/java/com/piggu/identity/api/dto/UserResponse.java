package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;

import com.piggu.identity.domain.ModulosDeEstiloDeVida;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil publico do usuario — o mesmo formato que {@code usuarioPublico_} devolvia,
 * acrescido do id.
 *
 * <p>{@code cpfInformado} falso faz o Perfil oferecer informar o CPF (opcional). Conta
 * antiga com documento de outro pais ja conta como informada. O numero nunca vem aqui;
 * so na exportacao dos dados.</p>
 *
 * <p>{@code foto} e a que o app mostra (a propria, senao a do Google); {@code fotoPropria} e
 * {@code temFotoDoGoogle} dizem quais botoes o Perfil oferece.</p>
 *
 * <p>{@code termosAceitos} e a versao dos Termos que a pessoa aceitou por ultimo (nula em
 * conta antiga, da lista fechada). Diferente da vigente, o front pede o aceite de novo
 * antes de liberar o app.</p>
 *
 * <p>{@code temSenha} diz se a conta tambem entra por e-mail e senha (o Perfil oferece
 * criar ou trocar); {@code emailConfirmado} e falso enquanto a conta, criada por senha,
 * nao entrou nenhuma vez pelo Google.</p>
 *
 * <p>{@code apelido} ou {@code nascimento} nulos: o front pede os dois antes de liberar o app.</p>
 */

public record UserResponse(
        UUID id,
        String email,
        String nome,
        String primeiroNome,
        String apelido,
        String foto,
        PigguRole role,
        boolean ativo,
        Map<String, Object> permissoes,
        Preferencias preferencias,
        Plano plano,
        Instant premiumAte,
        UUID familia,
        Avisos avisos,
        boolean cpfInformado,
        boolean fotoPropria,
        boolean temFotoDoGoogle,
        String termosAceitos,
        boolean temSenha,
        boolean emailConfirmado,
        String pais,
        LocalDate nascimento,
        Compartilhamento compartilhamento
) {

    /**
     * @param mostraTudoAoParceiro o parceiro ve tudo o que a pessoa lanca, nao so o compartilhado
     * @param compartilharPorPadrao lancamentos novos ja comecam marcados como compartilhados
     */
    public record Compartilhamento(boolean mostraTudoAoParceiro, boolean compartilharPorPadrao) {
    }

    /** Por onde chegam os avisos de fatura, orcamento e conta fixa quando o app esta fechado. */
    public record Avisos(boolean email, boolean push) {
    }

    /**
     * Moeda dos valores, as duas moedas da cotacao (separadas da moeda dos valores), se a
     * cotacao aparece no topo, fuso, idioma e os modulos de estilo de vida ligados no menu.
     */
    public record Preferencias(String moeda, String moedaCotacao, String moedaConversao, boolean mostrarCotacao,
                               String fuso, String idioma, List<String> modulos) {

        public Preferencias(String moeda, String moedaConversao, boolean mostrarCotacao) {
            this(moeda, moeda, moedaConversao, mostrarCotacao, null, null, ModulosDeEstiloDeVida.TODOS);
        }
    }

    /** O plano vem da familia: o titular assina e todos usam. */
    public static UserResponse de(UserAccount conta, Household familia) {
        return new UserResponse(
                conta.getId(),
                conta.getEmail(),
                conta.nomeExibicao(),
                conta.primeiroNomeExibicao(),
                conta.getNickname(),
                conta.fotoExibida(),
                conta.getRole(),
                conta.isActive(),
                conta.getPermissions(),
                new Preferencias(conta.getCurrency(), conta.getQuoteCurrency(), conta.getConversionCurrency(),
                        conta.isShowExchangeRate(),
                        conta.getTimezone(), conta.getLocale(), conta.getModulos()),
                familia.planoPara(conta),
                familia.planoPara(conta) == Plano.PREMIUM ? familia.getPremiumUntil() : null,
                familia.getId(),
                new Avisos(conta.isAlertsByEmail(), conta.isAlertsByPush()),
                conta.documentoEmDia(),
                conta.temFotoPropria(),
                conta.getGooglePicture() != null,
                conta.getTermsVersion(),
                conta.temSenha(),
                conta.isEmailVerified(),
                conta.getPais(),
                conta.getBirthDate(),
                new Compartilhamento(conta.getShowsAllTo() != null, conta.isSharesByDefault())
        );
    }
}
