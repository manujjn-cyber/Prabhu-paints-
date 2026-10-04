package in.prabhupaints.vksledger;

import android.app.Activity;
import android.print.PrintManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.documentfile.provider.DocumentFile;

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
    private static final int CLOUD_FOLDER_REQ = 9004;

    private static final String PREFS = "vks_native";
    private static final String CLOUD_URI = "cloud_uri";

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

        @JavascriptInterface public void shareTextWhatsApp(String text) {
            runOnUiThread(() -> shareToWhatsAppText(text == null ? "" : text));
        }

        @JavascriptInterface public void shareFile(String content, String mime, String fileName, boolean whatsappOnly) {
            new Thread(() -> {
                try {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create share folder");
                    String safeName = sanitizeFileName(fileName == null ? "VKS_Ledger_Export.txt" : fileName);
                    File file = new File(dir, safeName);
                    try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8, false)) {
                        fw.write(content == null ? "" : content);
                    }
                    Uri uri = FileProvider.getUriForFile(
                        MainActivity.this,
                        getPackageName() + ".fileprovider",
                        file
                    );
                    runOnUiThread(() -> shareUri(uri, mime == null ? "application/octet-stream" : mime, safeName, whatsappOnly));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        @JavascriptInterface public void chooseCloudFolder() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    startActivityForResult(intent, CLOUD_FOLDER_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cloud folder picker could not be opened", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public String cloudFolderStatus() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString(CLOUD_URI, "");
        }

        @JavascriptInterface public void cloudBackup(String content) {
            final String payload = content == null ? "" : content;
            new Thread(() -> {
                boolean ok = writeCloudBackup(payload);
                runOnUiThread(() -> {
                    web.evaluateJavascript("cloudBackupFinished(" + ok + ")", null);
                    Toast.makeText(MainActivity.this,
                            ok ? "Cloud backup updated" : "Cloud backup failed. Re-select the cloud folder.",
                            Toast.LENGTH_SHORT).show();
                });
            }).start();
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

    private String sanitizeFileName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "VKS_Ledger_Export.txt" : safe;
    }

    private void shareUri(Uri uri, String mime, String name, boolean whatsappOnly) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, name);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(name, uri));

        if (whatsappOnly) {
            if (launchPackage(send, "com.whatsapp")) return;
            if (launchPackage(send, "com.whatsapp.w4b")) return;
            Toast.makeText(this, "WhatsApp not found. Opening Android share menu.", Toast.LENGTH_SHORT).show();
        }

        send.setPackage(null);
        try {
            startActivity(Intent.createChooser(send, "Share " + name));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app is available to share this file", Toast.LENGTH_LONG).show();
        }
    }

    private boolean launchPackage(Intent base, String pkg) {
        Intent i = new Intent(base);
        i.setPackage(pkg);
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private void shareToWhatsAppText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        if (launchPackage(send, "com.whatsapp")) return;
        if (launchPackage(send, "com.whatsapp.w4b")) return;
        Toast.makeText(this, "WhatsApp not found. Opening Android share menu.", Toast.LENGTH_SHORT).show();
        send.setPackage(null);
        try {
            startActivity(Intent.createChooser(send, "Share ledger summary"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No sharing app found", Toast.LENGTH_LONG).show();
        }
    }

    private boolean writeCloudBackup(String content) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String uriText = prefs.getString(CLOUD_URI, "");
            if (uriText.isEmpty()) return false;

            Uri treeUri = Uri.parse(uriText);
            DocumentFile dir = DocumentFile.fromTreeUri(this, treeUri);
            if (dir == null || !dir.canWrite()) return false;

            String name = "VKS_Ledger_Auto_Backup.json";
            DocumentFile target = dir.findFile(name);
            if (target == null) target = dir.createFile("application/json", name);
            if (target == null) return false;

            try (OutputStream os = getContentResolver().openOutputStream(target.getUri(), "wt")) {
                if (os == null) return false;
                os.write(content.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
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
                    os.write((pendingSaveContent == null ? "" : pendingSaveContent).getBytes(StandardCharsets.UTF_8));
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
            return;
        }

        if (requestCode == CLOUD_FOLDER_REQ && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CLOUD_URI, uri.toString()).apply();
                web.evaluateJavascript("cloudFolderSelected()", null);
                Toast.makeText(this, "Cloud backup folder connected", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not keep access to this folder", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
