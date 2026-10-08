package com.yuriblog.app;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

  private static final String HOME = "https://www.132778.xyz/";
  private static final String HOST_WWW = "https://www.132778.xyz";
  private static final String HOST_ROOT = "https://132778.xyz";

  /** 启动层淡出与内容淡入的时长，和网站自身的过渡节奏对齐。 */
  private static final long REVEAL_MS = 340;
  /** onPageFinished 万一不触发（离线、被拦截）时的兜底，避免内容永远不显示。 */
  private static final long REVEAL_FALLBACK_MS = 4000;

  private WebView webView;
  private SwipeRefreshLayout swipe;
  private ProgressBar progress;
  private View splash;
  private boolean revealed = false;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // 从 SplashTheme 切回常规主题，否则 windowBackground 会一直是启动渐变
    setTheme(R.style.AppTheme);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    webView = findViewById(R.id.webview);
    swipe = findViewById(R.id.swipe);
    progress = findViewById(R.id.progress);
    splash = findViewById(R.id.splash);

    applySystemBars(isSystemDark());
    playSplashIntro();
    configureWebView();
    configureSwipeRefresh();
    configureBackHandling();

    if (savedInstanceState == null) {
      webView.loadUrl(HOME);
    }
    webView.postDelayed(this::revealContent, REVEAL_FALLBACK_MS);
  }

  /** 系统当前是否处于深色模式（用于首帧，之后由网页主题接管）。 */
  private boolean isSystemDark() {
    int mask = getResources().getConfiguration().uiMode
      & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
    return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
  }

  /**
   * 状态栏与导航栏跟随内容底色。网页可以自己切换亮/暗，所以颜色和图标明暗
   * 都要在运行时改，不能只靠 values-night 自动适配。
   */
  private void applySystemBars(boolean dark) {
    int color = ContextCompat.getColor(this, dark ? R.color.surface_dark : R.color.surface_light);
    getWindow().setStatusBarColor(color);
    getWindow().setNavigationBarColor(color);

    WindowInsetsControllerCompat controller =
      WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    controller.setAppearanceLightStatusBars(!dark);
    controller.setAppearanceLightNavigationBars(!dark);
  }

  /** 启动层的入场：logo 由小放大并上浮，站名随后跟上。 */
  private void playSplashIntro() {
    ImageView logo = findViewById(R.id.splash_logo);
    TextView name = findViewById(R.id.splash_name);
    DecelerateInterpolator ease = new DecelerateInterpolator(2f);

    logo.setScaleX(0.84f);
    logo.setScaleY(0.84f);
    logo.setTranslationY(14f);
    logo.animate()
      .alpha(1f)
      .scaleX(1f)
      .scaleY(1f)
      .translationY(0f)
      .setDuration(620)
      .setInterpolator(ease)
      .start();

    name.setTranslationY(10f);
    name.animate()
      .alpha(1f)
      .translationY(0f)
      .setStartDelay(200)
      .setDuration(520)
      .setInterpolator(ease)
      .start();
  }

  /**
   * 把网页当前的主题同步给原生。网站用 <html class="dark"> 表示暗色，
   * 监听 class 变化即可，用户点主题按钮时状态栏会一起跟着变。
   * 用标志位防止在 ClientRouter 反复回调时重复安装观察器。
   */
  private static final String THEME_BRIDGE_JS =
    "(function(){try{"
    + "if(window.__yuriThemeBridge)return;window.__yuriThemeBridge=1;"
    + "var push=function(){try{"
    + "YuriNative.syncDark(document.documentElement.classList.contains('dark'));"
    + "}catch(e){}};"
    + "push();"
    + "new MutationObserver(push).observe(document.documentElement,"
    + "{attributes:true,attributeFilter:['class']});"
    + "}catch(e){}})();";

  @SuppressLint("SetJavaScriptEnabled")
  private void configureWebView() {
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);
    s.setSupportZoom(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);

    // 纯原生滚动，关掉边缘辉光与滚动条，滚动时观感更干净
    webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    webView.setVerticalScrollBarEnabled(false);
    webView.setHorizontalScrollBarEnabled(false);
    webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));

    webView.addJavascriptInterface(new ThemeBridge(), "YuriNative");

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (url.startsWith(HOST_WWW) || url.startsWith(HOST_ROOT)) {
          return false;
        }
        openExternally(url);
        return true;
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        swipe.setRefreshing(false);
        view.evaluateJavascript(THEME_BRIDGE_JS, null);
        revealContent();
      }
    });

    // 顶部进度条：跟着真实载入进度走，走到头再淡出
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int newProgress) {
        if (newProgress >= 100) {
          hideProgress();
        } else {
          showProgress(newProgress);
        }
      }
    });
  }

  /** 进度条出现并推进；setProgress(…, true) 让跳变变成连贯的推进。 */
  private void showProgress(int value) {
    if (progress.getAlpha() == 0f) {
      progress.animate().cancel();
      progress.animate().alpha(1f).setDuration(160).start();
    }
    progress.setProgress(value, true);
  }

  private void hideProgress() {
    progress.animate().cancel();
    progress.animate()
      .alpha(0f)
      .setDuration(280)
      .withEndAction(() -> progress.setProgress(0, false))
      .start();
  }

  private void configureSwipeRefresh() {
    swipe.setColorSchemeResources(R.color.brand, R.color.brand_soft);
    swipe.setProgressBackgroundColorSchemeResource(R.color.surface);
    swipe.setProgressViewOffset(false, 0, (int) (getResources().getDisplayMetrics().density * 24));
    swipe.setOnRefreshListener(() -> webView.reload());
  }

  /** 用现代返回回调，交给系统手势时也能正确回退网页历史。 */
  private void configureBackHandling() {
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (webView.canGoBack()) {
          webView.goBack();
        } else {
          setEnabled(false);
          getOnBackPressedDispatcher().onBackPressed();
        }
      }
    });
  }

  /** 网页 → 原生的主题同步通道。只暴露这一个方法。 */
  private class ThemeBridge {
    @JavascriptInterface
    public void syncDark(boolean dark) {
      runOnUiThread(() -> applySystemBars(dark));
    }
  }

  /** 首屏就绪：内容淡入、启动层淡出。只执行一次。 */
  private void revealContent() {
    if (revealed) {
      return;
    }
    revealed = true;
    DecelerateInterpolator ease = new DecelerateInterpolator();
    webView.animate().alpha(1f).setDuration(REVEAL_MS).setInterpolator(ease).start();
    splash.animate()
      .alpha(0f)
      .setDuration(REVEAL_MS)
      .setInterpolator(ease)
      .withEndAction(() -> splash.setVisibility(View.GONE))
      .start();
  }

  /** 站外链接交给系统浏览器，并带一段淡入转场。 */
  private void openExternally(String url) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      ActivityOptions options = ActivityOptions.makeCustomAnimation(
        this, android.R.anim.fade_in, android.R.anim.fade_out);
      startActivity(intent, options.toBundle());
    } catch (Exception ignored) {
      // 没有可处理该链接的应用，静默忽略
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    webView.saveState(outState);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void finish() {
    super.finish();
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
  }
}
