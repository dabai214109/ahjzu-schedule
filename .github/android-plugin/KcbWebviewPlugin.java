package com.dabai.kcb;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 打开学校研究生系统原生网页：登录（含验证码）都在学校页面完成，
 * 注入 JS 拦截 py_kbcx_ew 的 XHR/fetch 响应，回传给前端解析映射到课表。
 */
@CapacitorPlugin(name = "KcbWebview")
public class KcbWebviewPlugin extends Plugin {

    private Dialog dialog = null;
    private WebView webView = null;
    private TextView statusText = null;
    private boolean captured = false;

    /** 注入到每个页面的响应拦截脚本（XHR + fetch 双覆盖） */
    private static final String HOOK_JS =
        "(function(){try{if(window.__kcbHooked)return;window.__kcbHooked=1;" +
        "function report(u,b){try{window.KcbNative.onScheduleResponse(String(u),String(b));}catch(e){}}" +
        "var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;" +
        "XMLHttpRequest.prototype.open=function(m,u){this.__kcbU=u;return oo.apply(this,arguments);};" +
        "XMLHttpRequest.prototype.send=function(){var x=this;" +
        "x.addEventListener('load',function(){" +
        "try{if(x.__kcbU&&String(x.__kcbU).indexOf('py_kbcx_ew')>=0)report(x.__kcbU,x.responseText);}catch(e){}});" +
        "return os.apply(this,arguments);};" +
        "if(window.fetch){var of=window.fetch;window.fetch=function(i,s){" +
        "var u=(typeof i==='string')?i:((i&&i.url)||'');" +
        "return of.apply(this,arguments).then(function(r){" +
        "try{if(u&&String(u).indexOf('py_kbcx_ew')>=0)r.clone().text().then(function(t){report(u,t);});}catch(e){}" +
        "return r;});};}" +
        "}catch(e){}})();";

    @PluginMethod
    public void open(final PluginCall call) {
        final String url = call.getString("url", "");
        if (url == null || url.isEmpty()) {
            call.reject("missing url");
            return;
        }
        final Context ctx = getActivity();
        if (ctx == null) {
            call.reject("no activity");
            return;
        }
        ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showWebview(ctx, url);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void close(PluginCall call) {
        final Context ctx = getActivity();
        if (ctx != null) {
            ctx.runOnUiThread(new Runnable() {
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

        /* 顶部状态条：提示 + 关闭按钮 */
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 6), dp(ctx, 10));
        bar.setBackgroundColor(Color.parseColor("#3B82F6"));

        statusText = new TextView(ctx);
        statusText.setText("加载中…");
        statusText.setTextColor(0xCCFFFFFF);
        statusText.setTextSize(13);
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
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        String ua = ws.getUserAgentString();
        if (ua != null) ws.setUserAgentString(ua.replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "KcbNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap favicon) {
                if (statusText != null && !captured) statusText.setText("加载中…");
                v.evaluateJavascript(HOOK_JS, null);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                v.evaluateJavascript(HOOK_JS, null);
                if (statusText != null && !captured) {
                    statusText.setText("登录后进入「学生课表查询」→ 自动捕获");
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

    private int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 页面 JS 通过 window.KcbNative.onScheduleResponse(url, body) 回传捕获的响应 */
    private class JsBridge {
        @JavascriptInterface
        public void onScheduleResponse(final String url, final String body) {
            final boolean hasRows = body != null && body.indexOf("\"rows\"") >= 0;
            final Context ctx = getActivity();
            if (ctx != null) {
                ctx.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (statusText != null) {
                            statusText.setText(hasRows ? "✓ 已捕获课表，正在同步…"
                                    : "捕获到响应但无课表数据，请确认已进入「学生课表查询」");
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
