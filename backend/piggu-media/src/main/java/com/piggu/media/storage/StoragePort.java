package com.piggu.media.storage;

import java.util.Arrays;
import java.util.Objects;

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
     * resultado desejado de qualquer forma. Qualquer outra falha lanca
     * {@link com.piggu.common.error.UpstreamException}, para quem chama nao apagar o registro
     * que aponta para o arquivo.</p>
     */
    void apagar(String idNoProvedor);

    /**
     * @param contentType tipo do conteudo lido do provedor
     * @param conteudo    bytes do arquivo
     */
    record ArquivoGuardado(String contentType, byte[] conteudo) {

        @Override
        public boolean equals(Object outro) {
            return outro instanceof ArquivoGuardado o
                    && Objects.equals(contentType, o.contentType)
                    && Arrays.equals(conteudo, o.conteudo);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(contentType) + Arrays.hashCode(conteudo);
        }

        /** Sem os bytes: so o tipo e o tamanho. */
        @Override
        public String toString() {
            return "ArquivoGuardado[contentType=" + contentType + ", bytes="
                    + (conteudo == null ? 0 : conteudo.length) + "]";
        }
    }
}
