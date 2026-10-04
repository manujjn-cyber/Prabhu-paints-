package in.prabhupaints.vksledger;

import android.app.Activity;
import android.app.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private Uri cameraUri;
    private String pendingSaveContent;
    private String pendingSaveMime;
    private static final int FILE_REQ = 9001;
    private static final int SAVE_REQ = 9002;
    private static final int RESTORE_REQ = 9003;

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
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new NativeBridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;

                String[] accepts = params != null ? params.getAcceptTypes() : new String[0];
                boolean wantsImage = false;
                if (accepts != null) {
                    for (String a : accepts) {
                        if (a != null && a.toLowerCase().contains("image")) wantsImage = true;
                    }
                }

                Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                open.addCategory(Intent.CATEGORY_OPENABLE);
                open.setType(wantsImage ? "image/*" : "*/*");

                if (!wantsImage) {
                    try { startActivityForResult(open, FILE_REQ); return true; }
                    catch (Exception e) { chooser = null; return false; }
                }

                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Intent chooserIntent = Intent.createChooser(open, "Bill / Payment Picture");
                try {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (dir != null) {
                        File photo = File.createTempFile("vks_bill_", ".jpg", dir);
                        cameraUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".fileprovider",
                            photo
                        );
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        if (camera.resolveActivity(getPackageManager()) != null) {
                            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                        }
                    }
                    startActivityForResult(chooserIntent, FILE_REQ);
                    return true;
                } catch (Exception e) {
                    chooser = null;
                    Toast.makeText(MainActivity.this, "Unable to open camera/gallery", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    public class NativeBridge {
        @JavascriptInterface public void saveFile(String content, String mime, String fileName) {
            runOnUiThread(() -> {
                pendingSaveContent = content == null ? "" : content;
                pendingSaveMime = (mime == null || mime.isEmpty()) ? "text/plain" : mime;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingSaveMime);
                intent.putExtra(Intent.EXTRA_TITLE, fileName == null ? "VKS_Ledger_Export.txt" : fileName);
                try {
                    startActivityForResult(intent, SAVE_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Export could not be opened", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void pickBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try {
                    startActivityForResult(intent, RESTORE_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Backup picker could not be opened", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void shareText(String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(send, "Share ledger summary"));
            });
        }

        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (manager != null) {
                    manager.print(
                        "VKS Party Ledger",
                        web.createPrintDocumentAdapter("VKS Party Ledger"),
                        null
                    );
                }
            });
        }

        @JavascriptInterface public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQ && chooser != null) {
            Uri[] out = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
                else if (cameraUri != null) out = new Uri[]{cameraUri};
            }
            chooser.onReceiveValue(out);
            chooser = null;
            cameraUri = null;
            return;
        }

        if (requestCode == SAVE_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                    if (os == null) throw new Exception("No output stream");
                    os.write(pendingSaveContent.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Toast.makeText(this, "Export saved successfully", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveContent = null;
            pendingSaveMime = null;
            return;
        }

        if (requestCode == RESTORE_REQ && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    getContentResolver().openInputStream(data.getData()), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                String js = "restoreBackupFromNative(" + JSONObject.quote(sb.toString()) + ")";
                web.evaluateJavascript(js, null);
            } catch (Exception e) {
                Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
