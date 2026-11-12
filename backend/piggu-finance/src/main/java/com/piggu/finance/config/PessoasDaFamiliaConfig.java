package com.piggu.finance.config;

import com.piggu.finance.domain.PessoasDaFamilia;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Toda chamada com token marca a pessoa como da familia do token (dividirCom, D-192). */
@Configuration
public class PessoasDaFamiliaConfig implements WebMvcConfigurer {

    private final PessoasDaFamilia pessoas;

    public PessoasDaFamiliaConfig(PessoasDaFamilia pessoas) {
        this.pessoas = pessoas;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(pessoas).addPathPatterns("/api/**");
    }
}
