package com.piggu.finance.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.assistente.AssistenteService;
import com.piggu.finance.assistente.ConversaComGemini;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assistente de IA (Premium). So le: perguntas sobre gastos, orcamento, contas e cofrinho.
 *
 * <p>O membro fica de fora, como do resto do financeiro.</p>
 */
@Tag(name = "Assistente", description = "Assistente de IA (Premium): perguntas sobre gastos, orçamento, contas e cofrinho.")
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistenteService servico;
    private final Consentimentos consentimentos;

    public AssistantController(AssistenteService servico, Consentimentos consentimentos) {
        this.servico = servico;
        this.consentimentos = consentimentos;
    }

    /** @param pessoa verdadeiro na fala da pessoa, falso na resposta do assistente */
    public record FalaRequest(boolean pessoa, @NotNull @Size(max = 4000) String texto) {
    }

    /**
     * @param historico     falas anteriores desta conversa, que so o aparelho guarda
     * @param moeda         moeda em que a pessoa ve os valores
     * @param autorizoIa    verdadeiro quando a pessoa acabou de autorizar o envio ao Gemini
     * @param versaoDoAviso versao do aviso de privacidade que a pessoa viu
     */
    public record PerguntaRequest(
            @NotBlank(message = "Escreva a pergunta.") @Size(max = 500, message = "A pergunta e longa demais.")
            String pergunta,
            @Size(max = 40) List<@Valid FalaRequest> historico,
            @Pattern(regexp = "[A-Z]{3}") String moeda,
            Boolean autorizoIa,
            String versaoDoAviso) {
    }

    @Operation(summary = "Situação do assistente (uso e autorização)")
    @GetMapping
    public AssistenteService.Situacao situacao(@AuthUser CurrentUser usuario) {
        return servico.situacao(usuario);
    }

    @Operation(summary = "Fazer uma pergunta ao assistente")
    @PostMapping("/ask")
    public AssistenteService.Resposta perguntar(@Valid @RequestBody PerguntaRequest pedido,
                                                @AuthUser CurrentUser usuario) {
        List<ConversaComGemini.Fala> historico = pedido.historico() == null ? List.of()
                : pedido.historico().stream().map(f -> new ConversaComGemini.Fala(f.pessoa(), f.texto())).toList();
        return servico.perguntar(pedido.pergunta().strip(), historico,
                pedido.moeda() == null ? "BRL" : pedido.moeda(), pedido.autorizoIa(), pedido.versaoDoAviso(), usuario);
    }

    public record AutorizacaoRequest(@NotBlank @Size(max = 20) String versaoDoAviso) {
    }

    /**
     * Grava a autorizacao no toque em "Autorizo", sem esperar a primeira pergunta: quem
     * autoriza e fecha o assistente nao precisa autorizar de novo. Vale ate o aviso mudar.
     */
    @Operation(summary = "Autorizar o envio das perguntas ao Gemini")
    @PostMapping("/consent")
    public AssistenteService.Situacao autorizar(@Valid @RequestBody AutorizacaoRequest pedido,
                                                @AuthUser CurrentUser usuario) {
        return servico.autorizar(usuario, pedido.versaoDoAviso());
    }

    /** Retira a autorizacao de enviar perguntas e totais ao Gemini; a proxima pergunta pede de novo. */
    @Operation(summary = "Retirar a autorização do assistente")
    @DeleteMapping("/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarConsentimento(@AuthUser CurrentUser usuario) {
        consentimentos.retirar(usuario, AssistenteService.FINALIDADE);
    }
}
