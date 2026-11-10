package com.stayon.settingspreview;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int SAVE_REQUEST = 701;
    private static final int OPEN_REQUEST = 702;
    private WebView web;
    private String pendingExport = "";

    public final class EditorBridge {
        @JavascriptInterface
        public void saveFile(final String filename, final String content, final String mime) {
            runOnUiThread(() -> {
                pendingExport = content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime);
                intent.putExtra(Intent.EXTRA_TITLE, filename);
                try { startActivityForResult(intent, SAVE_REQUEST); }
                catch (Exception e) { notifyEditor("No se pudo abrir el selector de archivos"); }
            });
        }

        @JavascriptInterface
        public void openJson() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(intent, OPEN_REQUEST); }
                catch (Exception e) { notifyEditor("No se pudo abrir el selector de archivos"); }
            });
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xff090b0e);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new EditorBridge(), "STAYON");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void notifyEditor(String message) {
        String js = "window.stayonNativeStatus(" + JSONObject.quote(message) + ")";
        web.evaluateJavascript(js, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == SAVE_REQUEST) {
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    if (stream == null) throw new Exception("No se pudo escribir el archivo");
                    stream.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                }
                pendingExport = "";
                notifyEditor("JSON/archivo guardado correctamente");
            } else if (requestCode == OPEN_REQUEST) {
                byte[] bytes;
                try (InputStream stream = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    if (stream == null) throw new Exception("Archivo inaccesible");
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = stream.read(chunk)) != -1) {
                        buffer.write(chunk, 0, n);
                        if (buffer.size() > 2_000_000) throw new Exception("El JSON supera 2 MB");
                    }
                    bytes = buffer.toByteArray();
                }
                String json = new String(bytes, StandardCharsets.UTF_8);
                web.evaluateJavascript("window.stayonNativeImported(" + JSONObject.quote(json) + ")", null);
            }
        } catch (Exception e) {
            notifyEditor("Error al guardar/importar: " + e.getMessage());
        }
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.stayonBack ? window.stayonBack() : void(0)", null);
    }
}
