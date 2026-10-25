package com.piggu.rewards.api;

import com.piggu.testing.ContratoDaApiTest;

/** Contrato da API do rewards em docs/api/rewards.yaml (ver {@link ContratoDaApiTest}). */
class ContratoTest extends ContratoDaApiTest {

    @Override
    protected String servico() {
        return "rewards";
    }
}
