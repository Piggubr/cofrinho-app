package com.piggu.lifestyle.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do lifestyle em docs/api/lifestyle.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "lifestyle";
    }
}
