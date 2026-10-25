package com.piggu.media.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do media em docs/api/media.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "media";
    }
}
