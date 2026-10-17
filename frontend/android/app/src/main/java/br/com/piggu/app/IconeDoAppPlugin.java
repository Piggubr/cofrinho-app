package br.com.piggu.app;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Troca o icone do app na tela inicial (Premium). Cada icone e um activity-alias da
 * MainActivity no AndroidManifest (.IconePadrao, .IconeAzul...); liga o escolhido e desliga
 * os outros. O Android nao tem icone escuro, entao aqui so muda a cor.
 *
 * <p>Alguns launchers levam alguns segundos para atualizar o icone. Contrato igual ao do iOS.</p>
 */
@CapacitorPlugin(name = "IconeDoApp")
public class IconeDoAppPlugin extends Plugin {

    private static final String PADRAO = "padrao";
    private static final List<String> ICONES = Arrays.asList(
        PADRAO, "azul", "menta", "lilas", "coral", "mel", "vinho", "grafite", "namorados"
    );

    @PluginMethod
    public void atual(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        JSObject resposta = new JSObject();
        resposta.put("nome", JSObject.NULL);
        for (String id : ICONES) {
            if (!PADRAO.equals(id) && ligado(pm, componente(id))) {
                resposta.put("nome", id);
                break;
            }
        }
        call.resolve(resposta);
    }

    @PluginMethod
    public void trocar(PluginCall call) {
        String nome = call.getString("nome");
        String escolhido = nome == null ? PADRAO : nome;
        if (!ICONES.contains(escolhido)) {
            call.reject("Ícone desconhecido");
            return;
        }
        PackageManager pm = getContext().getPackageManager();
        // Liga o novo antes de desligar os outros: nunca fica um instante sem icone.
        pm.setComponentEnabledSetting(
            componente(escolhido),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        );
        for (String id : ICONES) {
            if (!id.equals(escolhido)) {
                pm.setComponentEnabledSetting(
                    componente(id),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
            }
        }
        call.resolve();
    }

    private ComponentName componente(String id) {
        String classe = "Icone" + Character.toUpperCase(id.charAt(0)) + id.substring(1);
        return new ComponentName(getContext(), IconeDoAppPlugin.class.getPackage().getName() + "." + classe);
    }

    /** O padrao vem ligado no manifesto; os outros, desligados. */
    private boolean ligado(PackageManager pm, ComponentName componente) {
        int estado = pm.getComponentEnabledSetting(componente);
        return estado == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
