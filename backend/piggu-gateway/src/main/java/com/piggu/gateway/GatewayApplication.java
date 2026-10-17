package com.piggu.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Porta de entrada unica do Piggu.
 *
 * <p>O navegador conhece um endereco so. O gateway encaminha cada caminho para o
 * servico dono daquele assunto e trata CORS em um lugar unico, em vez de cada
 * servico repetir a mesma configuracao.</p>
 *
 * <p>O gateway nao valida o token: ele repassa o cabecalho Authorization e deixa
 * cada servico decidir. Assim uma chamada interna entre servicos passa pelas mesmas
 * regras de uma chamada vinda de fora.</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
