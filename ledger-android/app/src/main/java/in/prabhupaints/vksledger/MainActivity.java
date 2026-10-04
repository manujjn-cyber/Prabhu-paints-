package in.prabhupaints.vksledger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private Uri cameraUri;

    private String pendingSaveContent;
    private String pendingSaveMime;
    private byte[] pendingBinary;
    private String pendingImportKind;

    private static final int FILE_REQ = 9001;
    private static final int SAVE_REQ = 9002;
    private static final int IMPORT_REQ = 9003;

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
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;

                Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");
                gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                Intent chooserIntent = Intent.createChooser(gallery, "Add bill / payment pictures");

                try {
                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (dir != null && camera.resolveActivity(getPackageManager()) != null) {
                        File photo = File.createTempFile("vks_bill_", ".jpg", dir);
                        cameraUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                photo
                        );
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    }
                    startActivityForResult(chooserIntent, FILE_REQ);
                    return true;
                } catch (Exception e) {
                    chooser = null;
                    Toast.makeText(MainActivity.this, "Unable to open pictures", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    public class NativeBridge {
        @JavascriptInterface public void saveFile(String content, String mime, String fileName) {
            runOnUiThread(() -> {
                pendingBinary = null;
                pendingSaveContent = content == null ? "" : content;
                pendingSaveMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                launchSaveDialog(pendingSaveMime, fileName == null ? "VKS_Ledger_Export.txt" : fileName);
            });
        }


        @JavascriptInterface public void pickImport(String kind) {
            runOnUiThread(() -> {
                pendingImportKind = kind == null ? "csv" : kind;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                if ("xlsx".equals(pendingImportKind)) {
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                } else if ("json".equals(pendingImportKind)) {
                    intent.setType("application/json");
                } else {
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv","text/tab-separated-values","text/plain","application/csv"});
                }
                try {
                    startActivityForResult(intent, IMPORT_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open import file picker", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void exportPdf(String json, boolean share) {
            new Thread(() -> {
                try {
                    byte[] bytes = buildPdf(json == null ? "{}" : json);
                    String name = reportName("pdf");
                    if (share) shareBinary(bytes, "application/pdf", name);
                    else runOnUiThread(() -> {
                        pendingBinary = bytes;
                        pendingSaveContent = null;
                        pendingSaveMime = "application/pdf";
                        launchSaveDialog("application/pdf", name);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "PDF export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        @JavascriptInterface public void exportJpeg(String json, boolean share) {
            new Thread(() -> {
                try {
                    byte[] bytes = buildJpeg(json == null ? "{}" : json);
                    String name = reportName("jpg");
                    if (share) shareBinary(bytes, "image/jpeg", name);
                    else runOnUiThread(() -> {
                        pendingBinary = bytes;
                        pendingSaveContent = null;
                        pendingSaveMime = "image/jpeg";
                        launchSaveDialog("image/jpeg", name);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "JPEG export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private String reportName(String ext) {
        return "VKS_Professional_Ledger_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + "." + ext;
    }

    private void launchSaveDialog(String mime, String name) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, SAVE_REQ);
        } catch (Exception e) {
            pendingBinary = null;
            pendingSaveContent = null;
            Toast.makeText(this, "Could not open Save file dialog", Toast.LENGTH_LONG).show();
        }
    }

    private File writeShared(byte[] data, String name) throws Exception {
        File dir = new File(getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create share folder");
        File file = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(data);
            out.flush();
        }
        return file;
    }

    private void shareBinary(byte[] data, String mime, String name) {
        try {
            File file = writeShared(data, name);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, name);
                send.setClipData(ClipData.newRawUri(name, uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(send, "Share " + name));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No sharing app available", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
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
        while (s.length() > 1 && p.measureText(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private String num(JSONObject row, String key) {
        if (row == null || row.isNull(key)) return "";
        try { return new DecimalFormat("##,##,##0.##").format(row.getDouble(key)); }
        catch (Exception e) { return ""; }
    }

    private byte[] buildPdf(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.optJSONArray("rows");
        if (rows == null) rows = new JSONArray();

        final int W = 595, H = 842, M = 26;
        PdfDocument doc = new PdfDocument();
        Paint title = paint(18, Color.rgb(23,60,53), true);
        Paint label = paint(8.5f, Color.DKGRAY, false);
        Paint strong = paint(8.5f, Color.rgb(23,60,53), true);
        Paint head = paint(8, Color.WHITE, true);
        Paint cell = paint(7.4f, Color.rgb(35,40,45), false);
        Paint amount = paint(7.4f, Color.rgb(35,40,45), true);
        Paint line = new Paint(); line.setColor(Color.rgb(225,228,231)); line.setStrokeWidth(.7f);

        int pageNo = 0, index = 0;
        while (index < rows.length() || pageNo == 0) {
            pageNo++;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());
            Canvas c = page.getCanvas();
            c.drawColor(Color.WHITE);

            float y = 35;
            c.drawText("VKS PARTY LEDGER", M, y, title);
            y += 15;
            c.drawText("Prabhu Paints • Professional Ledger Statement", M, y, label);
            y += 16;

            c.drawText("Financial Year: " + root.optString("fy","All"), M, y, strong);
            c.drawText("Type: " + root.optString("type","All"), 250, y, strong);
            y += 13;
            c.drawText("Timeline: " + root.optString("timeline","All dates"), M, y, label);
            y += 15;

            c.drawText("Entries: " + root.optInt("count", rows.length()), M, y, label);
            c.drawText("Credit: " + root.optString("credit",""), 120, y, label);
            c.drawText("Debit: " + root.optString("debit",""), 245, y, label);
            c.drawText("Net: " + root.optString("net",""), 365, y, label);
            y += 13;
            c.drawText("Opening Balance: " + root.optString("opening",""), M, y, strong);
            c.drawText("Closing Balance: " + root.optString("closing",""), 300, y, strong);
            y += 18;

            Paint hb = new Paint(); hb.setColor(Color.rgb(23,60,53));
            c.drawRect(M, y-11, W-M, y+4, hb);
            float xDate=M+2, xPart=M+76, xCr=M+310, xDr=M+382, xBal=M+454;
            c.drawText("DATE", xDate, y, head);
            c.drawText("PARTICULAR", xPart, y, head);
            c.drawText("CREDIT", xCr, y, head);
            c.drawText("DEBIT", xDr, y, head);
            c.drawText("BALANCE", xBal, y, head);
            y += 17;

            while (index < rows.length() && y < H-35) {
                JSONObject r = rows.getJSONObject(index++);
                String d = r.optString("date",""); if (d.isEmpty()) d = "No date";
                c.drawText(fit(d, cell, 70), xDate, y, cell);
                c.drawText(fit(r.optString("particular",""), cell, 228), xPart, y, cell);
                c.drawText(fit(num(r,"credit"), amount, 67), xCr, y, amount);
                c.drawText(fit(num(r,"debit"), amount, 67), xDr, y, amount);
                c.drawText(fit(num(r,"balance"), amount, 85), xBal, y, amount);
                c.drawLine(M, y+5, W-M, y+5, line);
                y += 15;
            }

            c.drawText("Page " + pageNo, W-M-34, H-16, label);
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

        final int W = 900, M = 34, headerH = 310, rowH = 38;
        int h = headerH + Math.max(1, rows.length()) * rowH + 60;
        h = Math.min(h, 12000);

        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint brand = new Paint(); brand.setColor(Color.rgb(23,60,53));
        c.drawRect(0,0,W,220,brand);
        Paint title = paint(38,Color.WHITE,true);
        Paint light = paint(20,Color.rgb(224,238,233),false);
        Paint dark = paint(22,Color.rgb(23,60,53),true);

        c.drawText("VKS PARTY LEDGER",M,55,title);
        c.drawText("Prabhu Paints • Professional Ledger Statement",M,90,light);
        c.drawText(root.optString("fy","All")+" • "+root.optString("type","All"),M,126,light);
        c.drawText("Timeline: "+root.optString("timeline","All dates"),M,160,light);
        c.drawText("Credit "+root.optString("credit","")+"  |  Debit "+root.optString("debit","")+"  |  Net "+root.optString("net",""),M,196,light);

        c.drawText("Opening: "+root.optString("opening","")+"    Closing: "+root.optString("closing",""),M,267,dark);

        int y=315;
        Paint hb=new Paint(); hb.setColor(Color.rgb(235,242,239)); c.drawRect(M,y-28,W-M,y+10,hb);
        Paint head=paint(17,Color.rgb(23,60,53),true), cell=paint(16,Color.rgb(35,40,45),false), bold=paint(16,Color.rgb(35,40,45),true);
        int xDate=M, xPart=160, xCr=545, xDr=655, xBal=760;
        c.drawText("DATE",xDate,y,head);c.drawText("PARTICULAR",xPart,y,head);c.drawText("CREDIT",xCr,y,head);c.drawText("DEBIT",xDr,y,head);c.drawText("BAL",xBal,y,head);
        y+=35;

        Paint divider=new Paint();divider.setColor(Color.rgb(226,229,232));divider.setStrokeWidth(1);
        for(int i=0;i<rows.length() && y<h-35;i++){
            JSONObject r=rows.getJSONObject(i);
            String d=r.optString("date","");if(d.isEmpty())d="No date";
            c.drawText(fit(d,cell,118),xDate,y,cell);
            c.drawText(fit(r.optString("particular",""),cell,370),xPart,y,cell);
            c.drawText(fit(num(r,"credit"),bold,100),xCr,y,bold);
            c.drawText(fit(num(r,"debit"),bold,95),xDr,y,bold);
            c.drawText(fit(num(r,"balance"),bold,105),xBal,y,bold);
            c.drawLine(M,y+10,W-M,y+10,divider);
            y+=rowH;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new Exception("JPEG encoding failed");
        bmp.recycle();
        return out.toByteArray();
    }


    private String getDisplayName(Uri uri) {
        String name = "Selected file";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {}
        return name == null ? "Selected file" : name;
    }

    private byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Could not open file");
            return new String(readAllBytes(in), StandardCharsets.UTF_8);
        }
    }

    private JSONObject parseXlsx(Uri uri) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream in = getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(in)) {
            if (in == null) throw new Exception("Could not open Excel file");
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) entries.put(e.getName(), readAllBytes(zip));
                zip.closeEntry();
            }
        }

        if (entries.isEmpty()) throw new Exception("Excel workbook is empty or invalid");

        List<String> shared = new ArrayList<>();
        byte[] sharedXml = entries.get("xl/sharedStrings.xml");
        if (sharedXml != null) {
            Document doc = parseXml(sharedXml);
            NodeList sis = doc.getElementsByTagName("si");
            for (int i = 0; i < sis.getLength(); i++) {
                Element si = (Element) sis.item(i);
                NodeList ts = si.getElementsByTagName("t");
                StringBuilder s = new StringBuilder();
                for (int k = 0; k < ts.getLength(); k++) s.append(ts.item(k).getTextContent());
                shared.add(s.toString());
            }
        }

        List<String> sheetNames = new ArrayList<>();
        byte[] wb = entries.get("xl/workbook.xml");
        if (wb != null) {
            Document doc = parseXml(wb);
            NodeList sheets = doc.getElementsByTagName("sheet");
            for (int i = 0; i < sheets.getLength(); i++) {
                Element el = (Element) sheets.item(i);
                sheetNames.add(el.getAttribute("name"));
            }
        }

        List<String> sheetPaths = new ArrayList<>();
        for (String k : entries.keySet()) {
            if (k.matches("xl/worksheets/sheet\\d+\\.xml")) sheetPaths.add(k);
        }
        Collections.sort(sheetPaths, Comparator.comparingInt(this::sheetNumber));

        JSONArray outSheets = new JSONArray();
        for (int s = 0; s < sheetPaths.size(); s++) {
            byte[] sx = entries.get(sheetPaths.get(s));
            if (sx == null) continue;
            JSONArray rows = parseSheet(sx, shared);
            JSONObject sheet = new JSONObject();
            String name = s < sheetNames.size() && sheetNames.get(s) != null && !sheetNames.get(s).isEmpty()
                    ? sheetNames.get(s) : "Sheet " + (s + 1);
            sheet.put("name", name);
            sheet.put("rows", rows);
            outSheets.put(sheet);
        }

        if (outSheets.length() == 0) throw new Exception("No worksheets found in Excel file");
        JSONObject result = new JSONObject();
        result.put("sheets", outSheets);
        return result;
    }

    private int sheetNumber(String path) {
        try {
            String n = path.replaceAll("^.*sheet", "").replaceAll("\\.xml$", "");
            return Integer.parseInt(n);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private JSONArray parseSheet(byte[] xml, List<String> shared) throws Exception {
        Document doc = parseXml(xml);
        NodeList rowNodes = doc.getElementsByTagName("row");
        JSONArray out = new JSONArray();

        for (int ri = 0; ri < rowNodes.getLength() && ri < 20000; ri++) {
            Element row = (Element) rowNodes.item(ri);
            NodeList cells = row.getElementsByTagName("c");
            Map<Integer,String> values = new HashMap<>();
            int maxCol = -1;

            for (int ci = 0; ci < cells.getLength(); ci++) {
                Element cell = (Element) cells.item(ci);
                String ref = cell.getAttribute("r");
                int col = columnIndex(ref);
                if (col < 0 || col > 80) continue;
                maxCol = Math.max(maxCol, col);

                String type = cell.getAttribute("t");
                String value = "";
                if ("inlineStr".equals(type)) {
                    NodeList ts = cell.getElementsByTagName("t");
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < ts.getLength(); k++) sb.append(ts.item(k).getTextContent());
                    value = sb.toString();
                } else {
                    NodeList vs = cell.getElementsByTagName("v");
                    if (vs.getLength() > 0) value = vs.item(0).getTextContent();
                    if ("s".equals(type) && !value.isEmpty()) {
                        try {
                            int idx = Integer.parseInt(value);
                            if (idx >= 0 && idx < shared.size()) value = shared.get(idx);
                        } catch (Exception ignored) {}
                    } else if ("b".equals(type)) {
                        value = "1".equals(value) ? "TRUE" : "FALSE";
                    }
                }
                values.put(col, value == null ? "" : value);
            }

            if (maxCol < 0) continue;
            JSONArray arr = new JSONArray();
            boolean any = false;
            for (int c = 0; c <= maxCol; c++) {
                String v = values.get(c);
                if (v == null) v = "";
                if (!v.trim().isEmpty()) any = true;
                arr.put(v);
            }
            if (any) out.put(arr);
        }
        return out;
    }

    private int columnIndex(String ref) {
        if (ref == null || ref.isEmpty()) return -1;
        int n = 0, count = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                n = n * 26 + (ch - 'A' + 1);
                count++;
            } else if (ch >= 'a' && ch <= 'z') {
                n = n * 26 + (ch - 'a' + 1);
                count++;
            } else break;
        }
        return count == 0 ? -1 : n - 1;
    }

    private void deliverImport(String kind, String content, String name) {
        String js = "importFromNative(" + JSONObject.quote(kind) + "," +
                JSONObject.quote(content) + "," + JSONObject.quote(name) + ")";
        web.evaluateJavascript(js, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQ && chooser != null) {
            Uri[] out = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getClipData() != null) {
                    ClipData clip = data.getClipData();
                    out = new Uri[clip.getItemCount()];
                    for (int i=0;i<clip.getItemCount();i++) out[i] = clip.getItemAt(i).getUri();
                } else if (data != null && data.getData() != null) {
                    out = new Uri[]{data.getData()};
                } else if (cameraUri != null) {
                    out = new Uri[]{cameraUri};
                }
            }
            chooser.onReceiveValue(out);
            chooser = null;
            cameraUri = null;
            return;
        }


        if (requestCode == IMPORT_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                String kind = pendingImportKind == null ? "csv" : pendingImportKind;
                String name = getDisplayName(uri);
                new Thread(() -> {
                    try {
                        String content;
                        if ("xlsx".equals(kind)) {
                            content = parseXlsx(uri).toString();
                        } else {
                            content = readText(uri);
                        }
                        String finalContent = content;
                        runOnUiThread(() -> deliverImport(kind, finalContent, name));
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
            pendingImportKind = null;
            return;
        }

        if (requestCode == SAVE_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "w")) {
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
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
