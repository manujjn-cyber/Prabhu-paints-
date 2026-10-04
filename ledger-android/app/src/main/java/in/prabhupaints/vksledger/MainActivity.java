package in.prabhupaints.vksledger;

import android.app.Activity;
import android.print.PrintManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
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
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private Uri cameraUri;
    private String pendingSaveContent;
    private String pendingSaveMime;
    private byte[] pendingBinary;

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

        @JavascriptInterface public void exportPdf(String json, boolean whatsappOnly) {
            new Thread(() -> {
                try {
                    byte[] bytes = buildPdf(json == null ? "{}" : json);
                    String name = "VKS_Ledger_" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".pdf";
                    if (whatsappOnly) {
                        File file = writeSharedBinary(bytes, name);
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                        runOnUiThread(() -> shareUri(uri, "application/pdf", name, true));
                    } else {
                        runOnUiThread(() -> launchBinarySave(bytes, "application/pdf", name));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "PDF export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        @JavascriptInterface public void exportJpeg(String json, boolean whatsappOnly) {
            new Thread(() -> {
                try {
                    byte[] bytes = buildJpeg(json == null ? "{}" : json);
                    String name = "VKS_Ledger_" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".jpg";
                    if (whatsappOnly) {
                        File file = writeSharedBinary(bytes, name);
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                        runOnUiThread(() -> shareUri(uri, "image/jpeg", name, true));
                    } else {
                        runOnUiThread(() -> launchBinarySave(bytes, "image/jpeg", name));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "JPEG export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
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

    private void launchBinarySave(byte[] data, String mime, String fileName) {
        pendingBinary = data;
        pendingSaveContent = null;
        pendingSaveMime = mime;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(intent, SAVE_REQ);
        } catch (Exception e) {
            pendingBinary = null;
            Toast.makeText(this, "Save dialog could not be opened", Toast.LENGTH_LONG).show();
        }
    }

    private File writeSharedBinary(byte[] bytes, String fileName) throws Exception {
        File dir = new File(getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create share folder");
        File file = new File(dir, sanitizeFileName(fileName));
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(bytes);
            fos.flush();
        }
        return file;
    }

    private Paint paint(float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        if (bold) p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    private String fit(String value, Paint p, float maxWidth) {
        String s = value == null ? "" : value.replace("\n", " ").replace("\r", " ");
        if (p.measureText(s) <= maxWidth) return s;
        String ell = "…";
        while (s.length() > 1 && p.measureText(s + ell) > maxWidth) s = s.substring(0, s.length() - 1);
        return s + ell;
    }

    private String amount(JSONObject row, String key) {
        if (row == null || row.isNull(key)) return "";
        try {
            double v = row.getDouble(key);
            return new java.text.DecimalFormat("##,##,##0.##").format(v);
        } catch (Exception e) { return ""; }
    }

    private byte[] buildPdf(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.optJSONArray("rows");
        if (rows == null) rows = new JSONArray();

        final int W = 595, H = 842, M = 28;
        final float[] col = {M, M+70, M+292, M+365, M+438, M+505};
        PdfDocument doc = new PdfDocument();
        Paint title = paint(17, Color.rgb(23,60,53), true);
        Paint sub = paint(9, Color.DKGRAY, false);
        Paint head = paint(8, Color.WHITE, true);
        Paint cell = paint(7.6f, Color.rgb(30,35,40), false);
        Paint amount = paint(7.6f, Color.rgb(30,35,40), true);
        Paint line = new Paint(); line.setColor(Color.rgb(220,224,228)); line.setStrokeWidth(0.7f);

        int pageNo = 0, index = 0;
        while (index < rows.length() || pageNo == 0) {
            pageNo++;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());
            Canvas c = page.getCanvas();
            c.drawColor(Color.WHITE);
            float y = 38;
            c.drawText("VKS Party Ledger", M, y, title);
            y += 16;
            String fy = root.optString("fy","All");
            c.drawText("Prabhu Paints  •  " + fy + "  •  " + root.optString("filterLabel","Current filter"), M, y, sub);
            y += 14;
            c.drawText("Transactions: "+root.optInt("count",rows.length())+"   Credit: "+root.optString("credit","")+"   Debit: "+root.optString("debit","")+"   Current balance: "+root.optString("currentBalance",""), M, y, sub);
            y += 18;

            Paint headerBg = new Paint(); headerBg.setColor(Color.rgb(23,60,53));
            c.drawRect(M,y-11,W-M,y+4,headerBg);
            c.drawText("DATE", col[0]+2, y, head);
            c.drawText("PARTICULAR", col[1]+2, y, head);
            c.drawText("CREDIT", col[2]+2, y, head);
            c.drawText("DEBIT", col[3]+2, y, head);
            c.drawText("BALANCE", col[4]+2, y, head);
            y += 17;

            while (index < rows.length() && y < H-38) {
                JSONObject r = rows.getJSONObject(index++);
                String date = r.optString("date","");
                if (date.isEmpty()) date = "No date";
                c.drawText(fit(date,cell,66),col[0]+2,y,cell);
                c.drawText(fit(r.optString("particular",""),cell,216),col[1]+2,y,cell);
                c.drawText(fit(amount(r,"credit"),amount,68),col[2]+2,y,amount);
                c.drawText(fit(amount(r,"debit"),amount,68),col[3]+2,y,amount);
                c.drawText(fit(amount(r,"balance"),amount,62),col[4]+2,y,amount);
                c.drawLine(M,y+5,W-M,y+5,line);
                y += 15;
            }
            c.drawText("Page "+pageNo, W-M-36, H-18, sub);
            doc.finishPage(page);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.writeTo(out);
        doc.close();
        return out.toByteArray();
    }

    private byte[] buildJpeg(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.optJSONArray("rows");
        if (rows == null) rows = new JSONArray();

        final int W = 1080, margin = 42, headerH = 245, rowH = 40;
        int h = headerH + Math.max(1, rows.length()) * rowH + 80;
        h = Math.min(h, 15000);

        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint brand = new Paint(); brand.setColor(Color.rgb(23,60,53));
        c.drawRect(0,0,W,170,brand);
        Paint title = paint(42,Color.WHITE,true);
        Paint sub = paint(22,Color.rgb(220,235,230),false);
        c.drawText("VKS Party Ledger",margin,62,title);
        c.drawText("Prabhu Paints  •  "+root.optString("fy","All"),margin,102,sub);
        c.drawText("Transactions "+root.optInt("count",rows.length())+"  |  Credit "+root.optString("credit","")+"  |  Debit "+root.optString("debit",""),margin,138,sub);

        Paint bal = paint(28,Color.rgb(23,60,53),true);
        c.drawText("Current balance: "+root.optString("currentBalance",""),margin,210,bal);

        int y=250;
        Paint headBg=new Paint();headBg.setColor(Color.rgb(238,242,241));c.drawRect(margin,y-28,W-margin,y+12,headBg);
        Paint head=paint(18,Color.rgb(23,60,53),true), cell=paint(17,Color.rgb(30,35,40),false), num=paint(17,Color.rgb(30,35,40),true);
        int xDate=margin, xPart=195, xCr=640, xDr=775, xBal=900;
        c.drawText("DATE",xDate,y,head);c.drawText("PARTICULAR",xPart,y,head);c.drawText("CREDIT",xCr,y,head);c.drawText("DEBIT",xDr,y,head);c.drawText("BAL",xBal,y,head);
        y+=38;
        Paint divider=new Paint();divider.setColor(Color.rgb(225,228,231));divider.setStrokeWidth(1);

        for(int i=0;i<rows.length() && y<h-45;i++){
            JSONObject r=rows.getJSONObject(i);
            String date=r.optString("date","");if(date.isEmpty())date="No date";
            c.drawText(fit(date,cell,135),xDate,y,cell);
            c.drawText(fit(r.optString("particular",""),cell,420),xPart,y,cell);
            c.drawText(fit(amount(r,"credit"),num,125),xCr,y,num);
            c.drawText(fit(amount(r,"debit"),num,115),xDr,y,num);
            c.drawText(fit(amount(r,"balance"),num,125),xBal,y,num);
            c.drawLine(margin,y+10,W-margin,y+10,divider);
            y+=rowH;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new Exception("Could not encode JPEG");
        bmp.recycle();
        return out.toByteArray();
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
                    if (pendingBinary != null) os.write(pendingBinary);
                    else os.write((pendingSaveContent == null ? "" : pendingSaveContent).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Toast.makeText(this, "Export saved successfully", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingBinary = null;
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
