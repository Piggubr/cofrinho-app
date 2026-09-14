package com.piggu.media.storage;

/**
 * Onde os bytes ficam guardados.
 *
 * <p>Hoje existe uma implementacao so, no Google Drive, porque era onde os arquivos
 * ja estavam. Esta interface e o que permite trocar por S3, MinIO ou disco local sem
 * mexer em nenhuma regra de negocio: o resto do servico conhece apenas um
 * identificador opaco de arquivo.</p>
 */
public interface StoragePort {

    /**
     * Guarda um arquivo.
     *
     * @param nome        nome sugerido para o arquivo
     * @param contentType tipo do conteudo
     * @param conteudo    bytes do arquivo
     * @param pasta       agrupamento logico, como o mes do feed; pode ser nulo
     * @return identificador do arquivo no provedor
     */
    String guardar(String nome, String contentType, byte[] conteudo, String pasta);

    /** Le os bytes de um arquivo guardado. */
    ArquivoGuardado ler(String idNoProvedor);

    /**
     * Apaga um arquivo.
     *
     * <p>Nao lanca excecao se o arquivo ja nao existir: apagar o que ja sumiu e o
     * resultado desejado de qualquer forma.</p>
     */
    void apagar(String idNoProvedor);

    /**
     * @param contentType tipo do conteudo lido do provedor
     * @param conteudo    bytes do arquivo
     */
    record ArquivoGuardado(String contentType, byte[] conteudo) {
    }
}
