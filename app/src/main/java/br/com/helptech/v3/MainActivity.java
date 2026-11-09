package br.com.helptech.v3;
import android.app.*;import android.bluetooth.*;import android.content.pm.PackageManager;import java.util.*;import java.io.*;import java.nio.charset.Charset;import android.os.*;import android.content.*;import android.webkit.*;import androidx.webkit.WebViewAssetLoader;import org.json.*;
public class MainActivity extends Activity {
 WebView view;String attendedId;
 @Override public void onCreate(Bundle b){super.onCreate(b);view=new WebView(this);view.getSettings().setJavaScriptEnabled(true);view.getSettings().setDomStorageEnabled(true);view.addJavascriptInterface(new PrinterBridge(),"HelpTechPrinter");view.addJavascriptInterface(new Object(){@JavascriptInterface public void schedule(String json){runOnUiThread(()->{try{JSONArray a=new JSONArray(json);getSharedPreferences("alarms",0).edit().putString("jobs",json).apply();for(int n=0;n<a.length();n++){JSONObject j=a.getJSONObject(n);String id=j.getString("id");if(getSharedPreferences("attended",0).getBoolean(id,false))continue;long at=java.time.LocalDateTime.parse(j.getString("date")+"T"+j.getString("time")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-3600000;if(at>System.currentTimeMillis())AlarmReceiver.set(MainActivity.this,id,j.optString("client"),j.optString("service"),j.optString("address"),at);}}catch(Exception e){android.util.Log.e("HelpTech","Alarm schedule",e);}});}},"HelpTechAlarm");WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();view.setWebViewClient(new WebViewClient(){@Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}@Override public void onPageFinished(WebView v,String u){applyAttended();}});setContentView(view);attendedId=getIntent().getStringExtra("attended_id");view.loadUrl("https://appassets.androidplatform.net/assets/index.html");if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},3);}

 // Bluetooth Classic SPP / ESC-POS. Requer impressora pareada no Android.
 class PrinterBridge {
  final String PREF="helptech_printer";
  @JavascriptInterface public void listPaired(){runOnUiThread(()->{
   if(!printerPermission())return;
   try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled()){printerEvent("error","Ative o Bluetooth do celular.");return;}
    JSONArray arr=new JSONArray();for(BluetoothDevice d:a.getBondedDevices()){JSONObject o=new JSONObject();o.put("name",d.getName());o.put("address",d.getAddress());arr.put(o);}printerEvent("devices",arr.toString());
   }catch(Exception e){printerEvent("error","Falha ao listar impressoras: "+e.getMessage());}
  });}
  @JavascriptInterface public void saveAddress(String address){if(!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")){printerEvent("error","Endereço Bluetooth inválido.");return;}getSharedPreferences(PREF,0).edit().putString("address",address).apply();printerEvent("saved",address);}
  @JavascriptInterface public String getAddress(){return getSharedPreferences(PREF,0).getString("address","");}
  @JavascriptInterface public void printReceipt(String payload){runOnUiThread(()->{
   if(!printerPermission())return;
   String address=getAddress();if(address.isEmpty()){printerEvent("error","Selecione uma impressora nas Configurações.");return;}
   if(payload.length()>24000){printerEvent("error","Orçamento excede o limite de impressão.");return;}
   new Thread(()->{BluetoothSocket socket=null;try{
    BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled())throw new IOException("Bluetooth desligado");
    BluetoothDevice device=a.getRemoteDevice(address);socket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket.connect();
    JSONObject j=new JSONObject(payload);String body=j.optString("text","");if(body.trim().isEmpty())throw new IOException("Não há conteúdo para imprimir");
    int cols=j.optInt("columns",48);cols=cols==32?32:48;
    OutputStream out=socket.getOutputStream();out.write(new byte[]{27,64,27,116,2}); // init + codepage PC850 (varia por fabricante)
    StringBuilder clean=new StringBuilder();for(String line:body.replace("\r","").split("\n",-1)){
      line=line.replaceAll("[\\p{Cntrl}&&[^\\t]]","");while(line.length()>cols){clean.append(line,0,cols).append('\n');line=line.substring(cols);}clean.append(line).append('\n');}
    out.write(clean.toString().getBytes(Charset.forName("CP850")));out.write(new byte[]{10,10,10});out.flush();printerEvent("printed","Dados enviados à impressora.");
   }catch(Exception e){printerEvent("error","Impressão falhou: "+e.getMessage());}finally{if(socket!=null)try{socket.close();}catch(Exception ignored){}}}).start();
  });}
 }
 boolean printerPermission(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},77);printerEvent("error","Autorize o Bluetooth e tente novamente.");return false;}return true;}
 void printerEvent(String type,String value){runOnUiThread(()->{if(view!=null)view.evaluateJavascript("window.helpTechPrinterEvent&&window.helpTechPrinterEvent("+JSONObject.quote(type)+","+JSONObject.quote(value)+")",null);});}
 void applyAttended(){if(attendedId!=null){view.evaluateJavascript("window.helpTechMarkAttended&&window.helpTechMarkAttended("+JSONObject.quote(attendedId)+")",null);attendedId=null;}}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);attendedId=i.getStringExtra("attended_id");applyAttended();}
 @Override public void onBackPressed(){if(view.canGoBack())view.goBack();else super.onBackPressed();}
}
