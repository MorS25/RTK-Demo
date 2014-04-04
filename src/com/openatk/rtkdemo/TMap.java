package com.openatk.rtkdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

import android.support.v4.app.FragmentActivity;

public class TMap extends Activity {
	public static final int DONE_WITH_NETTASK = 0x01;
	public GoogleMap mMap;
	public Polyline linePath; 
	public ArrayList<LatLng> gplist;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tmap);
		
		Button x = (Button)this.findViewById(R.id.requestbutton);
		x.setOnClickListener(requestbutton_click);

		mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap(); //init map
		mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

    	linePath = mMap.addPolyline(new PolylineOptions()
  	     .width(3)
  	     .color(Color.RED));
		
    	gplist = new ArrayList<LatLng>();
    	
    	mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(40.429937303,-86.911030570), 25.00f)); 
    	
    	

    	
	}
	
	public void setLabelValue(String val){
		TextView x = (TextView)this.findViewById(R.id.textView1);
		x.setText(val);
	}
	
	private final Handler postman = new Handler() {
		@Override
		public void handleMessage(Message msg) {
						
			switch (msg.what) {
			case DONE_WITH_NETTASK:
				String [] j = (String []) msg.obj;
				Log.i("postman","MSG HAS LENGTH"+ j.length);
				setLabelValue(j[1] + ": " + j[2] + "," + j[3]);
				LatLng myPoint = new LatLng(Float.parseFloat(j[2]),Float.parseFloat(j[3]));
				Log.i("postman",myPoint.toString());
				gplist.add(myPoint);

				if(gplist.size() > 0){
					mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gplist.get(gplist.size() -1), 25.00f)); 	
					linePath.setPoints(gplist);

				}
				
				
				break;
			}
		}
	};
	
	View.OnClickListener requestbutton_click = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			
			//NetTask LTI = new NetTask();
			//AsyncTask test = LTI.execute();
			Button m =(Button)v;
			m.setText("Streaming");
			m.setEnabled(false);
			
			new Timer().scheduleAtFixedRate(new TimerTask() {
	            @Override
	            public void run() {
	                runOnUiThread(new Runnable() {
	                    @Override
	                    public void run() {
	                    	NetTask LTI = new NetTask();
	            			AsyncTask test = LTI.execute();


	                    }
	                });
	            }
	        }, 0, 500);



		}
	};
	public Socket getSocketForBBB(){
		InetAddress serveraddr;
		Socket clisock = null;
		try {
			serveraddr = InetAddress.getByName("192.168.1.15");
			Log.i("VIP",serveraddr.toString());
			clisock = new Socket(serveraddr,9000);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return clisock;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tmap, menu);
		return true;
	}
	
	private class NetTask extends AsyncTask<URL, Integer, Long> {
		public String readcontent;
		@Override
		protected Long doInBackground(URL... params) {
			Socket client = getSocketForBBB();
			try {
				InputStream incomingStream = client.getInputStream();
				OutputStream outgoingStream = client.getOutputStream();
				
		        DataOutputStream out =
	                     new DataOutputStream(outgoingStream);
		        out.writeUTF("RQST=GPS");
		        
		        Log.i("VIP","Will now read response");
		        
		        BufferedInputStream in = new BufferedInputStream(incomingStream,1024);
		        //Log.i("RTKTCP",new String(ByteStreams.toByteArray(in),Charsets.UTF_8));
		        
		        byte[] contents = new byte[1024];
	            int bytesRead=0;
	            //read byte by byte and put into contents
	            while( (bytesRead = in.read(contents)) != -1){ 
	                 
	            }
	            
	            readcontent = new String(contents, "UTF-8");
		        client.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        Long y = null;
	        return y;
		}
		
		protected void onPostExecute(Long result) {
	         Log.i("VIP",readcontent);
	         String[] datax;
	         datax = readcontent.split("\\s+");
	         postman.obtainMessage(TMap.DONE_WITH_NETTASK,
 					-1, -1, datax).sendToTarget();
	    }

	    
	}

}
