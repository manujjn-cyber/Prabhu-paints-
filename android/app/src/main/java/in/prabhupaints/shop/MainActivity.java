package in.prabhupaints.shop;
import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.*;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.widget.Toast;
public class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTitle("Prabhu Paints");
        web = new WebView(this); setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl(); Uri allowed = Uri.parse(BuildConfig.SHOP_URL);
                return !("https".equals(target.getScheme()) && allowed.getHost().equals(target.getHost()) && allowed.getPort()==target.getPort());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if(request.isForMainFrame()) Toast.makeText(MainActivity.this,"Connection unavailable / इंटरनेट जाँचें. Use Refresh.",Toast.LENGTH_LONG).show();
            }
            // SSL errors use the platform default: cancel. Never bypass certificate validation.
        });
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl(BuildConfig.SHOP_URL);
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,1,0,"Refresh / ताज़ा करें");
        menu.add(0,2,1,"Print / PDF");
        menu.add(0,3,2,"Open browser / Export"); return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==1) { web.reload(); return true; }
        if(item.getItemId()==2) {
            PrintManager manager=(PrintManager)getSystemService(PRINT_SERVICE);
            manager.print("Prabhu Paints",web.createPrintDocumentAdapter("Prabhu Paints"),new PrintAttributes.Builder().build()); return true;
        }
        if(item.getItemId()==3) {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.SHOP_URL))); return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override public void onBackPressed() { if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
