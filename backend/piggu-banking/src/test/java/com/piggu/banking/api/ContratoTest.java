package com.piggu.banking.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do banking em docs/api/banking.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "banking";
    }
}
