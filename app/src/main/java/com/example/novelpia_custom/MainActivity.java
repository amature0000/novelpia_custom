package com.example.novelpia_custom;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends AppCompatActivity {
    private WebView wvViewer;
    private WebView wvSearch;
    private WebView wvMain;
    private WebView wvBook;
    private WebView wvNovel;

    private Deque<Character> backoffstack = new ArrayDeque<>();
    private static final char MAIN_INDEX = 0b0001;
    private static final char SEARCH_INDEX = 0b0010;
    private static final char VIEWER_INDEX = 0b0011;
    private static final char BOOK_INDEX = 0b0100;
    private static final char NOVEL_INDEX = 0b0101;
    private char current = MAIN_INDEX;

    private String mainString = START_URL;
    private String searchString = START_URL + SEARCH_SUF;
    private String viewerString = "";
    private String bookString = START_URL + BOOK_SUF;
    private String novelString = "";

    private static final String START_URL  = "https://novelpia.com/";
    private static final String SEARCH_SUF = "search";
    private static final String VIEWER_SUF = "viewer";
    private static final String BOOK_SUF = "mybook";
    private static final String NOVEL_SUF = "novel";

    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        setContentView(R.layout.activity_main);

        wvMain = findViewById(R.id.wvMain);
        wvViewer = findViewById(R.id.wvReader);
        wvSearch = findViewById(R.id.wvSearch);
        wvBook = findViewById(R.id.wvBook);
        wvNovel = findViewById(R.id.wvNovel);

        setupWebView(wvMain);
        setupWebView(wvViewer);
        setupWebView(wvSearch);
        setupWebView(wvBook);
        setupWebView(wvNovel);

        // 팝업 허용
        WebSettings s = wvMain.getSettings();
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        wvMain.setWebChromeClient(new android.webkit.WebChromeClient());
        // 뒤로가기 콜백 등록
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
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
                cm.setPrimaryClip(ClipData.newPlainText("url", url));

                handleToast("링크 복사됨");
                return true;
            }
        });
        wvNovel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View w) {
                if (wvNovel.getVisibility() != View.VISIBLE) return false;

                String url = wvNovel.getUrl();
                if(url == null || url.isEmpty()) return false;

                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));

                handleToast("링크 복사됨");
                return true;
            }
        });

        // 초기 로드
        wvMain.loadUrl(START_URL);
        wvBook.loadUrl(START_URL + BOOK_SUF);
        wvSearch.loadUrl(START_URL + SEARCH_SUF);
        // main 웹뷰 스택에 넣기
        swapView(BOOK_INDEX, false);
        // search 로딩만(스택에 넣지 않음)
