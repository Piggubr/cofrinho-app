package com.piggu.media.storage;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.piggu.common.error.UpstreamException;
import com.piggu.media.config.DriveProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Google Drive com o cliente simulado: o que importa e o que o Piggu pede ao Drive
 * (pasta do mes, lixeira em vez de apagar) e como reage a falha.
 */
class DriveStorageAdapterTest {

    private static final String RAIZ = "pasta-raiz";

    private final Drive drive = mock(Drive.class);
    private final Drive.Files arquivos = mock(Drive.Files.class);
    private final Drive.Files.Create criarArquivo = mock(Drive.Files.Create.class);
    private final Drive.Files.Create criarPasta = mock(Drive.Files.Create.class);
    private final Drive.Files.List listar = mock(Drive.Files.List.class);
    private DriveStorageAdapter adaptador;

    @BeforeEach
    void preparar() throws IOException {
        when(drive.files()).thenReturn(arquivos);
        when(arquivos.create(any(File.class), any(AbstractInputStreamContent.class))).thenReturn(criarArquivo);
        when(criarArquivo.setFields("id")).thenReturn(criarArquivo);
        when(criarArquivo.execute()).thenReturn(new File().setId("arquivo-1"));
        when(arquivos.create(any(File.class))).thenReturn(criarPasta);
        when(criarPasta.setFields("id")).thenReturn(criarPasta);
        when(criarPasta.execute()).thenReturn(new File().setId("pasta-nova"));
        when(arquivos.list()).thenReturn(listar);
        when(listar.setQ(anyString())).thenReturn(listar);
        when(listar.setFields(anyString())).thenReturn(listar);
        when(listar.setPageSize(1)).thenReturn(listar);
        when(listar.execute()).thenReturn(new FileList().setFiles(List.of()));
        adaptador = new DriveStorageAdapter(drive, new DriveProperties("file:/x.json", RAIZ, null));
    }

    @Test
    @DisplayName("sem pasta, o arquivo vai para a raiz")
    void semPastaVaiNaRaiz() throws IOException {
        assertThat(adaptador.guardar("a.jpg", "image/jpeg", new byte[] {1}, null)).isEqualTo("arquivo-1");

        ArgumentCaptor<File> metadados = ArgumentCaptor.forClass(File.class);
        verify(arquivos).create(metadados.capture(), any(AbstractInputStreamContent.class));
        assertThat(metadados.getValue().getName()).isEqualTo("a.jpg");
        assertThat(metadados.getValue().getParents()).containsExactly(RAIZ);
        verify(arquivos, never()).list();
    }

    @Test
    @DisplayName("a pasta do mes e criada uma vez e depois vem do cache")
    void pastaDoMesEmCache() throws IOException {
        adaptador.guardar("a.jpg", "image/jpeg", new byte[] {1}, "2026-09 - Setembro");
        adaptador.guardar("b.jpg", "image/jpeg", new byte[] {2}, "2026-09 - Setembro");

        verify(arquivos, times(1)).list();
        ArgumentCaptor<File> pasta = ArgumentCaptor.forClass(File.class);
        verify(arquivos, times(1)).create(pasta.capture());
        assertThat(pasta.getValue().getMimeType()).isEqualTo("application/vnd.google-apps.folder");
        assertThat(pasta.getValue().getParents()).containsExactly(RAIZ);
        ArgumentCaptor<File> metadados = ArgumentCaptor.forClass(File.class);
        verify(arquivos, times(2)).create(metadados.capture(), any(AbstractInputStreamContent.class));
        assertThat(metadados.getAllValues()).allSatisfy(f -> assertThat(f.getParents()).containsExactly("pasta-nova"));
    }

    @Test
    @DisplayName("pasta que ja existe e reaproveitada, e aspas no nome nao quebram a consulta")
    void pastaExistente() throws IOException {
        when(listar.execute()).thenReturn(new FileList().setFiles(List.of(new File().setId("pasta-velha"))));

        adaptador.guardar("a.jpg", "image/jpeg", new byte[] {1}, "d'agua");

        verify(listar).setQ(eq("name = 'dagua' and mimeType = 'application/vnd.google-apps.folder' and '"
                + RAIZ + "' in parents and trashed = false"));
        verify(arquivos, never()).create(any(File.class));
    }

    @Test
    @DisplayName("falha no envio vira erro amigavel")
    void falhaAoGuardar() throws IOException {
        when(criarArquivo.execute()).thenThrow(new IOException("cota"));

        assertThatThrownBy(() -> adaptador.guardar("a.jpg", "image/jpeg", new byte[] {1}, null))
                .isInstanceOf(UpstreamException.class);
    }

    @Test
    @DisplayName("le o tipo e os bytes")
    void le() throws IOException {
        Drive.Files.Get get = mock(Drive.Files.Get.class);
        when(arquivos.get("arquivo-1")).thenReturn(get);
        when(get.setFields("mimeType")).thenReturn(get);
        when(get.execute()).thenReturn(new File().setMimeType("image/png"));
        doAnswer(chamada -> {
            chamada.<OutputStream>getArgument(0).write(new byte[] {7, 8});
            return null;
        }).when(get).executeMediaAndDownloadTo(any(OutputStream.class));

        StoragePort.ArquivoGuardado arquivo = adaptador.ler("arquivo-1");

        assertThat(arquivo.contentType()).isEqualTo("image/png");
        assertThat(arquivo.conteudo()).containsExactly(7, 8);
    }

    @Test
    @DisplayName("apagar manda para a lixeira; arquivo que ja sumiu e ok, falha de verdade avisa quem chamou")
    void apagaParaALixeira() throws IOException {
        Drive.Files.Update update = mock(Drive.Files.Update.class);
        when(arquivos.update(eq("arquivo-1"), any(File.class))).thenReturn(update);

        adaptador.apagar("arquivo-1");

        ArgumentCaptor<File> mudanca = ArgumentCaptor.forClass(File.class);
        verify(arquivos).update(eq("arquivo-1"), mudanca.capture());
        assertThat(mudanca.getValue().getTrashed()).isTrue();
        verify(update).execute();

        when(update.execute()).thenThrow(new HttpResponseException.Builder(404, "Not Found", new HttpHeaders()).build());
        assertThatCode(() -> adaptador.apagar("arquivo-1")).doesNotThrowAnyException();

        doThrow(new IOException("fora do ar")).when(update).execute();
        assertThatThrownBy(() -> adaptador.apagar("arquivo-1")).isInstanceOf(UpstreamException.class);
    }
}
