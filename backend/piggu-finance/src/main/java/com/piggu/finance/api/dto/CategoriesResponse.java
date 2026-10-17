package com.piggu.finance.api.dto;

import java.util.List;

/**
 * @param categorias todas as categorias validas: as oito de fabrica mais as criadas
 */
public record CategoriesResponse(List<String> categorias) {
}
