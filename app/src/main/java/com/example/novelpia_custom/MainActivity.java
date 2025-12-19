package com.example.novelpia_custom;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private WebView wvViewer;
    private WebView wvSearch;
    private WebView wvMain;

    private boolean searchReady = false;
    private String mainUrl = null;
    private String searchUrl = null;
    private String viewerUrl = null;

    private static final String START_URL  = "https://novelpia.com/";
    private static final String SEARCH_URL = START_URL + "search";
    private static final String VIEWER_URL = START_URL + "viewer";
    public static void clearWebViewData(WebView wv) {
        // WebView 캐시
        wv.clearCache(true);
        wv.clearHistory();
        wv.clearFormData(); // 폼 자동완성/입력 데이터

        // DOM Storage
        WebStorage.getInstance().deleteAllData();

        // 쿠키
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.removeAllCookies(value -> { /* no-op */ });
            cm.flush();
        } else {
            cm.removeAllCookie();
        }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.flush();

        setContentView(R.layout.activity_main);

        wvMain = findViewById(R.id.wvMain);
        wvViewer = findViewById(R.id.wvReader);
        wvSearch = findViewById(R.id.wvSearch);

        setupWebView(wvMain);
        setupWebView(wvViewer);
        setupWebView(wvSearch);

        // 팝업 허용
        WebSettings s = wvMain.getSettings();
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        wvMain.setWebChromeClient(new android.webkit.WebChromeClient());

        // 메인 창
        wvMain.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            private boolean handleUrl(String url) {
                if (url.contains("/search")) {
                    openSearch(url);
                    return true;
                }
                if (url.contains("/viewer/")) {
                    openViewer(url);
                    return true;
                }
                // 위치 저장
                mainUrl = url;
                return false;
            }
        });

        // 검색 창
        wvSearch.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("/search")) {
                    searchReady = true;
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            private boolean handleUrl(String url) {
                if (!url.contains("/search")) {
                    // 위치 저장
                    searchUrl = url;
                    return true;
                }
                openSearch(url);
                return false; // search 로딩만 통과
            }
        });
        // 읽기 창
        wvViewer.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            private boolean handleUrl(String url) {
                if (!url.contains("/viewer/")) {
                    // 위치 저장
                    viewerUrl = url;
                    return true;
                }
                openViewer(url);
                return false; // viewer 로딩만 통과
            }
        });
        // 현재 링크 복사 기능
        wvViewer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View w) {
                if (wvViewer.getVisibility() != View.VISIBLE) return false;

                String url = wvViewer.getUrl();
                if(url == null || url.isEmpty()) return false;

                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("viewer_url", url));

                Toast myToast = Toast.makeText(MainActivity.this,"링크 복사됨", Toast.LENGTH_SHORT);
                myToast.show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {myToast.cancel();}, 500);
                return true;
            }
        });

        // 초기 로드
        mainUrl = START_URL + "mybook";
        searchUrl = SEARCH_URL;
        wvMain.loadUrl(mainUrl);

        // 검색 페이지 프리로드
        preloadSearch(SEARCH_URL);
    }
    private void swapView(boolean main, boolean search, boolean view) {
        wvMain.setVisibility(View.GONE);
        wvSearch.setVisibility(View.GONE);
        wvViewer.setVisibility(View.GONE);

        String temp = "";
        if(main) {
            wvMain.setVisibility(View.VISIBLE);
            temp = "main";
        }
        else if(search) {
            wvSearch.setVisibility(View.VISIBLE);
            temp = "search";
        }
        else {
            wvViewer.setVisibility(View.VISIBLE);
            temp = "view";
        }

        Toast myToast = Toast.makeText(this.getApplicationContext(),temp, Toast.LENGTH_SHORT);
        myToast.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {myToast.cancel();}, 500);
    }
    private void setupWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    private void preloadSearch(String url) {
        if (searchReady) return;
        wvSearch.loadUrl(url);
    }
    private void openViewer(String viewerUrl) {
        String cur = wvViewer.getUrl();

        if (cur == null || !cur.equals(viewerUrl)) {
            wvViewer.loadUrl(viewerUrl);
        }

        // 뷰어 표시
        swapView(false, false, true);
    }
    private void openSearch(String url) {
        String current = wvSearch.getUrl();

        if (current == null || !current.contains("/search")) {
            wvSearch.loadUrl(url);
        } else if (!current.equals(url)) {
            wvSearch.loadUrl(url);
        }
        
        // 검색창 표시
        swapView(false, true, true);
    }

    @Override
    public void onBackPressed() {
        // Search 화면이 열려 있을 때 처리
        if (wvSearch.getVisibility() == View.VISIBLE) {
            if (wvSearch.canGoBack()) {
                wvSearch.goBack();
            } else {
                // 메인창 표시
                swapView(true, false, false);
            }
            return;
        }

        // Viewer 화면이 열려 있을 때 처리
        if (wvViewer.getVisibility() == View.VISIBLE) {
            swapView(true, false, false);
            return;

        }
        if (wvMain.canGoBack()) {
            wvMain.goBack();
            return;
        }
        super.onBackPressed();
    }
}