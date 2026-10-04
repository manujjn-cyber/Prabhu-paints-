package in.prabhupaints.vksledger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private String pendingSaveContent;
    private String pendingSaveMime;
    private static final int FILE_REQ = 9001;
    private static final int SAVE_REQ = 9002;

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

        web.addJavascriptInterface(new NativeBridge(), "AndroidApp");
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

    public class NativeBridge {
        @JavascriptInterface public void saveFile(String content, String mime, String fileName) {
            runOnUiThread(() -> {
                pendingSaveContent = content == null ? "" : content;
                pendingSaveMime = (mime == null || mime.isEmpty()) ? "application/json" : mime;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingSaveMime);
                intent.putExtra(Intent.EXTRA_TITLE, fileName == null ? "VKS_Ledger_Backup.json" : fileName);
                try {
                    startActivityForResult(intent, SAVE_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open Save file dialog", Toast.LENGTH_LONG).show();
                }
            });
        }
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
            return;
        }

        if (requestCode == SAVE_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (os == null) throw new Exception("No output stream");
                    os.write((pendingSaveContent == null ? "" : pendingSaveContent).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Toast.makeText(this, "Backup exported successfully", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveContent = null;
            pendingSaveMime = null;
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
