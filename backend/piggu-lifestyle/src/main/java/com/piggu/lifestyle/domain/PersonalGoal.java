package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Meta pessoal do mes, como "ler 3 livros". So a dona ve e mexe. */
@Entity
@Table(name = "personal_goals")
public class PersonalGoal {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Dia 1 do mes da meta. */
    @Column(nullable = false, updatable = false)
    private LocalDate month;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false)
    private int target;

    @Column(nullable = false)
    private int current;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PersonalGoal() {
    }

    public PersonalGoal(YearMonth mes, String title, int target, int current, UUID userId) {
        this.id = UUID.randomUUID();
        this.month = mes.atDay(1);
        this.userId = userId;
        aplicar(title, target, current);
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void aplicar(String title, int target, int current) {
        this.title = title;
        this.target = target;
        this.current = current;
    }

    public boolean isConcluida() {
        return current >= target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public YearMonth getMes() {
        return YearMonth.from(month);
    }

    public String getTitle() {
        return title;
    }

    public int getTarget() {
        return target;
    }

    public int getCurrent() {
        return current;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
