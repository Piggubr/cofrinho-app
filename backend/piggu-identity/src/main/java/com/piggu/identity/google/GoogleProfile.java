package com.piggu.identity.google;

/**
 * Dados extraidos do ID token do Google, ja validados.
 *
 * @param subject       identificador estavel da conta Google
 * @param email         e-mail normalizado em minusculas
 * @param name          nome completo
 * @param givenName     primeiro nome
 * @param picture       URL da foto de perfil
 */
public record GoogleProfile(String subject, String email, String name, String givenName, String picture) {
}
