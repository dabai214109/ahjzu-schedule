package com.dabai.kcb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Collections;

/**
 * 打开学校信息门户原生网页：登录（含验证码）都在学校页面完成，
 * 用户自己点进「研究生系统 → 培养管理 → 学生课表查询」；
 * 插件在此之前注入 JS 拦截 py_kbcx_ew 的 XHR/fetch 响应，回传前端解析映射到课表。
 */
@CapacitorPlugin(name = "KcbWebview")
public class KcbWebviewPlugin extends Plugin {

    private Dialog dialog = null;
    private WebView webView = null;
    private TextView statusText = null;
    private boolean captured = false;
    private boolean injectedThisLoad = false;

    /**
     * 注入脚本：
     *  1) hookWin() 包装 XHR / fetch，命中 py_kbcx_ew 时把响应回传原生；
     *  2) 同时 hook 同源 iframe（学校系统常用 frameset/iframe 布局）；
     *  3) 把 target=_blank 与 window.open 改成本窗口跳转，避免点了没反应。
     */
    private static final String HOOK_JS =
        "(function(){try{" +
        "function hookWin(w){if(!w)return;try{if(w.__kcbHooked)return;w.__kcbHooked=1;}catch(e){return;}" +
        "function report(u,b){try{var n=null;try{n=w.KcbNative;}catch(e){}" +
        "if(!n){try{n=window.KcbNative;}catch(e){}}n.onScheduleResponse(String(u),String(b));}catch(e){}}" +
        "try{var X=w.XMLHttpRequest;if(X&&X.prototype){var oo=X.prototype.open,os=X.prototype.send;" +
        "X.prototype.open=function(m,u){try{this.__kcbU=u;}catch(e){}return oo.apply(this,arguments);};" +
        "X.prototype.send=function(){var x=this;try{x.addEventListener('load',function(){" +
        "try{if(x.__kcbU&&String(x.__kcbU).indexOf('py_kbcx_ew')>=0)report(x.__kcbU,x.responseText);}catch(e){}});}catch(e){}" +
        "return os.apply(this,arguments);};}}catch(e){}" +
        "try{if(w.fetch){var of=w.fetch;w.fetch=function(i,s){var u=(typeof i==='string')?i:((i&&i.url)||'');" +
        "return of.apply(this,arguments).then(function(r){try{" +
        "if(u&&String(u).indexOf('py_kbcx_ew')>=0)r.clone().text().then(function(t){report(u,t);});" +
        "}catch(e){}return r;});};}}catch(e){}" +
        "}" +
        "hookWin(window);" +
        "try{window.open=function(u){try{if(u)window.location.href=u;}catch(e){}return null;};}catch(e){}" +
        "try{document.addEventListener('click',function(e){var t=e.target;" +
        "while(t&&t.tagName!=='A'){t=t.parentElement;}" +
        "if(t&&t.target&&t.target!=='_self'){t.target='_self';}},true);}catch(e){}" +
        "try{var fs=document.querySelectorAll('iframe');" +
        "for(var i=0;i<fs.length;i++){try{hookWin(fs[i].contentWindow);}catch(e){}}}catch(e){}" +
        "}catch(e){}})();";

