package in.prabhupaints.vksledger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private static final int FILE_REQ = 9001;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Intent pick = Intent.createChooser(gallery, "Bill / Payment Picture");
                if (camera.resolveActivity(getPackageManager()) != null) {
                    pick.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                }
                try { startActivityForResult(pick, FILE_REQ); return true; }
                catch (Exception e) { chooser = null; return false; }
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && chooser != null) {
            Uri[] out = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                out = new Uri[]{data.getData()};
            }
            chooser.onReceiveValue(out);
            chooser = null;
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
