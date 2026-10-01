package com.piggu.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Conta de um banco conectado, com o saldo da ultima sincronizacao. */
@Entity
@Table(name = "bank_accounts")
public class BankAccount {

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

    @Column(nullable = false, length = 50)
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
        this.number = number;
        this.balance = balance;
        this.currency = currency;
        this.updatedAt = Instant.now();
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
