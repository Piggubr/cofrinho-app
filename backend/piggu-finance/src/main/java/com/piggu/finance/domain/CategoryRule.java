package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** "Sempre que o item ou o estabelecimento tiver o termo, use a categoria." */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    /** Ja normalizado por {@link ChaveProduto}. */
    @Column(nullable = false, length = 100)
    private String term;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CategoryRule() {
    }

    public CategoryRule(String term, String category, String userEmail) {
        this.id = UUID.randomUUID();
        this.term = term;
        this.category = category;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void alterarCategoria(String category, String userEmail) {
        this.category = category;
        this.userEmail = userEmail;
    }

    public UUID getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public String getCategory() {
        return category;
    }
}
