package in.vivekpainter.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int REQ_CREATE_BACKUP = 501;
    private static final int REQ_OPEN_BACKUP = 502;
    private String pendingBackupJson = "{}";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(10, 37, 84));
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {
        @JavascriptInterface public void saveStore(String json) {
            getSharedPreferences("vivek_painter_store", MODE_PRIVATE).edit().putString("data", json).apply();
        }
        @JavascriptInterface public String loadStore() {
            return getSharedPreferences("vivek_painter_store", MODE_PRIVATE).getString("data", "{}");
        }
        @JavascriptInterface public void exportPdf(String fileName) {
            runOnUiThread(() -> exportPdfNative(safeName(fileName), false, "", ""));
        }
        @JavascriptInterface public void exportJpeg(String fileName) {
            runOnUiThread(() -> exportJpegNative(safeName(fileName), false, "", ""));
        }
        @JavascriptInterface public void sharePdf(String fileName, String phone, String message) {
            runOnUiThread(() -> exportPdfNative(safeName(fileName), true, phone, message));
        }
        @JavascriptInterface public void shareJpeg(String fileName, String phone, String message) {
            runOnUiThread(() -> exportJpegNative(safeName(fileName), true, phone, message));
        }
        @JavascriptInterface public void exportBackup(String json) {
            pendingBackupJson = json == null ? "{}" : json;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "Vivek_Painter_Cloud_Backup_" + stamp() + ".json");
                startActivityForResult(i, REQ_CREATE_BACKUP);
            });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                startActivityForResult(i, REQ_OPEN_BACKUP);
            });
        }
        @JavascriptInterface public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private String safeName(String n) {
        if (n == null || n.trim().isEmpty()) return "Vivek_Painter_" + stamp();
        return n.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private Picture picture() { return webView.capturePicture(); }

    private void exportPdfNative(String baseName, boolean share, String phone, String message) {
        try {
            Picture p = picture();
            if (p.getWidth() <= 0 || p.getHeight() <= 0) throw new Exception("Document not ready");
            PdfDocument pdf = new PdfDocument();
            int pageW = 595, pageH = 842, margin = 16;
            float scale = (pageW - margin * 2f) / p.getWidth();
            float sourcePageHeight = (pageH - margin * 2f) / scale;
            int pages = Math.max(1, (int)Math.ceil(p.getHeight() / sourcePageHeight));
            for (int i = 0; i < pages; i++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create();
                PdfDocument.Page page = pdf.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                c.save();
                c.translate(margin, margin);
                c.scale(scale, scale);
                c.translate(0, -i * sourcePageHeight);
                p.draw(c);
                c.restore();
                pdf.finishPage(page);
            }
            if (share) {
                File f = shareFile(baseName + ".pdf");
                try (OutputStream os = new FileOutputStream(f)) { pdf.writeTo(os); }
                pdf.close();
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                shareToWhatsApp(uri, "application/pdf", phone, message);
                done("PDF ready to share");
            } else {
                Uri uri = createDownload(baseName + ".pdf", "application/pdf");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { pdf.writeTo(os); }
                pdf.close();
                done("PDF saved in Downloads/Vivek Painter");
            }
        } catch (Exception e) { done("PDF export failed: " + e.getMessage()); }
    }

    private void exportJpegNative(String baseName, boolean share, String phone, String message) {
        try {
            Picture p = picture();
            if (p.getWidth() <= 0 || p.getHeight() <= 0) throw new Exception("Document not ready");
            int outW = Math.min(1800, Math.max(1080, p.getWidth()));
            float scale = outW / (float)p.getWidth();
            int outH = Math.max(1, Math.round(p.getHeight() * scale));
            if (outH > 12000) {
                float s = 12000f / outH;
                outW = Math.max(720, Math.round(outW * s));
                outH = 12000;
                scale = outW / (float)p.getWidth();
            }
            Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bitmap);
            c.drawColor(Color.WHITE);
            c.scale(scale, scale);
            p.draw(c);
            if (share) {
                File f = shareFile(baseName + ".jpg");
                try (OutputStream os = new FileOutputStream(f)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, os); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                shareToWhatsApp(uri, "image/jpeg", phone, message);
                done("JPEG ready to share");
            } else {
                Uri uri = createDownload(baseName + ".jpg", "image/jpeg");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, os); }
                done("JPEG saved in Downloads/Vivek Painter");
            }
            bitmap.recycle();
        } catch (Exception e) { done("JPEG export failed: " + e.getMessage()); }
    }

    private File shareFile(String name) throws Exception {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create share folder");
        return new File(dir, name);
    }

    private Uri createDownload(String displayName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vivek Painter");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("Cannot create file");
            return u;
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Vivek Painter");
        if (!dir.exists()) dir.mkdirs();
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(dir, displayName));
    }

    private void shareToWhatsApp(Uri uri, String mime, String phone, String message) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, message == null ? "" : message);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setPackage("com.whatsapp");
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 10) digits = "91" + digits;
        if (!digits.isEmpty()) send.putExtra("jid", digits + "@s.whatsapp.net");
        try { startActivity(send); }
        catch (Exception ex) {
            send.setPackage(null);
            startActivity(Intent.createChooser(send, "Share quotation / invoice"));
        }
    }

    private void done(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        webView.postDelayed(() -> webView.evaluateJavascript("window.finishExport&&window.finishExport()", null), 250);
    }

    private String jsQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_CREATE_BACKUP) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Backup saved. You can choose Google Drive/OneDrive in the picker.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_OPEN_BACKUP) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                String json = bos.toString("UTF-8");
                getSharedPreferences("vivek_painter_store", MODE_PRIVATE).edit().putString("data", json).apply();
                webView.evaluateJavascript("window.onBackupImported&&window.onBackupImported(" + jsQuote(json) + ")", null);
                Toast.makeText(this, "Backup restored", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                new AlertDialog.Builder(this).setTitle("Restore failed").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
            }
        }
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("window.handleAndroidBack&&window.handleAndroidBack()", value -> {
            if ("false".equals(value) || "null".equals(value)) MainActivity.super.onBackPressed();
        });
    }
}
