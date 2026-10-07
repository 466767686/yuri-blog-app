package com.yuriblog.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
  private static final String HOME = "https://www.132778.xyz/";
  private WebView webView;
  private SwipeRefreshLayout swipe;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    webView = findViewById(R.id.webview);
    swipe = findViewById(R.id.swipe);

    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);
    s.setSupportZoom(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith("https://www.132778.xyz") || url.startsWith("https://132778.xyz")) {
          return false;
        }
        try {
          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
        return true;
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        swipe.setRefreshing(false);
      }
    });

    swipe.setOnRefreshListener(() -> webView.reload());
    if (savedInstanceState == null) {
      webView.loadUrl(HOME);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
      webView.goBack();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    webView.saveState(outState);
  }
}
