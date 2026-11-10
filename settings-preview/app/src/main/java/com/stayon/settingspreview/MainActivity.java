package com.stayon.settingspreview;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
public class MainActivity extends Activity {
 WebView web;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);web=new WebView(this);web.setBackgroundColor(0xff000000);web.setWebViewClient(new WebViewClient());WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);web.setFocusable(true);web.setFocusableInTouchMode(true);setContentView(web);web.loadUrl("file:///android_asset/index.html");web.requestFocus();}
 @Override public boolean onKeyDown(int code, KeyEvent event){if(code==KeyEvent.KEYCODE_BACK){web.evaluateJavascript("window.stayonBack&&window.stayonBack()",null);return true;}return super.onKeyDown(code,event);}
}