//        swapView(BOOK_INDEX, true);
    }
    private void setupWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 링크 이동 블락
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                handleUrl(request.getUrl().toString());
                return true;
            }
        });
        // 얼럿창 처리
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("알림")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("확인")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setNegativeButton("Cancel", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });
    }
    // 웹뷰 전환
    private void swapView(char index, boolean isbackoff) { //0b0000
        wvMain.setVisibility(View.GONE);
        wvSearch.setVisibility(View.GONE);
        wvViewer.setVisibility(View.GONE);
        wvBook.setVisibility(View.GONE);
        wvNovel.setVisibility(View.GONE);

        String temp = "";
        char topush = current;

        if(index == MAIN_INDEX) {
            wvMain.setVisibility(View.VISIBLE);
            temp = "main";
        }
        else if(index == SEARCH_INDEX) {
            wvSearch.setVisibility(View.VISIBLE);
            temp = "search";
        }
        else if(index == VIEWER_INDEX) {
            wvViewer.setVisibility(View.VISIBLE);
            temp = "view";
        }
        else if(index == BOOK_INDEX) {
            wvBook.setVisibility(View.VISIBLE);
            temp = "mybook";
        }
        else if(index == NOVEL_INDEX) {
            wvNovel.setVisibility(View.VISIBLE);
            temp = "novel";
        }
        // 만약 초기화면으로 넘어온 경우 스택 초기화(openMain에 구현됨)
        // viewer 웹뷰가 아니거나(계층구조 설정) 되돌리기 작업이 아닌 경우 스택에 삽입
        if((current != VIEWER_INDEX) && (!isbackoff)) backoffstack.push(topush);

        current = index;
        handleToast(temp);
    }
    private void openMain(String url) {
        wvMain.loadUrl(url);
        url = url.split("\\?")[0];

        // 만약 이전 링크와 동일한 경우 삽입취소(메인코드의 경우 동일 링크여도 새로고침이 됨)
        boolean dobackoff = false;
        if(current == MAIN_INDEX && mainString.equals(url)) dobackoff = true;
        swapView(MAIN_INDEX, dobackoff);
        // 만약 초기화면으로 넘어온 경우 스택 초기화
        if(url.equals(START_URL)) backoffstack.clear();
        mainString = url;
    }
    private void openViewer(String url) {
        if (!viewerString.equals(url)) wvViewer.loadUrl(url);
        url = url.split("\\?")[0];
        swapView(VIEWER_INDEX, false);
        viewerString = url;
        // novel에서 넘어온 경우 해당 웹뷰의 로딩 화면 제거
        if(!novelString.equals("")) wvNovel.loadUrl(novelString);
    }
    private void openSearch(String url) {
        if (!searchString.equals(url)) wvSearch.loadUrl(url);
        url = url.split("\\?")[0];
        swapView(SEARCH_INDEX, false);
        searchString = url;
    }
    private void openBook(String url) {
        wvBook.loadUrl(url);
        url = url.split("\\?")[0];

        // 만약 이전 링크와 동일한 경우 삽입취소(메인코드의 경우 동일 링크여도 새로고침이 됨)
        boolean dobackoff = false;
        if(current == BOOK_INDEX && bookString.equals(url)) dobackoff = true;
        swapView(BOOK_INDEX, dobackoff);
        bookString = url;
    }
    private void openNovel(String url) {
        if(!novelString.equals(url)) wvNovel.loadUrl(url);
        url = url.split("\\?")[0];
        swapView(NOVEL_INDEX, false);
        novelString = url;
    }
    private void handleToast(String msg) {
        Toast myToast = Toast.makeText(this.getApplicationContext(),msg, Toast.LENGTH_SHORT);
        myToast.show();
        toastHandler.postDelayed(myToast::cancel, 500);
    }
    private void handleUrl(String url) {
        if (url.contains(SEARCH_SUF)) openSearch(url);
        else if (url.contains(VIEWER_SUF)) openViewer(url);
        else if (url.contains(BOOK_SUF)) openBook(url);
        else if (url.contains(NOVEL_SUF)) openNovel(url);
        else openMain(url);
    }
    public void handleBackPressed() {
        // 종료
        if(backoffstack.isEmpty()) {
            finish();
            return;
        }

        char backoff = backoffstack.pop();

        // Main 화면이 열려 있을 때 처리
        if (current == MAIN_INDEX) {
            // 우선 뒤로가기 실행
            if (wvMain.canGoBack()) wvMain.goBack();
            // 다른 웹뷰인 경우
            if (backoff != MAIN_INDEX) swapView(backoff, true);
            return;
        }
        // Search 화면이 열려 있을 때 처리
        if (current == SEARCH_INDEX) {
            if (backoff == SEARCH_INDEX && wvSearch.canGoBack()) wvSearch.goBack();
            // 다른 웹뷰인 경우
            else swapView(backoff, true);
            return;
        }
        // 내서재 화면이 열려 있을 때 처리
        if (current == BOOK_INDEX) {
            if (backoff == BOOK_INDEX && wvBook.canGoBack()) wvBook.goBack();
            // 다른 웹뷰인 경우
            else swapView(backoff, true);
            return;
        }
        if (current == NOVEL_INDEX) {
            if (backoff == NOVEL_INDEX && wvNovel.canGoBack()) wvNovel.goBack();
            // 다른 웹뷰인 경우
            else swapView(backoff, true);
            return;
        }
        // Viewer 화면이 열려 있을 때 처리 - 무조건 다른 웹뷰로 이동됨
        if (current == VIEWER_INDEX) swapView(backoff, true);
    }
}