package br.com.piggu.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugins do proprio projeto entram antes do super.onCreate, que monta a ponte.
        registerPlugin(AtalhosPlugin.class);
        registerPlugin(IconeDoAppPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
