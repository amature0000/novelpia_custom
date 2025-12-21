package com.example.novelpia_custom;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends AppCompatActivity {
    private WebView wvViewer;
    private WebView wvSearch;
    private WebView wvMain;
    private WebView wvBook;
    private WebView wvNovel;
    private ImageButton btnGo;

    private Deque<Byte> backoffstack = new ArrayDeque<>();
    private static final byte MAIN_INDEX = 0b0001;
    private static final byte SEARCH_INDEX = 0b0010;
    private static final byte VIEWER_INDEX = 0b0011;
    private static final byte BOOK_INDEX = 0b0100;
    private static final byte NOVEL_INDEX = 0b0101;
    private byte current = MAIN_INDEX;

    private String searchString = START_URL + SEARCH_SUF;
    private String viewerString = "";
    private String novelString = "";

    private static final String START_URL  = "https://novelpia.com/";
    private static final String SEARCH_SUF = "search";
    private static final String VIEWER_SUF = "viewer";
    private static final String BOOK_SUF = "mybook";
    private static final String NOVEL_SUF = "novel/"; // novelpia 중복

    private static final String KEY_CURRENT = "key_current";
    private static final String KEY_STACK = "key_stack";
    private static final String KEY_MAIN_STATE = "key_wv_main";
    private static final String KEY_SEARCH_STATE = "key_wv_search";
    private static final String KEY_VIEWER_STATE = "key_wv_viewer";
    private static final String KEY_BOOK_STATE = "key_wv_book";
    private static final String KEY_NOVEL_STATE = "key_wv_novel";

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
        btnGo = findViewById(R.id.btnGo);

        setupWebView(wvMain);
        setupWebView(wvViewer);
        setupWebView(wvSearch);
        setupWebView(wvBook);
        setupWebView(wvNovel);

        // 팝업 허용
        WebSettings s = wvMain.getSettings();
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

//        wvMain.setWebChromeClient(new android.webkit.WebChromeClient());

        btnGo.setOnClickListener(v -> showGoDialog());

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
        boolean restored = false;
        if(savedInstanceState != null) {
            restored = restoreAll(savedInstanceState);
        }
        if(!restored) {
            wvMain.loadUrl(START_URL);
            wvBook.loadUrl(START_URL + BOOK_SUF);
            wvSearch.loadUrl(START_URL + SEARCH_SUF);
            swapView(BOOK_INDEX, false);
        }
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
                String url = request.getUrl().toString();
                byte target = classify(url);
                Log.d("stack", backoffstack.size() + "**" + toRead(current) + "->" + toRead(target));
                if (target == current) {
                    String current_url = view.getUrl();
                    // 동일 웹뷰 내에서 이동한 경우
                    if (target != VIEWER_INDEX && current_url != null && !cutUrl(url).equals(cutUrl(current_url))) {
                        backoffstack.push(current);
                    }
                    return false;
                }

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
    private void showGoDialog() {
        EditText et = new EditText(this);
        et.setHint("링크를 입력하세요");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("주소로 이동")
                .setView(et)
                .setPositiveButton("이동", (d, w) -> {
                    String raw = et.getText().toString();
                    String url = getNovelpiaUrl(raw);
                    if (url == null) return;
                    handleUrl(url);
                })
                .setNegativeButton("취소", null)
                .show();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putByte(KEY_CURRENT, current);

        // backoffstack 저장
        int size = backoffstack.size();
        byte[] arr = new byte[size];
        int i = 0;
        for (Byte b : backoffstack) {
            arr[i++] = (byte) (b & 0xFF);
        }
        outState.putByteArray(KEY_STACK, arr);

        // WebView 상태 저장
        Bundle bMain = new Bundle();
        Bundle bSearch = new Bundle();
        Bundle bViewer = new Bundle();
        Bundle bBook = new Bundle();
        Bundle bNovel = new Bundle();

        wvMain.saveState(bMain);
        wvSearch.saveState(bSearch);
        wvViewer.saveState(bViewer);
        wvBook.saveState(bBook);
        wvNovel.saveState(bNovel);

        outState.putBundle(KEY_MAIN_STATE, bMain);
        outState.putBundle(KEY_SEARCH_STATE, bSearch);
        outState.putBundle(KEY_VIEWER_STATE, bViewer);
        outState.putBundle(KEY_BOOK_STATE, bBook);
        outState.putBundle(KEY_NOVEL_STATE, bNovel);
    }
    // 웹뷰 전환
    private void swapView(byte index, boolean isbackoff) { //0b0000
        wvMain.setVisibility(View.GONE);
        wvSearch.setVisibility(View.GONE);
        wvViewer.setVisibility(View.GONE);
        wvBook.setVisibility(View.GONE);
        wvNovel.setVisibility(View.GONE);

        String temp = toRead(index);
        classify(index).setVisibility(View.VISIBLE);
        // viewer 웹뷰가 아니거나 되돌리기 작업이 아닌 경우 스택에 삽입
        if((current != VIEWER_INDEX) && (!isbackoff)) backoffstack.push(current);
        current = index;
        handleToast(temp);
    }
    private void openMain(String url) {
        wvMain.loadUrl(url);

        url = cutUrl(url);
        swapView(MAIN_INDEX, false);
        // 만약 초기화면으로 넘어온 경우 스택 초기화
        if(url.equals(START_URL)) backoffstack.clear();
        Log.d("stack", "openMain " + url);
    }
    private void openViewer(String url) {
        if (!viewerString.equals(url)) wvViewer.loadUrl(url);

        url = cutUrl(url);
        swapView(VIEWER_INDEX, false);
        viewerString = url;
        // novel에서 넘어온 경우 해당 웹뷰의 로딩 화면 제거
        if(!novelString.equals("")) wvNovel.loadUrl(novelString);
        Log.d("stack", "openViewer " + url);
    }
    private void openSearch(String url) {
        if (!searchString.equals(url)) wvSearch.loadUrl(url);

        url = cutUrl(url);
        swapView(SEARCH_INDEX, false);
        searchString = url;
        Log.d("stack", "openSearch " + url);
    }
    private void openBook(String url) {
        wvBook.loadUrl(url);
        swapView(BOOK_INDEX, false);
        Log.d("stack", "openBook " + url);
    }
    private void openNovel(String url) {
        if(!novelString.equals(url)) wvNovel.loadUrl(url);

        url = cutUrl(url);
        swapView(NOVEL_INDEX, false);
        novelString = url;
        Log.d("stack", "openNovel " + url);
    }
    private String getNovelpiaUrl(String raw) {
        String[] tokens = raw.split("\\s+");

        for (String token : tokens) if (token.contains("novelpia.com"))
            return token;
        return null;
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
    private byte classify(String url) {
        if (url.contains(SEARCH_SUF)) return SEARCH_INDEX;
        if (url.contains(VIEWER_SUF)) return VIEWER_INDEX;
        if (url.contains(BOOK_SUF)) return BOOK_INDEX;
        if (url.contains(NOVEL_SUF)) return NOVEL_INDEX;
        return MAIN_INDEX;
    }
    private WebView classify(byte index) {
        if(index == SEARCH_INDEX) return wvSearch;
        if(index == VIEWER_INDEX) return wvViewer;
        if(index == BOOK_INDEX) return wvBook;
        if(index == NOVEL_INDEX) return wvNovel;
        if(index == MAIN_INDEX) return wvMain;
        return null;
    }
    private String toRead(byte index) {
        if(index == SEARCH_INDEX) return "search";
        if(index == VIEWER_INDEX) return "viewer";
        if(index == BOOK_INDEX) return "book";
        if(index == NOVEL_INDEX) return "novel";
        if(index == MAIN_INDEX) return "main";
        return null;
    }
    public void handleBackPressed() {
        // 종료
        if(backoffstack.isEmpty()) {
            finish();
            return;
        }

        byte backoff = backoffstack.pop();
        WebView wv = classify(current);

        if (current == backoff || current == MAIN_INDEX) {
            if (wv.canGoBack()) wv.goBack();
        }
        if (current != backoff) {
            swapView(backoff, true);
        }
    }
    private boolean restoreAll(Bundle state) {
        try {
            // backoffstack 구성
            byte[] arr = state.getByteArray(KEY_STACK);
            backoffstack.clear();
            if (arr != null) {
                for (int i = arr.length - 1; i >= 0; i--) {
                    backoffstack.push((byte) (arr[i] & 0xFF));
                }
            }

            Bundle bMain = state.getBundle(KEY_MAIN_STATE);
            Bundle bSearch = state.getBundle(KEY_SEARCH_STATE);
            Bundle bViewer = state.getBundle(KEY_VIEWER_STATE);
            Bundle bBook = state.getBundle(KEY_BOOK_STATE);
            Bundle bNovel = state.getBundle(KEY_NOVEL_STATE);

            if (bMain != null) wvMain.restoreState(bMain);
            if (bSearch != null) wvSearch.restoreState(bSearch);
            if (bViewer != null) wvViewer.restoreState(bViewer);
            if (bBook != null) wvBook.restoreState(bBook);
            if (bNovel != null) wvNovel.restoreState(bNovel);

            // 현재 화면 띄우기
            current = state.getByte(KEY_CURRENT, MAIN_INDEX);
            swapView(current, true);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        wvMain.onPause();
        wvSearch.onPause();
        wvViewer.onPause();
        wvBook.onPause();
        wvNovel.onPause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        wvMain.onResume();
        wvSearch.onResume();
        wvViewer.onResume();
        wvBook.onResume();
        wvNovel.onResume();
    }
    private String cutUrl(String url) {
        return url.split("\\?")[0];
    }
}