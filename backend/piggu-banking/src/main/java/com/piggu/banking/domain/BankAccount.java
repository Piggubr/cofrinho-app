package com.piggu.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Conta de um banco conectado, com o saldo da ultima sincronizacao. */
@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private BankConnection connection;

    @Column(name = "pluggy_account_id", nullable = false, unique = true, length = 100)
    private String pluggyAccountId;

    @Column(nullable = false, length = 200)
    private String name = "";

    @Column(nullable = false, length = 50)
    private String type = "";

    /** So os 4 ultimos caracteres (S7): o numero inteiro nao serve para nada no app. */
    @Column(nullable = false, length = 4)
    private String number = "";

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankAccount() {
    }

    public BankAccount(BankConnection connection, String pluggyAccountId) {
        this.id = UUID.randomUUID();
        this.connection = connection;
        this.pluggyAccountId = pluggyAccountId;
    }

    public void atualizar(String name, String type, String number, BigDecimal balance, String currency) {
        this.name = name;
        this.type = type;
        this.number = ultimosQuatro(number);
        this.balance = balance;
        this.currency = currency;
        this.updatedAt = Instant.now();
    }

    /** "12345-6" vira "3456"; o que nao e letra ou numero (traco, ponto, espaco) sai antes. */
    static String ultimosQuatro(String numero) {
        if (numero == null) {
            return "";
        }
        String limpo = numero.replaceAll("[^0-9A-Za-z]", "");
        return limpo.length() <= 4 ? limpo : limpo.substring(limpo.length() - 4);
    }

    public UUID getId() {
        return id;
    }

    public BankConnection getConnection() {
        return connection;
    }

    public String getPluggyAccountId() {
        return pluggyAccountId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
