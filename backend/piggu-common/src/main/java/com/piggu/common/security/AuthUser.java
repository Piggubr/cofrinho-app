package com.piggu.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta o {@link CurrentUser} em um metodo de controller.
 *
 * <pre>{@code
 * @GetMapping("/expenses")
 * List<ExpenseResponse> listar(@AuthUser CurrentUser usuario) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
