package com.piggu.common;

import com.piggu.common.error.ApiExceptionHandler;
import com.piggu.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Liga o tratamento de erros e a seguranca padrao assim que o servico declara
 * a dependencia {@code piggu-common} — sem precisar de {@code @Import} em cada aplicacao.
 */
@AutoConfiguration
@Import({ApiExceptionHandler.class, ResourceServerSecurityConfig.class})
public class PigguCommonAutoConfiguration {
}
