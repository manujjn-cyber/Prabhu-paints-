package in.prabhupaints.vksledger;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final String[] FYS={"2026-27","2025-26","2024-25","All"};
    static final int PICK_PHOTO=40, PICK_CLOUD=41, PICK_RESTORE=42;
    final int BRAND=Color.rgb(23,60,53), GREEN=Color.rgb(8,127,91), RED=Color.rgb(201,42,42);
    LedgerDb db; LinearLayout root; TextView balanceView, totalsView, countView, cloudView;
    EditText search; Spinner fySpinner; ListView list; LedgerAdapter adapter;
    String selectedFy="2026-27", pendingPhotoFor=null;
    Uri cloudTree=null;
    final NumberFormat money=NumberFormat.getCurrencyInstance(new Locale("en","IN"));

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new LedgerDb(this);
        cloudTree=loadCloudTree();
        buildUi();
        refresh();
    }

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);
        v.setPadding(dp(2),dp(3),dp(2),dp(3));if(bold)v.setTypeface(null,1);return v;
    }
    Button button(String s){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(dp(46));return b;
    }
    LinearLayout card(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(12),dp(14),dp(12));
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);g.setCornerRadius(dp(15));g.setStroke(dp(1),Color.rgb(226,230,234));l.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(12),dp(6),dp(12),dp(6));l.setLayoutParams(p);return l;
    }
    @Override public void setTitle(CharSequence t){super.setTitle(t);}

    void buildUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,247,248));
        setContentView(root);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(16),dp(15),dp(16),dp(14));hero.setBackgroundColor(BRAND);
        TextView title=text("VKS Party Ledger",22,Color.WHITE,true);hero.addView(title);
        TextView sub=text("Native Android • SQLite database • Prabhu Paints",12,Color.rgb(210,229,224),false);hero.addView(sub);
        balanceView=text("₹0",30,Color.WHITE,true);balanceView.setGravity(Gravity.END);hero.addView(balanceView);
        TextView bl=text("CURRENT BALANCE",10,Color.rgb(210,229,224),true);bl.setGravity(Gravity.END);hero.addView(bl);

        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);
        Button bill=button("＋ Add Bill");Button pay=button("− Add Payment");
        bill.setOnClickListener(v->showAddDialog(true));pay.setOnClickListener(v->showAddDialog(false));
        quick.addView(bill,new LinearLayout.LayoutParams(0,dp(50),1));quick.addView(pay,new LinearLayout.LayoutParams(0,dp(50),1));
        hero.addView(quick);root.addView(hero);

        LinearLayout controls=card();
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        search=new EditText(this);search.setHint("Search date, amount, bill, payment…");search.setSingleLine(true);search.setTextSize(14);
        fySpinner=new Spinner(this);ArrayAdapter<String> fa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,FYS);fySpinner.setAdapter(fa);
        row.addView(search,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(fySpinner,new LinearLayout.LayoutParams(dp(115),dp(50)));
        controls.addView(row);
        totalsView=text("",13,Color.DKGRAY,true);controls.addView(totalsView);
        cloudView=text("",11,Color.GRAY,false);controls.addView(cloudView);
        root.addView(controls);

        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);tools.setPadding(dp(10),0,dp(10),0);
        Button export=button("Export / WhatsApp");Button cloud=button("Cloud Backup");
        export.setOnClickListener(v->showExportMenu());cloud.setOnClickListener(v->showCloudMenu());
        tools.addView(export,new LinearLayout.LayoutParams(0,dp(48),1));tools.addView(cloud,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(tools);

        countView=text("",12,Color.GRAY,true);countView.setPadding(dp(16),dp(8),dp(16),dp(4));root.addView(countView);
        list=new ListView(this);list.setDivider(null);list.setPadding(dp(8),0,dp(8),dp(20));list.setClipToPadding(false);
        adapter=new LedgerAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));

        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh();}public void afterTextChanged(android.text.Editable e){}});
        fySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selectedFy=FYS[pos];refresh();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        list.setOnItemClickListener((p,v,pos,id)->showDetails(adapter.items.get(pos)));
    }

    void refresh(){
        if(db==null||adapter==null)return;
        String q=search==null?"":search.getText().toString().trim();
        adapter.items=db.query(selectedFy,q);adapter.notifyDataSetChanged();
        double[] t=db.totals(selectedFy,q);
        double bal=db.currentBalance(selectedFy.equals("All")?"2026-27":selectedFy);
        balanceView.setText(money.format(bal));
        totalsView.setText("Credit "+money.format(t[0])+"   •   Debit "+money.format(t[1]));
        countView.setText(adapter.items.size()+" transactions shown");
        cloudView.setText(cloudTree==null?"Cloud backup: Not connected":"Cloud backup: Connected • automatic after new entries");
    }

    void showAddDialog(boolean credit){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(4),dp(18),0);
        EditText date=new EditText(this);date.setHint("YYYY-MM-DD");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));
        EditText amount=new EditText(this);amount.setHint("Amount ₹");amount.setInputType(2|8192);
        EditText part=new EditText(this);part.setHint("Particular");part.setText(credit?"BILL":"PAYMENT");
        EditText notes=new EditText(this);notes.setHint("Notes (optional)");
        Spinner f=new Spinner(this);f.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"2026-27","2025-26","2024-25"}));
        int ix=selectedFy.equals("2025-26")?1:selectedFy.equals("2024-25")?2:0;f.setSelection(ix);
        box.addView(date);box.addView(f);box.addView(amount);box.addView(part);box.addView(notes);
        new AlertDialog.Builder(this).setTitle(credit?"Add Bill / Credit":"Add Payment / Debit").setView(box)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                try{
                    double a=Double.parseDouble(amount.getText().toString().trim());
                    if(a<=0)throw new Exception();
                    String id="user-"+System.currentTimeMillis();
                    db.addUser(id,(String)f.getSelectedItem(),date.getText().toString().trim(),part.getText().toString().trim(),
                        credit?a:null,credit?null:a,notes.getText().toString().trim());
                    refresh();autoCloudBackup();askPhoto(id);
                }catch(Exception e){Toast.makeText(this,"Enter a valid amount",Toast.LENGTH_LONG).show();}
            }).show();
    }

    void askPhoto(String id){
        new AlertDialog.Builder(this).setMessage("Attach bill/payment picture to this transaction?")
            .setNegativeButton("Not now",null).setPositiveButton("Choose picture",(d,w)->pickPhoto(id)).show();
    }
    void pickPhoto(String id){
        pendingPhotoFor=id;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_PHOTO);
    }

    void showDetails(Row r){
        String s=(r.date.isEmpty()?"Date not entered":r.date)+"\n\n"+r.particular+"\n"+
            (r.credit!=null?"Credit: "+money.format(r.credit)+"\n":"")+(r.debit!=null?"Debit: "+money.format(r.debit)+"\n":"")+
            "Balance: "+money.format(r.balance)+"\nFY: "+r.fy+(r.notes.isEmpty()?"":"\nNotes: "+r.notes)+(r.source?"\n\nImported workbook row "+r.sourceRow:"\n\nApp-added entry");
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Transaction details").setMessage(s).setNegativeButton("Close",null);
        if(r.photo!=null&&!r.photo.isEmpty())b.setNeutralButton("Open picture",(d,w)->{try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(r.photo));i.setDataAndType(Uri.parse(r.photo),"image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){Toast.makeText(this,"Picture unavailable",Toast.LENGTH_SHORT).show();}});
        b.setPositiveButton(r.photo==null||r.photo.isEmpty()?"Attach picture":"Replace picture",(d,w)->pickPhoto(r.id)).show();
    }

    void showExportMenu(){
        String[] a={"Share CSV on WhatsApp","Share JSON backup on WhatsApp","Save CSV","Save JSON backup","Restore JSON backup"};
        new AlertDialog.Builder(this).setTitle("Export & Backup").setItems(a,(d,which)->{
            if(which==0)shareFile(db.csv(selectedFy,search.getText().toString()),"text/csv","VKS_Ledger_"+selectedFy+".csv",true);
            else if(which==1)shareFile(db.jsonBackup(),"application/json","VKS_Ledger_Backup.json",true);
            else if(which==2)saveDocument(db.csv(selectedFy,search.getText().toString()),"text/csv","VKS_Ledger_"+selectedFy+".csv");
            else if(which==3)saveDocument(db.jsonBackup(),"application/json","VKS_Ledger_Backup.json");
            else {Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_RESTORE);}
        }).show();
    }

    void showCloudMenu(){
        String[] a=cloudTree==null?new String[]{"Connect cloud folder","About cloud backup"}:new String[]{"Backup now","Change cloud folder","About cloud backup"};
        new AlertDialog.Builder(this).setTitle("Cloud Data Backup").setItems(a,(d,w)->{
            String x=a[w];
            if(x.startsWith("Connect")||x.startsWith("Change"))chooseCloud();
            else if(x.startsWith("Backup"))cloudBackup();
            else new AlertDialog.Builder(this).setMessage("Choose a folder from Google Drive, OneDrive, Dropbox or another Android document provider. The app writes VKS_Ledger_Auto_Backup.json there. New app-added transactions trigger automatic backup.").setPositiveButton("OK",null).show();
        }).show();
    }
    void chooseCloud(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_CLOUD);
    }
    Uri loadCloudTree(){String s=getPreferences(MODE_PRIVATE).getString("cloud","");return s.isEmpty()?null:Uri.parse(s);}
    void autoCloudBackup(){if(cloudTree!=null)new Handler(Looper.getMainLooper()).postDelayed(this::cloudBackup,500);}
    void cloudBackup(){
        if(cloudTree==null){chooseCloud();return;}
        final String data=db.jsonBackup();
        new Thread(()->{
            boolean ok=false;
            try{
                DocumentFile dir=DocumentFile.fromTreeUri(this,cloudTree);if(dir==null||!dir.canWrite())throw new Exception();
                DocumentFile f=dir.findFile("VKS_Ledger_Auto_Backup.json");if(f==null)f=dir.createFile("application/json","VKS_Ledger_Auto_Backup.json");
                try(OutputStream os=getContentResolver().openOutputStream(f.getUri(),"wt")){os.write(data.getBytes(StandardCharsets.UTF_8));}
                ok=true;
            }catch(Exception ignored){}
            boolean done=ok;runOnUiThread(()->Toast.makeText(this,done?"Cloud backup updated":"Cloud backup failed — reconnect folder",Toast.LENGTH_LONG).show());
        }).start();
    }

    void shareFile(String data,String mime,String name,boolean whatsapp){
        try{
            File dir=new File(getCacheDir(),"shared");dir.mkdirs();File f=new File(dir,name);
            try(FileOutputStream o=new FileOutputStream(f)){o.write(data.getBytes(StandardCharsets.UTF_8));}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(whatsapp){i.setPackage("com.whatsapp");try{startActivity(i);return;}catch(Exception e){i.setPackage("com.whatsapp.w4b");try{startActivity(i);return;}catch(Exception ignored){i.setPackage(null);}}}
            startActivity(Intent.createChooser(i,"Share "+name));
        }catch(Exception e){Toast.makeText(this,"Share failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void saveDocument(String data,String mime,String name){
        try{
            File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"exports");dir.mkdirs();File f=new File(dir,name);
            try(FileOutputStream o=new FileOutputStream(f)){o.write(data.getBytes(StandardCharsets.UTF_8));}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Save or send "+name));
        }catch(Exception e){Toast.makeText(this,"Export failed",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
        Uri u=data.getData();if(u==null)return;
        if(req==PICK_PHOTO&&pendingPhotoFor!=null){
            try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            db.setPhoto(pendingPhotoFor,u.toString());pendingPhotoFor=null;refresh();autoCloudBackup();
        }else if(req==PICK_CLOUD){
            try{int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getContentResolver().takePersistableUriPermission(u,flags);}catch(Exception ignored){}
            cloudTree=u;getPreferences(MODE_PRIVATE).edit().putString("cloud",u.toString()).apply();refresh();cloudBackup();
        }else if(req==PICK_RESTORE){
            try{String json=readAll(getContentResolver().openInputStream(u));int n=db.restoreUserBackup(json);refresh();autoCloudBackup();Toast.makeText(this,"Restored "+n+" app-added entries",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Restore failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    String readAll(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toString("UTF-8");}

    class LedgerAdapter extends BaseAdapter{
        List<Row> items=new ArrayList<>();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View v,android.view.ViewGroup parent){
            Row r=items.get(p);LinearLayout c=card();c.setLayoutParams(new AbsListView.LayoutParams(-1,-2));
            LinearLayout top=new LinearLayout(MainActivity.this);top.setOrientation(LinearLayout.HORIZONTAL);
            TextView d=text(r.date.isEmpty()?"DATE NOT ENTERED":r.date,12,Color.GRAY,true);
            Double amt=r.credit!=null?r.credit:r.debit;TextView a=text((r.credit!=null?"+ ":"− ")+money.format(amt==null?0:amt),18,r.credit!=null?GREEN:RED,true);a.setGravity(Gravity.END);
            top.addView(d,new LinearLayout.LayoutParams(0,-2,1));top.addView(a);c.addView(top);
            c.addView(text(r.particular.isEmpty()?"(No particular)":r.particular,15,Color.rgb(28,36,44),true));
            if(!r.notes.isEmpty())c.addView(text(r.notes,12,Color.GRAY,false));
            TextView foot=text(r.fy+"  •  "+(r.source?"SOURCE":"APP")+(r.photo!=null&&!r.photo.isEmpty()?"  •  PHOTO":"")+"     Bal "+money.format(r.balance),11,Color.GRAY,true);c.addView(foot);
            return c;
        }
    }

    static class Row{
        String id,fy,date,particular,notes,photo;Double credit,debit;double balance;boolean source;int sourceRow;
    }

    static class LedgerDb extends SQLiteOpenHelper{
        final Context ctx;
        LedgerDb(Context c){super(c,"vks_ledger_native.db",null,2);ctx=c;getWritableDatabase();}
        public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE tx(id TEXT PRIMARY KEY,fy TEXT,date TEXT,particular TEXT,credit REAL,debit REAL,category TEXT,balance REAL,notes TEXT,flag TEXT,source INTEGER,source_row INTEGER,photo TEXT,created_at INTEGER)");
            importCsv(d);
        }
        public void onUpgrade(SQLiteDatabase d,int old,int now){}
        void importCsv(SQLiteDatabase d){
            try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("ledger.csv"),StandardCharsets.UTF_8))){
                String line=br.readLine();d.beginTransaction();
                while((line=br.readLine())!=null){
                    List<String> x=parse(line);if(x.size()<11)continue;
                    ContentValues v=new ContentValues();v.put("id",x.get(0));v.put("fy",x.get(1));v.put("date",x.get(2));v.put("particular",x.get(3));
                    putNum(v,"credit",x.get(4));putNum(v,"debit",x.get(5));v.put("category",x.get(6));putNum(v,"balance",x.get(7));v.put("notes",x.get(8));v.put("flag",x.get(9));v.put("source",1);v.put("source_row",toInt(x.get(10)));v.put("created_at",0);d.insert("tx",null,v);
                }d.setTransactionSuccessful();d.endTransaction();
            }catch(Exception e){throw new RuntimeException(e);}
        }
        static void putNum(ContentValues v,String k,String s){if(s==null||s.isEmpty())v.putNull(k);else v.put(k,Double.parseDouble(s));}
        static int toInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
        static List<String> parse(String line){
            ArrayList<String> a=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;
            for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){a.add(b.toString());b.setLength(0);}else b.append(c);}a.add(b.toString());return a;
        }
        void addUser(String id,String fy,String date,String part,Double cr,Double dr,String notes){
            SQLiteDatabase d=getWritableDatabase();ContentValues v=new ContentValues();v.put("id",id);v.put("fy",fy);v.put("date",date);v.put("particular",part);if(cr==null)v.putNull("credit");else v.put("credit",cr);if(dr==null)v.putNull("debit");else v.put("debit",dr);v.put("notes",notes);v.put("source",0);v.put("source_row",0);v.put("created_at",System.currentTimeMillis());d.insertOrThrow("tx",null,v);recalcUserBalances(fy);
        }
        void recalcUserBalances(String fy){
            SQLiteDatabase d=getWritableDatabase();double bal=0;
            Cursor c=d.rawQuery("SELECT balance FROM tx WHERE fy=? AND source=1 AND balance IS NOT NULL ORDER BY rowid DESC LIMIT 1",new String[]{fy});if(c.moveToFirst())bal=c.getDouble(0);c.close();
            c=d.rawQuery("SELECT id,credit,debit FROM tx WHERE fy=? AND source=0 ORDER BY created_at,rowid",new String[]{fy});
            while(c.moveToNext()){if(!c.isNull(1))bal+=c.getDouble(1);if(!c.isNull(2))bal-=c.getDouble(2);ContentValues v=new ContentValues();v.put("balance",bal);d.update("tx",v,"id=?",new String[]{c.getString(0)});}c.close();
        }
        void setPhoto(String id,String uri){ContentValues v=new ContentValues();v.put("photo",uri);getWritableDatabase().update("tx",v,"id=?",new String[]{id});}
        List<Row> query(String fy,String q){
            ArrayList<Row> out=new ArrayList<>();String where="1=1";ArrayList<String> args=new ArrayList<>();
            if(!"All".equals(fy)){where+=" AND fy=?";args.add(fy);}if(q!=null&&!q.trim().isEmpty()){where+=" AND (particular LIKE ? OR date LIKE ? OR notes LIKE ? OR CAST(credit AS TEXT) LIKE ? OR CAST(debit AS TEXT) LIKE ?)";String z="%"+q.trim()+"%";for(int i=0;i<5;i++)args.add(z);}
            Cursor c=getReadableDatabase().query("tx",null,where,args.toArray(new String[0]),null,null,"CASE WHEN date='' THEN 0 ELSE 1 END DESC,date DESC,created_at DESC,rowid DESC");
            while(c.moveToNext())out.add(row(c));c.close();return out;
        }
        Row row(Cursor c){Row r=new Row();r.id=g(c,"id");r.fy=g(c,"fy");r.date=g(c,"date");r.particular=g(c,"particular");r.notes=g(c,"notes");r.photo=g(c,"photo");r.credit=n(c,"credit");r.debit=n(c,"debit");Double b=n(c,"balance");r.balance=b==null?0:b;r.source=c.getInt(c.getColumnIndexOrThrow("source"))==1;r.sourceRow=c.getInt(c.getColumnIndexOrThrow("source_row"));return r;}
        String g(Cursor c,String k){int i=c.getColumnIndexOrThrow(k);return c.isNull(i)?"":c.getString(i);}Double n(Cursor c,String k){int i=c.getColumnIndexOrThrow(k);return c.isNull(i)?null:c.getDouble(i);}
        double[] totals(String fy,String q){List<Row> a=query(fy,q);double cr=0,dr=0;for(Row r:a){if(r.credit!=null)cr+=r.credit;if(r.debit!=null)dr+=r.debit;}return new double[]{cr,dr};}
        double currentBalance(String fy){Cursor c=getReadableDatabase().rawQuery("SELECT balance FROM tx WHERE fy=? AND balance IS NOT NULL ORDER BY source ASC,CASE WHEN source=0 THEN created_at ELSE rowid END DESC LIMIT 1",new String[]{fy});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        String csv(String fy,String q){StringBuilder b=new StringBuilder("\uFEFFDate,FY,Particular,Credit,Debit,Balance,Notes,Source\r\n");for(Row r:query(fy,q)){b.append(csv(r.date)).append(',').append(csv(r.fy)).append(',').append(csv(r.particular)).append(',').append(r.credit==null?"":r.credit).append(',').append(r.debit==null?"":r.debit).append(',').append(r.balance).append(',').append(csv(r.notes)).append(',').append(r.source?"Imported XLSX":"App").append("\r\n");}return b.toString();}
        static String csv(String s){return "\""+(s==null?"":s.replace("\"","\"\""))+"\"";}
        String jsonBackup(){try{JSONArray a=new JSONArray();Cursor c=getReadableDatabase().query("tx",null,null,null,null,null,"rowid");while(c.moveToNext()){Row r=row(c);JSONObject o=new JSONObject();o.put("id",r.id);o.put("fy",r.fy);o.put("date",r.date);o.put("particular",r.particular);o.put("credit",r.credit==null?JSONObject.NULL:r.credit);o.put("debit",r.debit==null?JSONObject.NULL:r.debit);o.put("balance",r.balance);o.put("notes",r.notes);o.put("source",r.source);o.put("sourceRow",r.sourceRow);o.put("photo",r.photo);a.put(o);}c.close();JSONObject root=new JSONObject();root.put("app","VKS Party Ledger Native");root.put("version","2.0.0");root.put("exportedAt",System.currentTimeMillis());root.put("transactions",a);return root.toString(2);}catch(Exception e){return "{}";}}
        int restoreUserBackup(String json)throws Exception{JSONObject root=new JSONObject(json);JSONArray a=root.getJSONArray("transactions");SQLiteDatabase d=getWritableDatabase();d.delete("tx","source=0",null);int n=0;HashSet<String> fys=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("source",true))continue;ContentValues v=new ContentValues();v.put("id",o.getString("id"));v.put("fy",o.getString("fy"));v.put("date",o.optString("date",""));v.put("particular",o.optString("particular",""));if(o.isNull("credit"))v.putNull("credit");else v.put("credit",o.getDouble("credit"));if(o.isNull("debit"))v.putNull("debit");else v.put("debit",o.getDouble("debit"));v.put("notes",o.optString("notes",""));v.put("source",0);v.put("source_row",0);v.put("photo",o.optString("photo",""));v.put("created_at",System.currentTimeMillis()+i);d.insert("tx",null,v);fys.add(o.getString("fy"));n++;}for(String fy:fys)recalcUserBalances(fy);return n;}
    }
}