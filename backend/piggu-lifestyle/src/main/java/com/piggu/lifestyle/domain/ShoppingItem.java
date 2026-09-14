package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de uma lista de compras ou de desejos. Uma linha da aba Compras.
 *
 * <p>Marca, imagem e codigo de barras chegam da busca de produtos e por isso podem
 * estar vazios quando o item foi digitado a mao.</p>
 */
@Entity
@Table(name = "shopping_items")
public class ShoppingItem {

    public static final String LISTA_COMPRAS = "Compras";
    public static final String LISTA_DESEJOS = "Desejos";

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String item;

    @Column(nullable = false, length = 50)
    private String quantity = "";

    @Column(name = "list_name", nullable = false, length = 20)
    private String listName = LISTA_COMPRAS;

    @Column(nullable = false)
    private boolean purchased;

    @Column(nullable = false, length = 100)
    private String brand = "";

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl = "";

    @Column(nullable = false, length = 20)
    private String barcode = "";

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShoppingItem() {
    }

    public ShoppingItem(String item, String quantity, String listName, String brand,
                        String imageUrl, String barcode, String userEmail) {
        this.id = UUID.randomUUID();
        this.item = item;
        this.quantity = quantity;
        this.listName = listName;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.barcode = barcode;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void marcarComprado(boolean comprado) {
        this.purchased = comprado;
    }

    public UUID getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public String getQuantity() {
        return quantity;
    }

    public String getListName() {
        return listName;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public String getBrand() {
        return brand;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
