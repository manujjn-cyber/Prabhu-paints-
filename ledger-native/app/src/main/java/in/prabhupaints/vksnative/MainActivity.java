package in.prabhupaints.vksnative;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PHOTO = 101;
    private static final int REQ_SAVE = 102;
    private static final int REQ_RESTORE = 103;
    private static final int REQ_CLOUD_TREE = 104;

    private static final int BRAND = Color.rgb(23, 60, 53);
    private static final int BRAND2 = Color.rgb(36, 92, 81);
    private static final int GREEN = Color.rgb(8, 127, 91);
    private static final int RED = Color.rgb(201, 42, 42);
    private static final int BG = Color.rgb(245, 246, 247);
    private static final int LINE = Color.rgb(224, 228, 232);
    private static final int MUTED = Color.rgb(100, 110, 120);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LedgerDatabase db;

    private TextView balanceText;
    private TextView statsText;
    private TextView countText;
    private EditText search;
    private Spinner fySpinner;
    private RecyclerView recycler;
    private LedgerAdapter adapter;

    private List<TransactionEntity> allData = new ArrayList<>();
    private List<TransactionEntity> filteredData = new ArrayList<>();
    private final Map<Long, Long> computedBalance = new HashMap<>();
    private final Map<String, Long> currentFyBalance = new HashMap<>();

    private String activeFilter = "ALL";
    private Uri cameraUri;
    private TransactionEntity pendingPhotoTransaction;

    private String pendingSaveText;
    private File pendingSaveFile;
    private String pendingSaveMime;
    private String pendingSaveName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BRAND);
        db = LedgerDatabase.get(this);
        buildUi();

        io.execute(() -> {
            try {
                BackupUtils.ensureSeeded(this, db);
                BackupUtils.configurePeriodic(this);
                loadDataFromDb();
            } catch (Exception e) {
                runOnUiThread(() -> toast("Database setup failed: " + e.getMessage()));
            }
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(14), dp(16), dp(14));
        hero.setBackground(gradient(BRAND, BRAND2, 0));

        LinearLayout heroTop = row();
        TextView title = text("VKS Native Ledger", 21, Color.WHITE, true);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(title);
        titleBox.addView(text("Prabhu Paints • Room / SQLite • Offline", 11, Color.rgb(210, 230, 224), false));
        heroTop.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout balBox = new LinearLayout(this);
        balBox.setGravity(Gravity.END);
        balBox.setOrientation(LinearLayout.VERTICAL);
        TextView bLabel = text("Current balance", 10, Color.rgb(210, 230, 224), false);
        bLabel.setGravity(Gravity.END);
        balanceText = text("₹0", 23, Color.WHITE, true);
        balanceText.setGravity(Gravity.END);
        balBox.addView(bLabel);
        balBox.addView(balanceText);
        heroTop.addView(balBox);
        hero.addView(heroTop);

        statsText = text("Loading verified workbook data…", 12, Color.WHITE, false);
        statsText.setPadding(0, dp(10), 0, 0);
        hero.addView(statsText);

        LinearLayout quick = row();
        quick.setPadding(0, dp(12), 0, 0);
        Button addBill = button("+ Add Bill", Color.WHITE, BRAND);
        Button addPayment = button("− Add Payment", Color.argb(40,255,255,255), Color.WHITE);
        addBill.setOnClickListener(v -> showEntryDialog(null, "BILL"));
        addPayment.setOnClickListener(v -> showEntryDialog(null, "PAYMENT"));
        quick.addView(addBill, weight(1, dp(48), dp(4)));
        quick.addView(addPayment, weight(1, dp(48), dp(4)));
        hero.addView(quick);
        root.addView(hero);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(6));

        LinearLayout searchRow = row();
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search amount, bill, payment, note…");
        search.setTextSize(14);
        search.setPadding(dp(12),0,dp(12),0);
        search.setBackground(round(Color.WHITE, LINE, 12));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));

        fySpinner = new Spinner(this);
        ArrayAdapter<String> fyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"2026-27","2025-26","2024-25","All"});
        fySpinner.setAdapter(fyAdapter);
        LinearLayout.LayoutParams fyLp = new LinearLayout.LayoutParams(dp(112), dp(48));
        fyLp.setMargins(dp(8),0,0,0);
        searchRow.addView(fySpinner, fyLp);
        controls.addView(searchRow);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        chips.setPadding(0, dp(9), 0, dp(4));
        String[][] defs = {{"ALL","All"},{"BILL","Bills"},{"PAYMENT","Payments"},{"RECHARGE","Recharge"},{"PHOTO","With Photo"}};
        for (String[] def : defs) {
            Button b = chip(def[1], def[0].equals("ALL"));
            b.setOnClickListener(v -> {
                activeFilter = def[0];
                for (int i=0;i<chips.getChildCount();i++) {
                    Button c=(Button)chips.getChildAt(i);
                    boolean selected = c.getTag().equals(activeFilter);
                    c.setBackground(round(selected?BRAND:Color.WHITE, selected?BRAND:LINE, 40));
                    c.setTextColor(selected?Color.WHITE:MUTED);
                }
                applyFilters();
            });
            b.setTag(def[0]);
            chips.addView(b);
        }
        hsv.addView(chips);
        controls.addView(hsv);

        LinearLayout listHead = row();
        TextView txTitle = text("Transactions", 16, Color.rgb(30,35,40), true);
        countText = text("", 11, MUTED, false);
        countText.setGravity(Gravity.END);
        listHead.addView(txTitle, new LinearLayout.LayoutParams(0, dp(32), 1));
        listHead.addView(countText, new LinearLayout.LayoutParams(dp(100), dp(32)));
        controls.addView(listHead);
        root.addView(controls);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LedgerAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = row();
        bottom.setPadding(dp(10), dp(8), dp(10), dp(8));
        bottom.setBackgroundColor(Color.WHITE);
        Button bill = button("+ Bill", Color.rgb(232,248,242), GREEN);
        Button pay = button("− Payment", Color.rgb(255,240,240), RED);
        Button export = button("Export / Backup", BRAND, Color.WHITE);
        bill.setOnClickListener(v -> showEntryDialog(null, "BILL"));
        pay.setOnClickListener(v -> showEntryDialog(null, "PAYMENT"));
        export.setOnClickListener(v -> showExportMenu());
        bottom.addView(bill, weight(1, dp(48), dp(3)));
        bottom.addView(pay, weight(1, dp(48), dp(3)));
        bottom.addView(export, weight(1.25f, dp(48), dp(3)));
        root.addView(bottom);

        search.addTextChangedListener(new SimpleWatcher(this::applyFilters));
        fySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { applyFilters(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        setContentView(root);
    }

    private void loadDataFromDb() {
        List<TransactionEntity> rows = db.ledgerDao().getAll();
        runOnUiThread(() -> {
            allData = rows;
            recomputeBalances();
            applyFilters();
        });
    }

    private void recomputeBalances() {
        computedBalance.clear();
        currentFyBalance.clear();

        Map<String, Long> base = new HashMap<>();
        for (TransactionEntity t : allData) {
            if ("XLSX".equals(t.sourceType) && t.sourceBalancePaise != null) {
                computedBalance.put(t.id, t.sourceBalancePaise);
                base.put(t.financialYear, t.sourceBalancePaise);
            }
        }
        currentFyBalance.putAll(base);

        for (TransactionEntity t : allData) {
            if (!"USER".equals(t.sourceType)) continue;
            long bal = currentFyBalance.getOrDefault(t.financialYear, 0L);
            bal += nz(t.creditPaise) - nz(t.debitPaise);
            computedBalance.put(t.id, bal);
            currentFyBalance.put(t.financialYear, bal);
        }
    }

    private void applyFilters() {
        if (allData == null) return;
        String fy = fySpinner == null ? "2026-27" : String.valueOf(fySpinner.getSelectedItem());
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<TransactionEntity> out = new ArrayList<>();

        for (int i = allData.size()-1; i >= 0; i--) {
            TransactionEntity t = allData.get(i);
            if (!"All".equals(fy) && !fy.equals(t.financialYear)) continue;
            if (!matchesFilter(t, activeFilter)) continue;
            String hay = (safe(t.dateText)+" "+safe(t.particular)+" "+safe(t.category)+" "+safe(t.notes)+" "+
                    rupees(nz(t.creditPaise))+" "+rupees(nz(t.debitPaise))).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            out.add(t);
        }
        filteredData = out;
        adapter.setItems(out);

        long cr=0, dr=0;
        for (TransactionEntity t : out) { cr += nz(t.creditPaise); dr += nz(t.debitPaise); }
        long current = currentFyBalance.getOrDefault("All".equals(fy) ? "2026-27" : fy, 0L);
        balanceText.setText(money(current));
        statsText.setText("Credit "+money(cr)+"   •   Debit "+money(dr)+"   •   Filtered net "+money(cr-dr));
        countText.setText(out.size()+" shown");
    }

    private boolean matchesFilter(TransactionEntity t, String filter) {
        if ("ALL".equals(filter)) return true;
        if ("PHOTO".equals(filter)) return t.photoPath != null && new File(t.photoPath).isFile();
        String type = classify(t);
        return filter.equals(type);
    }

    private String classify(TransactionEntity t) {
        String p = (safe(t.particular)+" "+safe(t.category)).toLowerCase(Locale.ROOT);
        if (p.contains("recharge") || p.contains("recharg")) return "RECHARGE";
        if (t.creditPaise != null && t.debitPaise == null) return "BILL";
        if (t.debitPaise != null && t.creditPaise == null) return "PAYMENT";
        if (p.contains("payment") || p.contains("payement")) return "PAYMENT";
        if (p.contains("bill")) return "BILL";
        return "OTHER";
    }

    private void showEntryDialog(TransactionEntity edit, String preset) {
        boolean editing = edit != null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), dp(4));

        Spinner type = fieldSpinner(new String[]{"Bill / Credit","Payment / Debit","Recharge / Debit","Other Credit","Other Debit"});
        Spinner fy = fieldSpinner(new String[]{"2026-27","2025-26","2024-25"});
        EditText date = field("Date YYYY-MM-DD", InputType.TYPE_CLASS_DATETIME);
        date.setFocusable(false);
        date.setOnClickListener(v -> pickDate(date));
        EditText amount = field("Amount ₹", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText particular = field("Particular / description", InputType.TYPE_CLASS_TEXT);
        EditText category = field("Category (optional)", InputType.TYPE_CLASS_TEXT);
        EditText notes = field("Notes (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setMinLines(2);

        addLabel(form,"Type"); form.addView(type);
        addLabel(form,"Financial year"); form.addView(fy);
        addLabel(form,"Date"); form.addView(date);
        addLabel(form,"Amount"); form.addView(amount);
        addLabel(form,"Particular"); form.addView(particular);
        addLabel(form,"Category"); form.addView(category);
        addLabel(form,"Notes"); form.addView(notes);

        if (editing) {
            type.setSelection(typeIndex(edit));
            setSpinnerValue(fy, edit.financialYear);
            date.setText(safe(edit.dateText));
            amount.setText(formatPlain(edit.creditPaise != null ? edit.creditPaise : edit.debitPaise));
            particular.setText(safe(edit.particular));
            category.setText(safe(edit.category));
            notes.setText(safe(edit.notes));
        } else {
            setSpinnerValue(fy, String.valueOf(fySpinner.getSelectedItem()).equals("All") ? "2026-27" : String.valueOf(fySpinner.getSelectedItem()));
            date.setText(java.time.LocalDate.now().toString());
            if ("PAYMENT".equals(preset)) {
                type.setSelection(1); particular.setText("PAYMENT");
            } else {
                type.setSelection(0); particular.setText("BILL");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit transaction" : "Add transaction")
                .setView(new ScrollView(this) {{ addView(form); }})
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long paise = parsePaise(amount.getText().toString());
                if (paise <= 0) throw new IllegalArgumentException("Enter a valid amount.");
                String part = particular.getText().toString().trim();
                if (part.isEmpty()) throw new IllegalArgumentException("Enter a description.");

                int ti = type.getSelectedItemPosition();
                TransactionEntity t = editing ? edit : new TransactionEntity();
                if (!editing) {
                    t.sourceId = "USER-" + UUID.randomUUID();
                    t.sourceType = "USER";
                    t.sourceSheet = "APP";
                    t.sourceRow = 0;
                    t.rawJson = "";
                    t.createdAt = System.currentTimeMillis();
                }
                t.financialYear = String.valueOf(fy.getSelectedItem());
                t.dateText = date.getText().toString().trim().isEmpty() ? null : date.getText().toString().trim();
                t.particular = part;
                t.category = category.getText().toString().trim();
                t.notes = notes.getText().toString().trim();
                t.creditPaise = (ti==0 || ti==3) ? paise : null;
                t.debitPaise = (ti==1 || ti==2 || ti==4) ? paise : null;
                t.sourceBalancePaise = null;
                t.updatedAt = System.currentTimeMillis();

                io.execute(() -> {
                    if (editing) db.ledgerDao().update(t);
                    else db.ledgerDao().insert(t);
                    BackupUtils.scheduleAutoBackup(this);
                    loadDataFromDb();
                });
                dialog.dismiss();
            } catch (Exception e) {
                toast(e.getMessage());
            }
        }));
        dialog.show();
    }

    private void pickDate(EditText target) {
        Calendar c = Calendar.getInstance();
        String s = target.getText().toString();
        try {
            String[] p = s.split("-");
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1])-1, Integer.parseInt(p[2]));
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (DatePicker v, int y, int m, int d) ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m+1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private int typeIndex(TransactionEntity t) {
        String c = classify(t);
        if ("BILL".equals(c)) return 0;
        if ("RECHARGE".equals(c)) return 2;
        if ("PAYMENT".equals(c)) return 1;
        return t.creditPaise != null ? 3 : 4;
    }

    private void showTransactionMenu(TransactionEntity t) {
        List<String> opts = new ArrayList<>();
        opts.add("View details");
        if (t.photoPath != null && new File(t.photoPath).isFile()) opts.add("View attached photo");
        opts.add(t.photoPath == null ? "Attach bill/payment photo" : "Replace photo");
        if (t.photoPath != null) opts.add("Remove photo");
        if ("USER".equals(t.sourceType)) {
            opts.add("Edit transaction");
            opts.add("Delete transaction");
        }

        new AlertDialog.Builder(this)
                .setTitle(safe(t.particular).isEmpty() ? "Transaction" : t.particular)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String choice = opts.get(which);
                    if (choice.startsWith("View details")) showDetails(t);
                    else if (choice.startsWith("View attached")) showPhoto(t);
                    else if (choice.startsWith("Attach") || choice.startsWith("Replace")) choosePhoto(t);
                    else if (choice.startsWith("Remove photo")) removePhoto(t);
                    else if (choice.startsWith("Edit")) showEntryDialog(t, classify(t));
                    else if (choice.startsWith("Delete")) deleteTransaction(t);
                }).show();
    }

    private void showDetails(TransactionEntity t) {
        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(safe(t.dateText).isEmpty() ? "Not entered" : t.dateText).append("\n");
        sb.append("FY: ").append(t.financialYear).append("\n");
        if (t.creditPaise != null) sb.append("Credit: ").append(money(t.creditPaise)).append("\n");
        if (t.debitPaise != null) sb.append("Debit: ").append(money(t.debitPaise)).append("\n");
        Long bal = computedBalance.get(t.id);
        if (bal != null) sb.append("Balance: ").append(money(bal)).append("\n");
        if (!safe(t.category).isEmpty()) sb.append("Category: ").append(t.category).append("\n");
        if (!safe(t.notes).isEmpty()) sb.append("Notes: ").append(t.notes).append("\n");
        sb.append("\nSource: ").append(t.sourceType);
        if ("XLSX".equals(t.sourceType)) sb.append(" • ").append(t.sourceSheet).append(" row ").append(t.sourceRow);
        new AlertDialog.Builder(this).setTitle(t.particular).setMessage(sb.toString()).setPositiveButton("OK",null).show();
    }

    private void choosePhoto(TransactionEntity t) {
        pendingPhotoTransaction = t;
        Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Intent chooser = Intent.createChooser(gallery, "Bill / Payment Picture");
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File temp = File.createTempFile("vks_camera_", ".jpg", dir);
            cameraUri = FileProvider.getUriForFile(this, getPackageName()+".fileprovider", temp);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (camera.resolveActivity(getPackageManager()) != null) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            }
            startActivityForResult(chooser, REQ_PHOTO);
        } catch (Exception e) {
            toast("Unable to open camera/gallery.");
        }
    }

    private void attachPhoto(Uri uri) {
        TransactionEntity t = pendingPhotoTransaction;
        if (t == null || uri == null) return;
        io.execute(() -> {
            try {
                File dir = new File(getFilesDir(), "attachments");
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(dir, "txn_"+t.id+"_"+System.currentTimeMillis()+".jpg");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new Exception("Unable to read picture.");
                    byte[] buf = new byte[8192]; int n;
                    while ((n=in.read(buf))>0) out.write(buf,0,n);
                }
                if (t.photoPath != null) new File(t.photoPath).delete();
                t.photoPath = dest.getAbsolutePath();
                t.updatedAt = System.currentTimeMillis();
                db.ledgerDao().update(t);
                BackupUtils.scheduleAutoBackup(this);
                loadDataFromDb();
                runOnUiThread(() -> toast("Photo attached."));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Photo failed: "+e.getMessage()));
            }
        });
    }

    private void showPhoto(TransactionEntity t) {
        if (t.photoPath == null) return;
        File f = new File(t.photoPath);
        if (!f.isFile()) { toast("Photo file not found."); return; }
        ImageView iv = new ImageView(this);
        iv.setImageURI(Uri.fromFile(f));
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(8),dp(8),dp(8),dp(8));
        new AlertDialog.Builder(this).setTitle("Attached picture").setView(iv).setPositiveButton("Close",null).show();
    }

    private void removePhoto(TransactionEntity t) {
        new AlertDialog.Builder(this).setMessage("Remove the attached photo?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Remove",(d,w)-> io.execute(() -> {
                    if (t.photoPath != null) new File(t.photoPath).delete();
                    t.photoPath = null; t.updatedAt = System.currentTimeMillis();
                    db.ledgerDao().update(t);
                    BackupUtils.scheduleAutoBackup(this);
                    loadDataFromDb();
                })).show();
    }

    private void deleteTransaction(TransactionEntity t) {
        new AlertDialog.Builder(this)
                .setTitle("Delete transaction?")
                .setMessage("Only app-added transactions can be deleted. This action cannot be undone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete",(d,w)-> io.execute(() -> {
                    if (t.photoPath != null) new File(t.photoPath).delete();
                    db.ledgerDao().delete(t);
                    BackupUtils.scheduleAutoBackup(this);
                    loadDataFromDb();
                })).show();
    }

    private void showExportMenu() {
        String cloud = BackupUtils.hasCloudFolder(this) ? "Cloud folder: CONNECTED" : "Cloud folder: NOT CONNECTED";
        String[] opts = {
                "WhatsApp CSV statement",
                "WhatsApp PDF statement",
                "WhatsApp full JSON backup",
                "Share summary to WhatsApp",
                "Save CSV statement",
                "Save PDF statement",
                "Save full JSON backup",
                "Cloud backup now",
                "Choose Google Drive / cloud folder",
                "Restore from cloud backup",
                "Restore from JSON backup file",
                "Export source-audit JSON",
                "Data audit summary",
                cloud
        };
        new AlertDialog.Builder(this).setTitle("Export & Backup")
                .setItems(opts,(d,w)-> handleExport(w)).show();
    }

    private void handleExport(int i) {
        if (i==0) shareCsv(true);
        else if (i==1) sharePdf(true);
        else if (i==2) shareBackup(true);
        else if (i==3) shareSummaryWhatsApp();
        else if (i==4) saveCsv();
        else if (i==5) savePdf();
        else if (i==6) saveBackup();
        else if (i==7) cloudBackupNow();
        else if (i==8) chooseCloudFolder();
        else if (i==9) restoreCloud();
        else if (i==10) pickRestoreFile();
        else if (i==11) exportSourceAudit();
        else if (i==12) showAuditSummary();
    }

    private String csvText() {
        StringBuilder sb = new StringBuilder("\uFEFFDate,FY,Particular,Category,Credit,Debit,Balance,Notes,Source\r\n");
        for (TransactionEntity t : filteredData) {
            sb.append(csv(t.dateText)).append(',').append(csv(t.financialYear)).append(',')
                    .append(csv(t.particular)).append(',').append(csv(t.category)).append(',')
                    .append(csv(t.creditPaise==null?"":formatPlain(t.creditPaise))).append(',')
                    .append(csv(t.debitPaise==null?"":formatPlain(t.debitPaise))).append(',')
                    .append(csv(computedBalance.containsKey(t.id)?formatPlain(computedBalance.get(t.id)):"")).append(',')
                    .append(csv(t.notes)).append(',').append(csv(t.sourceType)).append("\r\n");
        }
        return sb.toString();
    }

    private void shareCsv(boolean whatsapp) {
        try {
            File f = writeSharedText("VKS_Ledger_"+selectedFy()+"_"+today()+".csv", csvText());
            shareFile(f,"text/csv",whatsapp);
        } catch(Exception e){ toast(e.getMessage()); }
    }

    private void saveCsv() {
        pendingSaveText = csvText();
        createDocument("text/csv","VKS_Ledger_"+selectedFy()+"_"+today()+".csv");
    }

    private void shareBackup(boolean whatsapp) {
        toast("Preparing full backup…");
        io.execute(() -> {
            try {
                String json = BackupUtils.buildBackup(this,db);
                File f = writeSharedText("VKS_Full_Backup_"+today()+".json",json);
                runOnUiThread(() -> shareFile(f,"application/json",whatsapp));
            } catch(Exception e){ runOnUiThread(() -> toast("Backup failed: "+e.getMessage())); }
        });
    }

    private void saveBackup() {
        toast("Preparing full backup…");
        io.execute(() -> {
            try {
                pendingSaveText = BackupUtils.buildBackup(this,db);
                runOnUiThread(() -> createDocument("application/json","VKS_Full_Backup_"+today()+".json"));
            } catch(Exception e){ runOnUiThread(() -> toast("Backup failed: "+e.getMessage())); }
        });
    }

    private void sharePdf(boolean whatsapp) {
        try {
            File f = createPdf();
            shareFile(f,"application/pdf",whatsapp);
        } catch(Exception e){ toast("PDF failed: "+e.getMessage()); }
    }

    private void savePdf() {
        try {
            pendingSaveFile = createPdf();
            createDocument("application/pdf","VKS_Ledger_"+selectedFy()+"_"+today()+".pdf");
        } catch(Exception e){ toast("PDF failed: "+e.getMessage()); }
    }

    private File createPdf() throws Exception {
        File dir = sharedDir();
        File f = new File(dir,"VKS_Ledger_"+selectedFy()+"_"+today()+".pdf");
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int width=595,height=842,left=36,top=48,line=18;
        int pageNo=1,y=top;

        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,pageNo).create());
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG); titlePaint.setColor(BRAND); titlePaint.setTextSize(17); titlePaint.setFakeBoldText(true);
        Paint normal = new Paint(Paint.ANTI_ALIAS_FLAG); normal.setColor(Color.BLACK); normal.setTextSize(9);
        Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG); muted.setColor(Color.DKGRAY); muted.setTextSize(8);

        page.getCanvas().drawText("VKS Party Ledger — "+selectedFy(),left,y,titlePaint); y+=22;
        page.getCanvas().drawText("Generated "+today()+" • "+filteredData.size()+" transaction(s)",left,y,muted); y+=22;

        for (TransactionEntity t : filteredData) {
            if (y > height-55) {
                pdf.finishPage(page); pageNo++; y=top;
                page=pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,pageNo).create());
                page.getCanvas().drawText("VKS Party Ledger — "+selectedFy()+" — page "+pageNo,left,y,titlePaint); y+=25;
            }
            String amount = t.creditPaise!=null ? "+ "+money(t.creditPaise) : t.debitPaise!=null ? "− "+money(t.debitPaise) : "";
            String row = ellipsize((safe(t.dateText).isEmpty()?"No date":t.dateText)+"  "+safe(t.particular),70);
            page.getCanvas().drawText(row,left,y,normal);
            page.getCanvas().drawText(amount,width-150,y, t.creditPaise!=null ? greenPaint(9) : redPaint(9));
            y+=line;
            if(!safe(t.notes).isEmpty()){
                page.getCanvas().drawText(ellipsize(t.notes,88),left+10,y,muted); y+=line;
            }
        }
        pdf.finishPage(page);
        try(FileOutputStream out=new FileOutputStream(f)){ pdf.writeTo(out); }
        pdf.close();
        return f;
    }

    private void shareSummaryWhatsApp() {
        long cr=0,dr=0;
        for(TransactionEntity t: filteredData){cr+=nz(t.creditPaise);dr+=nz(t.debitPaise);}
        String msg="VKS Party Ledger — "+selectedFy()+"\nTransactions: "+filteredData.size()+"\nCredit: "+money(cr)+"\nDebit: "+money(dr)+"\nNet: "+money(cr-dr)+"\nCurrent balance: "+balanceText.getText();
        Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT,msg);
        if(!launchPackage(send,"com.whatsapp") && !launchPackage(send,"com.whatsapp.w4b")){
            startActivity(Intent.createChooser(send,"Share ledger summary"));
        }
    }

    private void shareFile(File f,String mime,boolean whatsappOnly) {
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM,uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(f.getName(),uri));
        if(whatsappOnly){
            if(launchPackage(send,"com.whatsapp")) return;
            if(launchPackage(send,"com.whatsapp.w4b")) return;
            toast("WhatsApp not found; opening Android share menu.");
        }
        startActivity(Intent.createChooser(send,"Share "+f.getName()));
    }

    private boolean launchPackage(Intent base,String pkg){
        Intent i=new Intent(base);i.setPackage(pkg);
        try{startActivity(i);return true;}catch(ActivityNotFoundException e){return false;}
    }

    private void createDocument(String mime,String name){
        pendingSaveMime=mime;pendingSaveName=name;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(i,REQ_SAVE);
    }

    private void chooseCloudFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,REQ_CLOUD_TREE);
    }

    private void cloudBackupNow(){
        if(!BackupUtils.hasCloudFolder(this)){toast("Choose a cloud folder first.");return;}
        toast("Backing up to cloud folder…");
        io.execute(() -> {
            boolean ok=BackupUtils.writeCloudBackup(this,db);
            runOnUiThread(() -> toast(ok?"Cloud backup updated.":"Cloud backup failed. Re-select the cloud folder."));
        });
    }

    private void restoreCloud(){
        if(!BackupUtils.hasCloudFolder(this)){toast("Choose a cloud folder first.");return;}
        new AlertDialog.Builder(this).setTitle("Restore cloud backup?")
                .setMessage("This replaces the current local database with the cloud backup.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Restore",(d,w)-> io.execute(() -> {
                    try{
                        String json=BackupUtils.readCloudBackup(this);
                        int n=BackupUtils.restoreBackup(this,db,json);
                        loadDataFromDb();
                        runOnUiThread(() -> toast("Restored "+n+" transactions."));
                    }catch(Exception e){runOnUiThread(() -> toast("Restore failed: "+e.getMessage()));}
                })).show();
    }

    private void pickRestoreFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        startActivityForResult(i,REQ_RESTORE);
    }

    private void restoreFromUri(Uri uri){
        io.execute(() -> {
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new Exception("Cannot open backup.");
                String json=new String(readAll(in),StandardCharsets.UTF_8);
                int n=BackupUtils.restoreBackup(this,db,json);
                BackupUtils.scheduleAutoBackup(this);
                loadDataFromDb();
                runOnUiThread(() -> toast("Restored "+n+" transactions."));
            }catch(Exception e){runOnUiThread(() -> toast("Restore failed: "+e.getMessage()));}
        });
    }

    private void exportSourceAudit(){
        io.execute(() -> {
            try{
                String audit=BackupUtils.readSourceAudit(this);
                File f=writeSharedText("VKS_Source_Audit.json",audit);
                runOnUiThread(() -> shareFile(f,"application/json",false));
            }catch(Exception e){runOnUiThread(() -> toast("Audit export failed: "+e.getMessage()));}
        });
    }

    private void showAuditSummary(){
        int source=0,user=0,blank=0,suspicious=0;
        for(TransactionEntity t:allData){
            if("XLSX".equals(t.sourceType))source++;else user++;
            if(t.dateText==null||t.dateText.trim().isEmpty())blank++;
            if("2025-26".equals(t.financialYear)&&t.dateText!=null&&
                    (t.dateText.equals("75")||t.dateText.startsWith("2026-08")||t.dateText.startsWith("2026-09")||t.dateText.startsWith("2026-10")||t.dateText.startsWith("2026-12"))) suspicious++;
        }
        String msg="Native Room/SQLite database\n\nImported source transactions: "+source+
                "\nApp-added transactions: "+user+
                "\nBlank source dates preserved: "+blank+
                "\nQuestionable source dates preserved: "+suspicious+
                "\n\nWorkbook totals verified:\n2024-25: Cr ₹24,27,301 • Dr ₹19,57,321 • Balance ₹4,69,980"+
                "\n2025-26: Cr ₹43,69,661 • Dr ₹42,31,777.82 • Balance ₹1,37,883.18"+
                "\n2026-27: Cr ₹7,64,290 • Dr ₹6,14,747 • Balance ₹1,49,543"+
                "\n\nSupporting workbook sheets are bundled in Source Audit and are not double-counted.";
        new AlertDialog.Builder(this).setTitle("Data audit").setMessage(msg).setPositiveButton("OK",null).show();
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_PHOTO){
            if(resultCode==RESULT_OK){
                Uri uri=(data!=null&&data.getData()!=null)?data.getData():cameraUri;
                if(uri!=null)attachPhoto(uri);
            }
            cameraUri=null;return;
        }
        if(requestCode==REQ_SAVE){
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
                Uri uri=data.getData();
                io.execute(() -> {
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                        if(out==null)throw new Exception("Cannot open destination.");
                        if(pendingSaveFile!=null){
                            try(FileInputStream in=new FileInputStream(pendingSaveFile)){
                                byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                            }
                        }else if(pendingSaveText!=null){
                            out.write(pendingSaveText.getBytes(StandardCharsets.UTF_8));
                        }
                        out.flush();runOnUiThread(() -> toast("Saved successfully."));
                    }catch(Exception e){runOnUiThread(() -> toast("Save failed: "+e.getMessage()));}
                    finally{pendingSaveText=null;pendingSaveFile=null;}
                });
            }
            return;
        }
        if(requestCode==REQ_RESTORE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            new AlertDialog.Builder(this).setTitle("Restore this backup?")
                    .setMessage("Current local data will be replaced by the selected backup.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Restore",(d,w)->restoreFromUri(uri)).show();
            return;
        }
        if(requestCode==REQ_CLOUD_TREE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try{
                getContentResolver().takePersistableUriPermission(uri,flags);
                BackupUtils.setCloudFolder(this,uri);
                toast("Cloud folder connected.");
                cloudBackupNow();
            }catch(Exception e){toast("Could not keep access to this folder.");}
        }
    }

    private File writeSharedText(String name,String content)throws Exception{
        File f=new File(sharedDir(),name);
        try(FileOutputStream out=new FileOutputStream(f)){out.write(content.getBytes(StandardCharsets.UTF_8));}
        return f;
    }

    private File sharedDir(){
        File dir=new File(getCacheDir(),"shared");if(!dir.exists())dir.mkdirs();return dir;
    }

    private static byte[] readAll(InputStream in)throws Exception{
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();
    }

    private String selectedFy(){return String.valueOf(fySpinner.getSelectedItem()).replace("All","All_Years");}
    private String today(){return java.time.LocalDate.now().toString();}
    private String csv(Object v){String s=v==null?"":String.valueOf(v);return "\""+s.replace("\"","\"\"")+"\"";}
    private long nz(Long v){return v==null?0L:v;}
    private String safe(String s){return s==null?"":s;}
    private String ellipsize(String s,int max){return s.length()<=max?s:s.substring(0,max-1)+"…";}

    private long parsePaise(String rupees){
        BigDecimal d=new BigDecimal(rupees.trim()).setScale(2,RoundingMode.HALF_UP);
        return d.multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private String formatPlain(Long paise){
        if(paise==null)return "";
        BigDecimal d=BigDecimal.valueOf(paise,2).stripTrailingZeros();
        return d.toPlainString();
    }

    private String rupees(long paise){return formatPlain(paise);}
    private String money(long paise){
        BigDecimal d=BigDecimal.valueOf(paise,2);
        DecimalFormat df=new DecimalFormat("#,##0.##");
        return (paise<0?"-₹":"₹")+df.format(d.abs());
    }

    private Paint greenPaint(float size){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(GREEN);p.setTextSize(size);p.setFakeBoldText(true);return p;}
    private Paint redPaint(float size){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(RED);p.setTextSize(size);p.setFakeBoldText(true);return p;}

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams weight(float w,int h,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,w);p.setMargins(m,m,m,m);return p;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(fg);b.setTextSize(13);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackground(round(bg,bg,12));return b;}
    private Button chip(String s,boolean selected){Button b=button(s,selected?BRAND:Color.WHITE,selected?Color.WHITE:MUTED);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));p.setMargins(0,0,dp(7),0);b.setLayoutParams(p);return b;}
    private GradientDrawable round(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private GradientDrawable gradient(int a,int b,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(radius));return g;}
    private EditText field(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setInputType(type);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.WHITE,LINE,10));e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));return e;}
    private Spinner fieldSpinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setBackground(round(Color.WHITE,LINE,10));s.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));return s;}
    private void addLabel(LinearLayout p,String s){TextView t=text(s,11,MUTED,true);t.setPadding(0,dp(10),0,dp(4));p.addView(t);}
    private void setSpinnerValue(Spinner s,String value){for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(value)){s.setSelection(i);return;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.Holder>{
        private List<TransactionEntity> items=new ArrayList<>();
        void setItems(List<TransactionEntity> x){items=x;notifyDataSetChanged();}

        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(13),dp(11),dp(13),dp(11));card.setBackground(round(Color.WHITE,LINE,14));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(12),dp(4),dp(12),dp(4));card.setLayoutParams(lp);

            LinearLayout top=row();TextView date=text("",12,MUTED,true);TextView amount=text("",17,Color.BLACK,true);amount.setGravity(Gravity.END);
            top.addView(date,new LinearLayout.LayoutParams(0,dp(28),1));top.addView(amount,new LinearLayout.LayoutParams(dp(180),dp(28)));card.addView(top);
            TextView part=text("",15,Color.rgb(30,35,40),true);part.setPadding(0,dp(3),0,dp(2));card.addView(part);
            TextView note=text("",11,MUTED,false);card.addView(note);
            LinearLayout foot=row();TextView badge=text("",10,MUTED,true);TextView bal=text("",11,Color.DKGRAY,true);bal.setGravity(Gravity.END);
            foot.addView(badge,new LinearLayout.LayoutParams(0,dp(26),1));foot.addView(bal,new LinearLayout.LayoutParams(dp(160),dp(26)));card.addView(foot);
            return new Holder(card,date,amount,part,note,badge,bal);
        }

        @Override public void onBindViewHolder(Holder h,int pos){
            TransactionEntity t=items.get(pos);
            h.date.setText(safe(t.dateText).isEmpty()?"Date not entered":t.dateText);
            if(t.creditPaise!=null&&t.debitPaise!=null){h.amount.setText("Cr "+money(t.creditPaise)+" / Dr "+money(t.debitPaise));h.amount.setTextSize(12);h.amount.setTextColor(Color.DKGRAY);}
            else if(t.creditPaise!=null){h.amount.setText("+ "+money(t.creditPaise));h.amount.setTextSize(17);h.amount.setTextColor(GREEN);}
            else if(t.debitPaise!=null){h.amount.setText("− "+money(t.debitPaise));h.amount.setTextSize(17);h.amount.setTextColor(RED);}
            else {h.amount.setText("—");h.amount.setTextColor(MUTED);}
            h.part.setText(safe(t.particular).isEmpty()?"(no description)":t.particular);
            String n=safe(t.notes);h.note.setText(n);h.note.setVisibility(n.isEmpty()?View.GONE:View.VISIBLE);
            boolean photo=t.photoPath!=null&&new File(t.photoPath).isFile();
            h.badge.setText(t.financialYear+"  •  "+("XLSX".equals(t.sourceType)?"SOURCE":"APP")+(photo?"  •  PHOTO":""));
            Long bal=computedBalance.get(t.id);h.bal.setText(bal==null?"":"Bal "+money(bal));
            h.itemView.setOnClickListener(v->showTransactionMenu(t));
        }
        @Override public int getItemCount(){return items.size();}
        class Holder extends RecyclerView.ViewHolder{
            TextView date,amount,part,note,badge,bal;
            Holder(View v,TextView d,TextView a,TextView p,TextView n,TextView b,TextView l){super(v);date=d;amount=a;part=p;note=n;badge=b;bal=l;}
        }
    }

    private static class SimpleWatcher implements TextWatcher{
        private final Runnable r;SimpleWatcher(Runnable r){this.r=r;}
        public void beforeTextChanged(CharSequence s,int st,int c,int a){}
        public void onTextChanged(CharSequence s,int st,int before,int count){r.run();}
        public void afterTextChanged(Editable e){}
    }
}
