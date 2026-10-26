package com.piggu.finance.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do finance em docs/api/finance.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "finance";
    }
}
