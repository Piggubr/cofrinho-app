package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Historico de preco de um produto — a aba Produtos.
 *
 * <p>Alimentada automaticamente toda vez que um gasto e salvo. Guarda ultimo preco,
 * minimo, maximo, media movel, quantidade de compras e a variacao da ultima compra,
 * reproduzindo a funcao atualizarMemoriaProdutos_ do Apps Script.</p>
 */
@Entity
@Table(name = "product_memory")
public class ProductMemory {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id = UUID.randomUUID();

    /** Nome normalizado; unico dentro da familia. */
    @Column(name = "product_key", nullable = false, length = 200)
    private String productKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String category = "Outros";

    @Column(name = "last_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastPrice = BigDecimal.ZERO;

    @Column(name = "min_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal minPrice = BigDecimal.ZERO;

    @Column(name = "max_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxPrice = BigDecimal.ZERO;

    @Column(name = "avg_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal avgPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private int purchases;

    @Column(name = "last_purchase")
    private LocalDate lastPurchase;

    @Column(name = "last_variation", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastVariation = BigDecimal.ZERO;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductMemory() {
    }

    public ProductMemory(String productKey, String name, String category,
                         BigDecimal price, LocalDate purchaseDate, UUID userId) {
        this.productKey = productKey;
        this.name = name;
        this.category = category;
        this.lastPrice = price;
        this.minPrice = price;
        this.maxPrice = price;
        this.avgPrice = price;
        this.purchases = 1;
        this.lastPurchase = purchaseDate;
        this.lastVariation = BigDecimal.ZERO;
        this.userId = userId;
    }

    /**
     * Registra mais uma compra deste produto.
     *
     * <p>A media e recalculada de forma incremental, sem reler todo o historico:
     * media_nova = (media_antiga * (n - 1) + preco) / n.</p>
     */
    public void registrarCompra(String nome, String categoria, BigDecimal preco,
                                LocalDate dataCompra, UUID pessoa) {
        BigDecimal anterior = this.lastPrice;
        this.purchases = this.purchases + 1;
        this.avgPrice = this.avgPrice
                .multiply(BigDecimal.valueOf(this.purchases - 1L))
                .add(preco)
                .divide(BigDecimal.valueOf(this.purchases), 2, RoundingMode.HALF_UP);
        this.minPrice = this.minPrice.min(preco);
        this.maxPrice = this.maxPrice.max(preco);
        this.lastVariation = preco.subtract(anterior);
        this.lastPrice = preco;
        this.name = nome;
        this.category = categoria;
        this.lastPurchase = dataCompra;
        this.userId = pessoa;
        this.updatedAt = Instant.now();
    }

    public String getProductKey() {
        return productKey;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public int getPurchases() {
        return purchases;
    }

    public LocalDate getLastPurchase() {
        return lastPurchase;
    }

    public BigDecimal getLastVariation() {
        return lastVariation;
    }
}
