package com.dabai.kcb;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 注册本地插件：课表响应捕获 WebView
        registerPlugin(KcbWebviewPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
