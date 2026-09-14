package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Valor pequeno e duravel guardado pelo servico, no formato chave e texto. */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void atualizar(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getValue() {
        return value;
    }
}
