package in.prabhupaints.shop;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import android.print.PrintManager;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private ProgressBar progress;
    private LinearLayout error;
    private String css = "", enhancements = "";
    private boolean failed;
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean allowed(String url) {
        if (url == null) return false;
        Uri target = Uri.parse(url), origin = Uri.parse(BuildConfig.SHOP_URL);
        return "https".equals(target.getScheme()) && origin.getHost().equals(target.getHost()) && origin.getPort() == target.getPort();
    }
    private String asset(String name) {
        try (InputStream input = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        css = asset("luxury.css"); enhancements = asset("enhance.js");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250,248,243)); root.setFitsSystemWindows(true);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12),dp(8),dp(8),dp(8)); bar.setBackgroundColor(Color.rgb(21,21,21));
        ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.ic_mark);
        mark.setContentDescription("Prabhu Paints"); bar.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title = new TextView(this); title.setText("PRABHU PAINTS\nATELIER · PILOT 0.2");
        title.setTextColor(Color.rgb(233,220,195)); title.setTextSize(14); title.setPadding(dp(8),0,0,0);
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button menu = new Button(this); menu.setText("☰"); menu.setContentDescription("Menu / मेन्यू");
        menu.setTextColor(Color.rgb(233,220,195)); menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setOnClickListener(v -> showMenu()); bar.addView(menu,new LinearLayout.LayoutParams(dp(56),dp(48)));
        root.addView(bar);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        FrameLayout body = new FrameLayout(this); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        web = new WebView(this); web.setBackgroundColor(Color.rgb(250,248,243)); body.addView(web,new FrameLayout.LayoutParams(-1,-1));
        error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(android.view.Gravity.CENTER);
        error.setPadding(dp(28),dp(28),dp(28),dp(28)); error.setBackgroundColor(Color.rgb(250,248,243));
        TextView message = new TextView(this); message.setText("Connection unavailable\nकनेक्शन उपलब्ध नहीं है\n\nCheck your internet connection and try again.\nकनेक्शन जाँचकर दोबारा कोशिश करें।\n\nAn interrupted save may have reached the server. Check Sales before creating the bill again.\nबिल दोबारा बनाने से पहले बिक्री सूची जाँचें।");
        message.setTextSize(17); message.setTextColor(Color.rgb(21,21,21)); error.addView(message);
        Button retry = new Button(this); retry.setText("Retry / फिर कोशिश करें"); retry.setOnClickListener(v -> web.loadUrl(BuildConfig.SHOP_URL)); error.addView(retry);
        error.setVisibility(View.GONE); body.addView(error,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !allowed(request.getUrl().toString()); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                failed=false; error.setVisibility(View.GONE); progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if(!failed && allowed(url)) {
                    String script="(function(){let s=document.getElementById('pp-native-theme');if(!s){s=document.createElement('style');s.id='pp-native-theme';document.head.appendChild(s)}s.textContent="+JSONObject.quote(css)+";})();";
                    view.evaluateJavascript(script,null); view.evaluateJavascript(enhancements,null);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError detail) {
                if(request.isForMainFrame()) { failed=true; error.setVisibility(View.VISIBLE); progress.setVisibility(View.GONE); }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if(request.isForMainFrame() && response.getStatusCode()>=400) { failed=true; error.setVisibility(View.VISIBLE); }
            }
            // Default certificate-error handling cancels the request. Never bypass TLS errors.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) { progress.setProgress(value); }
        });
        web.loadUrl(BuildConfig.SHOP_URL);
    }
    private void showMenu() {
        new AlertDialog.Builder(this).setTitle("Prabhu Atelier")
            .setItems(new String[]{"Refresh / ताज़ा करें","Print / PDF","Open browser / ब्राउज़र","About this pilot / जानकारी"},(dialog,which)->{
                if(which==0) new AlertDialog.Builder(this).setMessage("Refresh may clear an unsaved cart. / बिना सहेजा बिल हट सकता है।").setPositiveButton("Refresh",(d,w)->web.loadUrl(BuildConfig.SHOP_URL)).setNegativeButton("Cancel",null).show();
                if(which==1) { if(failed)return; PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE); pm.print("Prabhu Paints",web.createPrintDocumentAdapter("Prabhu Paints"),null); }
                if(which==2) startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.SHOP_URL)));
                if(which==3) new AlertDialog.Builder(this).setTitle("Atelier 0.2 · Pilot")
                    .setMessage("Ivory & gold interface • Hindi / English • Shared online workspace\n\nTest entries only. Durable cloud storage, offline sales and complete GST invoicing are not ready. Your stock draft has not been imported.\n\nकेवल परीक्षण डेटा डालें। स्थायी स्टोरेज, ऑफलाइन बिक्री और पूर्ण GST बिलिंग अभी तैयार नहीं हैं।")
                    .setPositiveButton("OK",null).show();
            }).show();
    }
    @Override public void onBackPressed() {
        if(web.canGoBack())web.goBack();
        else new AlertDialog.Builder(this).setMessage("Close app? Unsaved work may be lost. / ऐप बंद करें?")
            .setPositiveButton("Close",(d,w)->finish()).setNegativeButton("Cancel",null).show();
    }
    @Override protected void onDestroy() { if(web!=null){web.stopLoading();web.destroy();}super.onDestroy(); }
}
