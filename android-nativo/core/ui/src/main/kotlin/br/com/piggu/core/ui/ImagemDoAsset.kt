package br.com.piggu.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Uma imagem guardada no piggu-media (`assetId` de Lugar e FotoDoFeed). O carregador do app
 * (PigguApplication) troca por `/api/assets/{id}/content`, pede com a sessão e guarda em cache.
 */
data class AssetDoPiggu(val id: String)

@Composable
fun ImagemDoAsset(
    assetId: String,
    descricao: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(model = AssetDoPiggu(assetId), contentDescription = descricao, modifier = modifier, contentScale = contentScale)
}
