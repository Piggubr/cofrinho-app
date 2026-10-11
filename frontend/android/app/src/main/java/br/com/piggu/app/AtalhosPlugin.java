package br.com.piggu.app;

import android.content.Intent;
import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Atalhos que abrem o app numa tela: o "Lançar gasto" do ícone (toque longo) e os links piggu://.
 *
 * <p>Com o app fechado, a rota fica guardada até o Angular pedir (pendente); com ele aberto,
 * vai direto pelo evento "abrir". Mesmo contrato do plugin do iOS.</p>
 */
@CapacitorPlugin(name = "Atalhos")
public class AtalhosPlugin extends Plugin {

    private String guardada;

    @Override
    public void load() {
        guardada = rotaDe(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        String rota = rotaDe(intent);
        if (rota != null) {
            JSObject dados = new JSObject();
            dados.put("rota", rota);
            notifyListeners("abrir", dados, true);
        }
    }

    @PluginMethod
    public void pendente(PluginCall call) {
        JSObject resposta = new JSObject();
        resposta.put("rota", guardada);
        guardada = null;
        call.resolve(resposta);
    }

    /** piggu://gastos?rapido=1 vira /gastos?rapido=1; qualquer outra coisa e ignorada. */
    static String rotaDe(Intent intent) {
        Uri link = intent == null ? null : intent.getData();
        if (link == null || !"piggu".equals(link.getScheme())) {
            return null;
        }
        String host = link.getHost() == null ? "" : link.getHost();
        String caminho = link.getPath() == null ? "" : link.getPath();
        String consulta = link.getQuery();
        return "/" + host + caminho + (consulta == null || consulta.isEmpty() ? "" : "?" + consulta);
    }
}
