package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conta de usuario — o equivalente a uma linha da aba "Usuarios".
 *
 * <p>Os dados vindos do Google (nome, foto, primeiro nome) sao atualizados a cada login,
 * como fazia {@code obterOuCriarUsuario_}. A foto propria, quando a pessoa escolhe uma,
 * aparece no lugar da do Google. O apelido, quando preenchido, tem preferencia
 * sobre o nome do Google na hora de exibir.</p>
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PigguRole role;

    @Column(length = 120)
    private String nickname;

    @Column(name = "google_name", length = 120)
    private String googleName;

    @Column(name = "google_picture", length = 1000)
    private String googlePicture;

    /** Foto escolhida pela pessoa ({@link FotoDePerfil}); tem preferencia sobre a do Google. */
    @Column(name = "foto_propria", columnDefinition = "text")
    private String fotoPropria;

    /** A pessoa tirou a foto: ficam as iniciais, nem a do Google aparece. */
    @Column(name = "sem_foto", nullable = false)
    private boolean semFoto;

    @Column(name = "first_name", length = 80)
    private String firstName;

    /** Decide se a pessoa e menor de idade ({@link #MAIORIDADE}); entra uma vez, como o CPF. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private boolean active = true;

    /** Desde quando a conta esta desativada; a retencao apaga depois de um prazo. */
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Column(name = "conversion_currency", nullable = false, length = 3)
    private String conversionCurrency = "USD";

    @Column(name = "show_exchange_rate", nullable = false)
    private boolean showExchangeRate = true;

    /** Fuso da pessoa (IANA); conta nova nasce no horario de Brasilia. */
    @Column(nullable = false, length = 50)
    private String timezone = "America/Sao_Paulo";

    /** Idioma da interface; por enquanto so pt-BR, os textos ja estao prontos para traducao. */
    @Column(nullable = false, length = 10)
    private String locale = "pt-BR";

    /** Telas de estilo de vida no menu (ver {@link ModulosDeEstiloDeVida}); conta nova nasce sem nenhuma. */
    @Column(name = "lifestyle_modules", length = 200)
    private String lifestyleModules = "";

    /** Avisos fora do app (fatura, orcamento, conta fixa); cada canal nasce desligado. */
    @Column(name = "alerts_by_email", nullable = false)
    private boolean alertsByEmail;

    @Column(name = "alerts_by_push", nullable = false)
    private boolean alertsByPush;

    /** Familia a que a conta pertence; o plano e os dados de dominio sao dela. */
    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** CPF cifrado ({@link CofreDeCpf}); nulo ate a pessoa informar. */
    @Column(name = "cpf_cifrado", length = 200)
    private String cpfCifrado;

    /** HMAC do CPF, unico entre as contas: uma pessoa, uma conta. */
    @Column(name = "cpf_indice", length = 64)
    private String cpfIndice;

    @Column(name = "cpf_informado_em")
    private Instant cpfInformadoEm;

    /**
     * Pais onde mora (ISO 3166). No Brasil o CPF e obrigatorio; nos paises de
     * {@link DocumentosDeIdentidade} o documento de la; nos demais, nenhum. Nulo so em
     * conta que disse "fora do Brasil" antes de existir a lista de paises.
     */
    @Column(name = "country", length = 2)
    private String country = DocumentosDeIdentidade.BRASIL;

    /** Documento de quem mora fora ({@link DocumentosDeIdentidade.Tipo}), cifrado como o CPF. */
    @Column(name = "id_doc_type", length = 20)
    private String idDocType;

    @Column(name = "id_doc_cifrado", length = 200)
    private String idDocCifrado;

    /** HMAC do tipo + numero, unico entre as contas. */
    @Column(name = "id_doc_indice", length = 64)
    private String idDocIndice;

    @Column(name = "id_doc_informado_em")
    private Instant idDocInformadoEm;

    /** BCrypt da senha ({@link Senhas}); nulo = a conta so entra pelo Google. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** O e-mail ja foi provado (pelo Google); o cadastro por senha nasce sem prova. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** SHA-256 do link de confirmacao do e-mail ainda nao usado. */
    @Column(name = "email_token_hash", length = 64)
    private String emailTokenHash;

    @Column(name = "email_token_expires_at")
    private Instant emailTokenExpiresAt;

    /** Celular em E.164 (+5511999998888), pedido no cadastro por senha; ainda sem prova por SMS. */
    @Column(name = "phone", length = 16)
    private String phone;

    /** Senhas erradas seguidas; ao chegar no limite a entrada por senha trava um tempo. */
    @Column(name = "password_failures", nullable = false)
    private int passwordFailures;

    @Column(name = "password_locked_until")
    private Instant passwordLockedUntil;

    /** Quando a conta usou o teste gratis; nulo = ainda pode usar. */
    @Column(name = "trial_used_at")
    private Instant trialUsedAt;

    /** Cliente no provedor de pagamento de quem assinou, para abrir o portal depois. */
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, PigguRole role, UUID householdId) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.role = role;
        this.householdId = householdId;
    }

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        updatedAt = Instant.now();
    }

    /** Grava os dados do perfil Google, com os mesmos limites de tamanho do Apps Script. */
    public void atualizarPerfilGoogle(String nome, String primeiroNome, String foto) {
        this.googleName = nome;
        this.firstName = primeiroNome;
        this.googlePicture = foto;
        this.lastLoginAt = Instant.now();
    }

    /** Entrou de novo (por senha); o Google atualiza isto em {@link #atualizarPerfilGoogle}. */
    public void registrarAcesso() {
        this.lastLoginAt = Instant.now();
    }

    public boolean temSenha() {
        return passwordHash != null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** @param hash ja calculado por {@link Senhas#hash}; zera as tentativas erradas */
    public void definirSenha(String hash) {
        this.passwordHash = hash;
        this.passwordFailures = 0;
        this.passwordLockedUntil = null;
    }

    /** Tira a senha: a conta volta a entrar so pelo Google. */
    public void apagarSenha() {
        definirSenha(null);
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void confirmarEmail() {
        this.emailVerified = true;
        this.emailTokenHash = null;
        this.emailTokenExpiresAt = null;
    }

    /** Guarda o hash do link novo; o anterior deixa de valer. */
    public void pedirConfirmacaoDeEmail(String hash, Instant validoAte) {
        this.emailTokenHash = hash;
        this.emailTokenExpiresAt = validoAte;
    }

    public boolean linkDeEmailValido(Instant agora) {
        return emailTokenExpiresAt != null && agora.isBefore(emailTokenExpiresAt);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    /** A entrada por senha esta travada por excesso de tentativas erradas. */
    public boolean senhaTravada(Instant agora) {
        return passwordLockedUntil != null && passwordLockedUntil.isAfter(agora);
    }

    /**
     * Conta uma senha errada; no limite trava a entrada por senha ate {@code agora + trava}
     * e recomeca a contagem.
     */
    public void registrarSenhaErrada(int limite, java.time.Duration trava, Instant agora) {
        passwordFailures++;
        if (passwordFailures >= limite) {
            passwordLockedUntil = agora.plus(trava);
            passwordFailures = 0;
        }
    }

    public void registrarSenhaCerta() {
        this.passwordFailures = 0;
        this.passwordLockedUntil = null;
    }

    /** Nome de exibicao: apelido, senao nome do Google, senao o primeiro nome. */
    public String nomeExibicao() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        if (googleName != null && !googleName.isBlank()) {
            return googleName;
        }
        return primeiroNomeExibicao();
    }

    public String primeiroNomeExibicao() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        if (googleName != null && !googleName.isBlank()) {
            return googleName.split("\\s+")[0];
        }
        return email.split("@")[0];
    }

    /** Idade a partir da qual a pessoa deixa de ser menor e so ela ve os proprios dados. */
    public static final int MAIORIDADE = 18;

    /** Idade minima para ter conta (LGPD art. 14). */
    public static final int IDADE_MINIMA = 13;

    public LocalDate getBirthDate() {
        return birthDate;
    }

    /** Fica presa a conta: mudar depois deixaria o menor sair da vista do titular. */
    public void informarNascimento(LocalDate data) {
        if (birthDate != null) {
            throw new IllegalStateException("A conta ja tem data de nascimento");
        }
        this.birthDate = data;
    }

    /** Menor de 18 com data informada; sem data, ninguem trata a pessoa como menor. */
    public boolean menorDeIdade(LocalDate hoje) {
        return birthDate != null && Period.between(birthDate, hoje).getYears() < MAIORIDADE;
    }

    /** Falta como chamar ou a data de nascimento: o app pede antes de liberar o resto. */
    public boolean perfilIncompleto() {
        return nickname == null || nickname.isBlank() || birthDate == null;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public PigguRole getRole() {
        return role;
    }

    public void setRole(PigguRole role) {
        this.role = role;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGooglePicture() {
        return googlePicture;
    }

    /** Foto que o app mostra: a escolhida pela pessoa, senao a do Google, senao nenhuma. */
    public String fotoExibida() {
        if (semFoto) {
            return null;
        }
        return fotoPropria != null ? fotoPropria : googlePicture;
    }

    public boolean temFotoPropria() {
        return fotoPropria != null;
    }

    /** @param dataUrl imagem ja preparada por {@link FotoDePerfil#preparar} */
    public void trocarFoto(String dataUrl) {
        this.fotoPropria = dataUrl;
        this.semFoto = false;
    }

    /** Apaga a foto propria e esconde a do Google. */
    public void removerFoto() {
        this.fotoPropria = null;
        this.semFoto = true;
    }

    /** Apaga a foto propria e volta a mostrar a do Google, que segue vindo a cada login. */
    public void usarFotoDoGoogle() {
        this.fotoPropria = null;
        this.semFoto = false;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active && !active) {
            this.deactivatedAt = Instant.now();
        } else if (active) {
            this.deactivatedAt = null;
        }
        this.active = active;
    }

    public Map<String, Object> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions == null ? new HashMap<>() : permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /** Os codigos chegam ja validados como ISO 4217. */
    public void alterarPreferencias(String currency, String conversionCurrency, boolean showExchangeRate,
                                    String timezone) {
        this.currency = currency;
        this.conversionCurrency = conversionCurrency;
        this.showExchangeRate = showExchangeRate;
        if (timezone != null) {
            this.timezone = timezone;
        }
    }

    public List<String> getModulos() {
        return ModulosDeEstiloDeVida.ler(lifestyleModules);
    }

    public void escolherModulos(List<String> modulos) {
        this.lifestyleModules = ModulosDeEstiloDeVida.gravar(modulos);
    }

    public void escolherAvisos(boolean porEmail, boolean porPush) {
        this.alertsByEmail = porEmail;
        this.alertsByPush = porPush;
    }

    public boolean isAlertsByEmail() {
        return alertsByEmail;
    }

    public boolean isAlertsByPush() {
        return alertsByPush;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public String getConversionCurrency() {
        return conversionCurrency;
    }

    public boolean isShowExchangeRate() {
        return showExchangeRate;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    /** Sai de uma familia e entra em outra (convite aceito, membro removido). */
    public void mudarDeFamilia(UUID householdId, PigguRole role) {
        this.householdId = householdId;
        this.role = role;
    }

    /** Aceite dos termos e do aviso de privacidade, com a versao do texto. */
    public void aceitarTermos(String versao) {
        this.termsVersion = versao;
        this.termsAcceptedAt = Instant.now();
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public boolean usouTesteGratis() {
        return trialUsedAt != null;
    }

    public Instant getTrialUsedAt() {
        return trialUsedAt;
    }

    public void marcarTesteGratisUsado() {
        if (trialUsedAt == null) {
            trialUsedAt = Instant.now();
        }
    }

    public void lembrarClienteNoProvedor(String clienteNoProvedor) {
        if (clienteNoProvedor != null) {
            this.stripeCustomerId = clienteNoProvedor;
        }
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public boolean temCpf() {
        return cpfIndice != null;
    }

    /** HMAC do CPF: compara contas sem abrir o CPF. */
    public String getCpfIndice() {
        return cpfIndice;
    }

    public String getPais() {
        return country;
    }

    public void declararPais(String pais) {
        this.country = pais;
    }

    public boolean temDocumento() {
        return idDocIndice != null;
    }

    /**
     * Nada falta de documento: ja informou um (CPF ou o de fora), ou mora num pais onde
     * o Piggu nao pede nenhum. Quem ja informou um documento nao precisa de outro ao mudar.
     */
    public boolean documentoEmDia() {
        if (temCpf() || temDocumento()) {
            return true;
        }
        if (DocumentosDeIdentidade.BRASIL.equals(country)) {
            return false;
        }
        return DocumentosDeIdentidade.pedidoEm(country).isEmpty();
    }

    /** Como o CPF: entra uma vez e fica preso a conta. */
    public void informarDocumento(DocumentosDeIdentidade.Tipo tipo, String cifrado, String indice) {
        if (temDocumento()) {
            throw new IllegalStateException("A conta ja tem documento");
        }
        this.idDocType = tipo.name();
        this.idDocCifrado = cifrado;
        this.idDocIndice = indice;
        this.idDocInformadoEm = Instant.now();
    }

    public String getTipoDoDocumento() {
        return idDocType;
    }

    public String getDocumentoCifrado() {
        return idDocCifrado;
    }

    public Instant getDocumentoInformadoEm() {
        return idDocInformadoEm;
    }

    /** O CPF fica preso a conta: so entra uma vez (trocar e pelo canal de privacidade). */
    public void informarCpf(String cifrado, String indice) {
        if (temCpf()) {
            throw new IllegalStateException("A conta ja tem CPF");
        }
        this.cpfCifrado = cifrado;
        this.cpfIndice = indice;
        this.cpfInformadoEm = Instant.now();
    }

    public String getCpfCifrado() {
        return cpfCifrado;
    }

    public Instant getCpfInformadoEm() {
        return cpfInformadoEm;
    }
}
