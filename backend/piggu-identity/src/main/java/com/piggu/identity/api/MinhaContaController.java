package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.MinhaContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;


/** CPF e foto da conta, copia dos dados e exclusao da conta (LGPD art. 18). Gratis em qualquer plano e papel. */
@Tag(name = "Minha conta", description = "CPF, país, foto, termos, cópia dos dados e exclusão da conta.")
@RestController
@RequestMapping("/api/me")
public class MinhaContaController {

    private final MinhaContaService servico;

    public MinhaContaController(MinhaContaService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Baixar uma cópia de todos os meus dados (LGPD)")
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportar(@AuthUser CurrentUser usuario, @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("piggu-meus-dados.json").build().toString())
                .body(servico.exportar(usuario, token.getTokenValue()));
    }

    public record CpfRequest(@NotBlank(message = "Informe o CPF.") @Size(max = 20) String cpf) {
    }

    /** Uma vez por conta; depois de informado o CPF fica preso a ela. */
    @Operation(summary = "Informar o CPF")
    @PutMapping("/cpf")
    public UserResponse informarCpf(@Valid @RequestBody CpfRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.informarCpf(usuario.id(), pedido.cpf());
    }

    /** @param documento o CPF, aqui ou fora; pode faltar */
    public record PaisRequest(@NotBlank(message = "Escolha o país onde você mora.") @Size(max = 2) String pais,
                              @Size(max = 40) String documento) {
    }

    /** Onde mora e, se vier, o CPF; o documento fica preso a conta. */
    @Operation(summary = "Informar o país onde mora (e o CPF)")
    @PutMapping("/pais")
    public UserResponse informarPais(@Valid @RequestBody PaisRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.informarPais(usuario.id(), pedido.pais(), pedido.documento());
    }

    /** @param cpf e telefone so quando a conta ainda nao tem (quem entrou pelo Google, D-088) */
    public record SobreVoceRequest(@NotBlank(message = "Conte como podemos te chamar.") @Size(max = 120) String apelido,
                                   LocalDate nascimento,
                                   @Size(max = 40) String cpf,
                                   @Size(max = 30) String telefone) {
    }

    /** Como quer ser chamado e, so na primeira vez, nascimento, CPF e celular: completa o cadastro. */
    @Operation(summary = "Completar o cadastro: apelido, nascimento, CPF e celular")
    @PutMapping("/sobre-voce")
    public UserResponse sobreVoce(@Valid @RequestBody SobreVoceRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.sobreVoce(usuario.id(), pedido.apelido(), pedido.nascimento(), pedido.cpf(), pedido.telefone());
    }

    public record FotoRequest(
@NotBlank(message = "Escolha uma foto.") @Size(max = 250_000) String imagemBase64,
                              @NotBlank @Size(max = 40) String mimeType) {
    }

    public record TermosRequest(@NotBlank(message = "Informe a versao dos termos.") @Size(max = 20) String versao) {
    }

    /** Os Termos mudaram: a pessoa aceita a versao nova para seguir usando o app. */
    @Operation(summary = "Aceitar a versão nova dos termos")
    @PutMapping("/termos")
    public UserResponse aceitarTermos(@Valid @RequestBody TermosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.aceitarTermos(usuario.id(), pedido.versao());
    }

    /** Foto de perfil propria; o servidor reduz, recorta e regrava antes de guardar. */
    @Operation(summary = "Trocar a foto de perfil")
    @PutMapping("/foto")
    public UserResponse trocarFoto(@Valid @RequestBody FotoRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.trocarFoto(usuario.id(), pedido.imagemBase64(), pedido.mimeType());
    }

    /** Sem foto nenhuma: ficam as iniciais. */
    @Operation(summary = "Remover a foto de perfil")
    @DeleteMapping("/foto")
    public UserResponse removerFoto(@AuthUser CurrentUser usuario) {
        return servico.removerFoto(usuario.id());
    }

    /** Volta a foto da conta Google. */
    @Operation(summary = "Usar a foto da conta Google")
    @PutMapping("/foto/google")
    public UserResponse usarFotoDoGoogle(@AuthUser CurrentUser usuario) {
        return servico.usarFotoDoGoogle(usuario.id());
    }

    @Operation(summary = "Excluir a conta")
    @DeleteMapping
    public ResponseEntity<Void> excluir(@AuthUser CurrentUser usuario) {
        servico.excluir(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
