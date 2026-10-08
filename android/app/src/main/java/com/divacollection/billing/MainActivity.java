package com.divacollection.billing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    WebView web;
    BluetoothSocket socket;
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){ super.onCreate(b);
        web=new WebView(this); web.setBackgroundColor(0xfff5f5f7);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true); web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.addJavascriptInterface(new Bridge(),"AndroidPrinter");
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("https://rahuldiva0505-khare.github.io/diva-collection-billing/");
        setContentView(web);
    }

    boolean btReady(){
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    void permission(){
        if(Build.VERSION.SDK_INT>=31 && !btReady()){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},91);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==91 && btReady()) connectPrinter();
        else if(requestCode==91) toast("Bluetooth permission allow karo, phir Connect dabao.");
    }

    @JavascriptInterface
    public void connectPrinter(){
        if(!btReady()){ permission(); return; }
        runOnUiThread(()->{ try{
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
            if(a==null){toast("Bluetooth available nahi hai");return;}
            if(!a.isEnabled()){toast("Phone Bluetooth ON karo");return;}
            Set<BluetoothDevice> ds=a.getBondedDevices();
            ArrayList<BluetoothDevice> matches=new ArrayList<>();
            for(BluetoothDevice d:ds){
                String n=d.getName()==null?"":d.getName().toUpperCase();
                if(n.contains("9280")||n.contains("JSC")) matches.add(d);
            }
            if(matches.size()==1){connect(matches.get(0));return;}
            if(matches.size()>1){
                String[] names=new String[matches.size()];
                for(int i=0;i<names.length;i++)names[i]=matches.get(i).getName();
                new AlertDialog.Builder(this).setTitle("Printer चुनें").setItems(names,(dialog,which)->connect(matches.get(which))).show();
                return;
            }
            if(ds.size()==1){connect(ds.iterator().next());return;}
            toast("Pehle Android Bluetooth Settings me POS-9280 pair karo");
        }catch(Exception e){toast("Bluetooth error: "+e.getMessage());}
    });}

    void connect(BluetoothDevice d){ new Thread(()->{ try{
        close();
        socket=d.createRfcommSocketToServiceRecord(SPP);
        try { socket.connect(); }
        catch(Exception first){
            close();
            socket=d.createInsecureRfcommSocketToServiceRecord(SPP);
            socket.connect();
        }
        runOnUiThread(()->toast("Connected: "+d.getName()));
    }catch(Exception e){close();runOnUiThread(()->toast("Connect nahi hua: "+e.getMessage()));} }).start(); }

    @JavascriptInterface
    public void disconnectPrinter(){ new Thread(this::close).start(); }

    @JavascriptInterface
    public void testPrinter(){
        new Thread(()->{
            try{
                if(socket==null||!socket.isConnected()){ runOnUiThread(()->toast("Pehle printer Connect karo.")); return; }
                OutputStream out=socket.getOutputStream();
                out.write(new byte[]{0x1B,0x40});
                out.write(tspl("Diva Collection","TEST","Bluetooth","2x1","DIVA-TEST",1999,1499).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                runOnUiThread(()->toast("Test label print command sent."));
            }catch(Exception e){runOnUiThread(()->toast("Test print error: "+e.getMessage()));}
        }).start();
    }

    @JavascriptInterface
    public void printLabel(String json){ new Thread(()->{
        try{
            if(socket==null||!socket.isConnected()){
                runOnUiThread(()->toast("Printer connected nahi hai. Pehle Connect JSC-9280 dabao."));
                return;
            }
            org.json.JSONObject x=new org.json.JSONObject(json);
            String name=x.optString("name",""), sku=x.optString("sku","");
            String color=x.optString("color",""), size=x.optString("size","");
            String code=x.optString("code","DIVA");
            double mrp=x.optDouble("mrp",0), sell=x.optDouble("selling",0);
            String cmd=tspl(name,sku,color,size,code,mrp,sell);
            OutputStream out=socket.getOutputStream();
            out.write(new byte[]{0x1B,0x40});
            out.write(cmd.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            runOnUiThread(()->toast("Label print sent"));
        }catch(Exception e){runOnUiThread(()->toast("Print error: "+e.getMessage()));}
    }).start();}

    String clean(String s){
        return s.replace("\\"," ").replace("\n"," ").replace("\r"," ");
    }

    String tspl(String name,String sku,String color,String size,String code,double mrp,double sell){
        name=clean(name); sku=clean(sku); color=clean(color); size=clean(size); code=clean(code);
        StringBuilder b=new StringBuilder();
        b.append("SIZE 2,1\r\n");
        b.append("GAP 0,0\r\n");
        b.append("DENSITY 8\r\n");
        b.append("DIRECTION 1\r\n");
        b.append("CLS\r\n");
        b.append("TEXT 203,8,\"0\",0,1,1,\"Diva Collection\"\r\n");
        b.append("TEXT 203,28,\"0\",0,1,1,\"").append(name).append("\"\r\n");
        b.append("TEXT 203,48,\"0\",0,1,1,\"").append(sku).append(" | ").append(color).append(" | ").append(size).append("\"\r\n");
        b.append("BARCODE 25,68,\"128\",58,1,0,2,2,\"").append(code).append("\"\r\n");
        b.append("TEXT 203,145,\"0\",0,1,1,\"MRP Rs ").append(String.format(Locale.US,"%.2f",mrp))
         .append(" | SELL Rs ").append(String.format(Locale.US,"%.2f",sell)).append("\"\r\n");
        b.append("PRINT 1,1\r\n");
        return b.toString();
    }

    void close(){try{if(socket!=null)socket.close();}catch(Exception e){}socket=null;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    class Bridge {
        @JavascriptInterface public void connectPrinter(){MainActivity.this.connectPrinter();}
        @JavascriptInterface public void disconnectPrinter(){MainActivity.this.disconnectPrinter();}
        @JavascriptInterface public void printLabel(String json){MainActivity.this.printLabel(json);}
        @JavascriptInterface public void testPrinter(){MainActivity.this.testPrinter();}
    }
}
