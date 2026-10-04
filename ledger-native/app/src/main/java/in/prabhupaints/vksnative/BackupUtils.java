package in.prabhupaints.vksnative;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import androidx.documentfile.provider.DocumentFile;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public final class BackupUtils {
    private BackupUtils() {}

    public static final String PREFS = "vks_native_prefs";
    public static final String CLOUD_URI = "cloud_tree_uri";
    private static final String AUTO_BACKUP = "VKS_Ledger_Auto_Backup.json";

    public static void ensureSeeded(Context context, LedgerDatabase db) throws Exception {
        if (db.ledgerDao().count() > 0) return;
        String json = readCompressedAsset(context, "seed_transactions.json.gz.b64");
        JSONArray arr = new JSONArray(json);
        List<TransactionEntity> items = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            TransactionEntity t = new TransactionEntity();
            t.sourceId = o.getString("sourceId");
            t.financialYear = o.optString("financialYear", "");
            t.dateText = nullableString(o, "dateText");
            t.particular = o.optString("particular", "");
            t.category = o.optString("category", "");
            t.creditPaise = nullableLong(o, "creditPaise");
            t.debitPaise = nullableLong(o, "debitPaise");
            t.sourceBalancePaise = nullableLong(o, "sourceBalancePaise");
            t.notes = o.optString("notes", "");
            t.sourceSheet = o.optString("sourceSheet", "");
            t.sourceRow = o.optInt("sourceRow", 0);
            t.sourceType = o.optString("sourceType", "XLSX");
            t.createdAt = o.optLong("createdAt", 0);
            t.updatedAt = o.optLong("updatedAt", 0);
            t.rawJson = o.optString("rawJson", "");
            t.photoPath = null;
            items.add(t);
        }
        db.ledgerDao().insertAll(items);
    }

    public static String buildBackup(Context context, LedgerDatabase db) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "VKS Native Ledger");
        root.put("version", 2);
        root.put("generatedAt", System.currentTimeMillis());
        root.put("sourceWorkbook", "vks (1)(1).xlsx");

        JSONArray rows = new JSONArray();
        for (TransactionEntity t : db.ledgerDao().getAll()) {
            JSONObject o = new JSONObject();
            put(o, "sourceId", t.sourceId);
            put(o, "financialYear", t.financialYear);
            put(o, "dateText", t.dateText);
            put(o, "particular", t.particular);
            put(o, "category", t.category);
            put(o, "creditPaise", t.creditPaise);
            put(o, "debitPaise", t.debitPaise);
            put(o, "sourceBalancePaise", t.sourceBalancePaise);
            put(o, "notes", t.notes);
            put(o, "sourceSheet", t.sourceSheet);
            o.put("sourceRow", t.sourceRow);
            put(o, "sourceType", t.sourceType);
            o.put("createdAt", t.createdAt);
            o.put("updatedAt", t.updatedAt);
            put(o, "rawJson", t.rawJson);

            if (t.photoPath != null) {
                File f = new File(t.photoPath);
                if (f.isFile() && f.length() <= 12L * 1024L * 1024L) {
                    o.put("photoName", f.getName());
                    o.put("photoBase64", Base64.encodeToString(readAll(new FileInputStream(f)), Base64.NO_WRAP));
                }
            }
            rows.put(o);
        }
        root.put("transactions", rows);
        return root.toString();
    }

    public static int restoreBackup(Context context, LedgerDatabase db, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("transactions");
        List<TransactionEntity> parsed = new ArrayList<>();
        List<String> photoData = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            TransactionEntity t = new TransactionEntity();
            t.sourceId = o.optString("sourceId", "RESTORE-" + i + "-" + System.nanoTime());
            t.financialYear = o.optString("financialYear", "");
            t.dateText = nullableString(o, "dateText");
            t.particular = o.optString("particular", "");
            t.category = o.optString("category", "");
            t.creditPaise = nullableLong(o, "creditPaise");
            t.debitPaise = nullableLong(o, "debitPaise");
            t.sourceBalancePaise = nullableLong(o, "sourceBalancePaise");
            t.notes = o.optString("notes", "");
            t.sourceSheet = o.optString("sourceSheet", "");
            t.sourceRow = o.optInt("sourceRow", 0);
            t.sourceType = o.optString("sourceType", "USER");
            t.createdAt = o.optLong("createdAt", 0);
            t.updatedAt = o.optLong("updatedAt", 0);
            t.rawJson = o.optString("rawJson", "");
            parsed.add(t);
            photoData.add(o.optString("photoBase64", null));
        }

        File attachDir = new File(context.getFilesDir(), "attachments");
        if (!attachDir.exists()) attachDir.mkdirs();

        db.runInTransaction(() -> {
            db.ledgerDao().clearAll();
            db.ledgerDao().insertAll(parsed);
        });

        List<TransactionEntity> restored = db.ledgerDao().getAll();
        int photoIndex = 0;
        for (int i = 0; i < parsed.size(); i++) {
            String b64 = photoData.get(i);
            if (b64 == null || b64.isEmpty()) continue;
            TransactionEntity match = null;
            String sid = parsed.get(i).sourceId;
            for (TransactionEntity candidate : restored) {
                if (sid.equals(candidate.sourceId)) { match = candidate; break; }
            }
            if (match == null) continue;
            File f = new File(attachDir, "txn_" + match.id + "_" + (++photoIndex) + ".jpg");
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(bytes); }
            match.photoPath = f.getAbsolutePath();
            match.updatedAt = System.currentTimeMillis();
            db.ledgerDao().update(match);
        }
        return parsed.size();
    }

    public static boolean hasCloudFolder(Context context) {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CLOUD_URI, "").isEmpty();
    }

    public static void setCloudFolder(Context context, Uri uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(CLOUD_URI, uri.toString()).apply();
        configurePeriodic(context);
    }

    public static boolean writeCloudBackup(Context context, LedgerDatabase db) {
        try {
            String uriText = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(CLOUD_URI, "");
            if (uriText.isEmpty()) return false;

            DocumentFile dir = DocumentFile.fromTreeUri(context, Uri.parse(uriText));
            if (dir == null || !dir.canWrite()) return false;

            DocumentFile target = dir.findFile(AUTO_BACKUP);
            if (target == null) target = dir.createFile("application/json", AUTO_BACKUP);
            if (target == null) return false;

            String json = buildBackup(context, db);
            try (OutputStream os = context.getContentResolver().openOutputStream(target.getUri(), "wt")) {
                if (os == null) return false;
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String readCloudBackup(Context context) throws Exception {
        String uriText = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CLOUD_URI, "");
        if (uriText.isEmpty()) throw new IllegalStateException("Cloud folder is not connected.");
        DocumentFile dir = DocumentFile.fromTreeUri(context, Uri.parse(uriText));
        if (dir == null) throw new IllegalStateException("Cloud folder is unavailable.");
        DocumentFile target = dir.findFile(AUTO_BACKUP);
        if (target == null) throw new IllegalStateException("No cloud backup found.");
        try (InputStream in = context.getContentResolver().openInputStream(target.getUri())) {
            if (in == null) throw new IllegalStateException("Could not open cloud backup.");
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    public static void scheduleAutoBackup(Context context) {
        if (!hasCloudFolder(context)) return;
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(CloudBackupWorker.class).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "vks-cloud-backup-now",
                ExistingWorkPolicy.REPLACE,
                req
        );
    }

    public static void configurePeriodic(Context context) {
        if (!hasCloudFolder(context)) return;
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                CloudBackupWorker.class, 24, TimeUnit.HOURS
        ).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "vks-cloud-backup-daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                req
        );
    }

    public static String readSourceAudit(Context context) throws Exception {
        return readCompressedAsset(context, "source_audit.json.gz.b64");
    }

    private static String readCompressedAsset(Context context, String name) throws Exception {
        byte[] b64;
        try (InputStream in = context.getAssets().open(name)) {
            b64 = readAll(in);
        }
        byte[] gz = Base64.decode(new String(b64, StandardCharsets.UTF_8).trim(), Base64.DEFAULT);
        try (GZIPInputStream gin = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return new String(readAll(gin), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String nullableString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }

    private static Long nullableLong(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optLong(key);
    }

    private static void put(JSONObject o, String key, Object value) throws Exception {
        if (value == null) o.put(key, JSONObject.NULL);
        else o.put(key, value);
    }
}