    @PluginMethod
    public void open(final PluginCall call) {
        final String url = call.getString("url", "");
        if (url == null || url.isEmpty()) {
            call.reject("missing url");
            return;
        }
        final Activity act = getActivity();
        if (act == null) {
            call.reject("no activity");
            return;
        }
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showWebview(act, url);
            }
        });
        call.resolve();
    }

    /**
     * 用系统浏览器打开外部网页。
     * 插件自带的 open() 是应用内弹窗（用于学校登录页），不适合跳外部站点；
     * 「设置 → 页脚 GitHub 项目」这类外链走这个方法，跳系统浏览器。
     */
    @PluginMethod
    public void openExternal(final PluginCall call) {
        final String url = call.getString("url", "");
        if (url == null || url.isEmpty()) {
            call.reject("missing url");
            return;
        }
        final Activity act = getActivity();
        if (act == null) {
            call.reject("no activity");
            return;
        }
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                    call.resolve();
                } catch (Exception e) {
                    call.reject("cannot open url: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 把已安装 APK 的真实版本号告诉前端。
     * 设置页脚显示的版本号走这里，而不是在网页里写死 ——
     * CI 每次构建都会把 versionName 改成 android-app/VERSION 里的版本号，这样界面永远与实际一致。
     */
    /**
     * 返回 Android 实际状态栏高度，换算为 WebView CSS px。
     * 某些 Android WebView 不会正确提供 env(safe-area-inset-top)，
     * 因此前端用这个值覆盖 --safe-top。
     */
    @PluginMethod
    public void getStatusBarHeight(PluginCall call) {
        try {
            Activity act = getActivity();
            if (act == null) {
                call.reject("no activity");
                return;
            }
            int resId = act.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int px = resId > 0 ? act.getResources().getDimensionPixelSize(resId) : 0;
            float density = act.getResources().getDisplayMetrics().density;
            JSObject ret = new JSObject();
            ret.put("height", density > 0 ? px / density : px);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("cannot read status bar height: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getVersion(PluginCall call) {
        try {
            Activity act = getActivity();
            if (act == null) {
                call.reject("no activity");
                return;
            }
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            JSObject ret = new JSObject();
            ret.put("version", pi.versionName == null ? "" : pi.versionName);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("cannot read version: " + e.getMessage());
        }
    }

    @PluginMethod
    public void close(PluginCall call) {
        final Activity act = getActivity();
        if (act != null) {
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    closeDialog();
                }
            });
        }
        call.resolve();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebview(Context ctx, String url) {
        if (dialog != null && dialog.isShowing()) {
            if (webView != null) webView.loadUrl(url);
            return;
        }
        captured = false;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        /* 顶部状态条：当前域名 + 提示 + 关闭按钮 */
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 6), dp(ctx, 9));
        bar.setBackgroundColor(Color.parseColor("#3B82F6"));

        statusText = new TextView(ctx);
        statusText.setText("加载中…");
        statusText.setTextColor(0xE6FFFFFF);
        statusText.setTextSize(12);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(statusText, sp);

        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(17);
        closeBtn.setPadding(dp(ctx, 12), dp(ctx, 2), dp(ctx, 12), dp(ctx, 2));
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDialog();
            }
        });
        bar.addView(closeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        webView = new WebView(ctx);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        String ua = ws.getUserAgentString();
        if (ua != null) ws.setUserAgentString(ua.replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "KcbNative");

        /* 最强注入：文档开始执行前（覆盖所有 frame/origin），失败则退回下面几种时机 */
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, HOOK_JS, Collections.singleton("*"));
            }
        } catch (Throwable ignored) {
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap favicon) {
                injectedThisLoad = false;
                if (statusText != null && !captured) statusText.setText(hostOf(u) + " · 加载中…");
                v.evaluateJavascript(HOOK_JS, null);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                v.evaluateJavascript(HOOK_JS, null);
                if (statusText != null && !captured) {
                    statusText.setText(hostOf(u) + " · 登录后进「培养管理 → 学生课表查询」");
                }
            }
        });

        /* 兜底注入：页面加载到 10% 时 JS 上下文已就绪，早于多数 ready 回调 */
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int progress) {
                if (progress >= 10 && !injectedThisLoad) {
                    injectedThisLoad = true;
                    v.evaluateJavascript(HOOK_JS, null);
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                destroyWebview();
            }
        });
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        webView.loadUrl(url);
    }

    private void closeDialog() {
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
            dialog = null;
        }
        destroyWebview();
    }

    private void destroyWebview() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        statusText = null;
        dialog = null;
    }

    private String hostOf(String u) {
        try {
            String h = Uri.parse(u).getHost();
            return h != null ? h : "";
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 页面 JS 通过 window.KcbNative.onScheduleResponse(url, body) 回传捕获的响应 */
    private class JsBridge {
        @JavascriptInterface
        public void onScheduleResponse(final String url, final String body) {
            final boolean hasRows = body != null && body.indexOf("\"rows\"") >= 0;
            final Activity act = getActivity();
            if (act != null) {
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (statusText != null) {
                            statusText.setText(hasRows ? "✓ 已捕获课表，正在同步…"
                                    : "捕获到响应但无课表数据，请进「学生课表查询」");
                        }
                        if (hasRows && !captured) {
                            captured = true;
                            if (webView != null) {
                                webView.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        closeDialog();
                                    }
                                }, 700);
                            }
                        }
                    }
                });
            }
            if (hasRows) {
                JSObject ret = new JSObject();
                ret.put("url", url);
                ret.put("body", body);
                notifyListeners("scheduleResponse", ret);
            }
        }
    }
}
