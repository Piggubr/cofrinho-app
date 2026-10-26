package com.piggu.identity.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do identity em docs/api/identity.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "identity";
    }
}
