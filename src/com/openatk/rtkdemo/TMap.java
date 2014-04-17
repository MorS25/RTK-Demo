package com.openatk.rtkdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;



public class TMap extends Activity {
	public static final int DONE_WITH_NETTASK = 0x01;
	public static final int MAP_DRAW_OVERLAY = 0x02;
	public static final int PLOT_ON_OVERLAY = 0x03;
	public static final int HOSTNAME_CONNECT = 0x04;
	public static final int SHOW_TOAST = 0x05;
	
	public GoogleMap mMap;
	public ArrayList<LatLng> gplist;
	public LatLng referenceLatLng;
	public static float aprx_latlng_per_cm = 0.0000001f;
	public Menu myMenu;
	private String serverAddress;

	private ImageView canvas;
	private int canvas_width, canvas_height;
	
	private int curpixel_idx;
	public ArrayList<Integer> pathPivots;
	public ArrayList<Integer> colorScheme;

	//NW to NE : Top
	private LatLng NW = new LatLng(40.429932932,-86.911030507); //Top Left
	private LatLng NE = new LatLng(40.429949890,-86.911030507); //Long_dist Right
	//SW to SE : Bottom
	private LatLng SW = new LatLng(40.429932932,-86.911053802); //Bottom Left
	private LatLng SE = new LatLng(40.429949890,-86.911053802); //Bottom Right
	
	private double MIN_LATITUDE, MAX_LATITUDE;
	private double MIN_LONGITUDE, MAX_LONGITUDE;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tmap);
		
		ActionBar bar = getActionBar();
		bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.raserorange)));
		pathPivots = new ArrayList<Integer>();
		
    	gplist = new ArrayList<LatLng>();
    	canvas_width = 500;
    	canvas_height = 900;
		canvas = (ImageView)this.findViewById(R.id.imageView1);
		curpixel_idx = 0;
		colorScheme = new ArrayList<Integer>();
		colorScheme.add(getResources().getColor(R.color.isoblue));
		colorScheme.add(getResources().getColor(R.color.kayak));
		colorScheme.add(Color.GREEN);
		colorScheme.add(getResources().getColor(R.color.turtle));

	}

	public void setLabelValue(String val){
		TextView x = (TextView)this.findViewById(R.id.textView1);
		x.setText(val);
	}
	
	private final Handler postman = new Handler() {
		@Override
		public void handleMessage(Message msg) {
						
			switch (msg.what) {
				case SHOW_TOAST:
				
				Toast toast = Toast.makeText(TMap.this, msg.obj.toString(),Toast.LENGTH_SHORT);
		 	    toast.show();

				break;
				
				case DONE_WITH_NETTASK:
					//Parse Received DATA
					
					String [] j = (String []) msg.obj;
					setLabelValue(j[1] + ": " + j[2] + "," + j[3]);
					LatLng myPoint = new LatLng(Double.parseDouble(j[2]),Double.parseDouble(j[3]));
					
					if(gplist.size() == 0){
						//We "may" use this as reference point to set screen boundary

						referenceLatLng = myPoint;
						//Init Max-Min 
						//TODO: Use Heap? Maybe overkill for demo
						MIN_LATITUDE = myPoint.latitude;
						MIN_LONGITUDE = myPoint.longitude;
						MAX_LATITUDE = MIN_LATITUDE;
						MAX_LONGITUDE = MIN_LONGITUDE;
					}else{ 
						//If gplist has data, check it against current point
						if(GeoUtil.SameLatLng(myPoint, gplist.get(gplist.size() - 1))){
							//don't feed repetitive point over and over
							//save some memory
							break;
						}
						
						
						//Rescale 4 calibration points
						if(myPoint.latitude > MAX_LATITUDE){
							MAX_LATITUDE = myPoint.latitude;
						}
						if(myPoint.longitude > MAX_LONGITUDE){
							MAX_LONGITUDE = myPoint.longitude;
						}
						if(myPoint.latitude < MIN_LATITUDE){
							MIN_LATITUDE = myPoint.latitude;
						}
						if(myPoint.longitude < MIN_LONGITUDE){
							MIN_LONGITUDE = myPoint.longitude;
						}
						

						NW = new LatLng(MIN_LATITUDE - 0.000004,MAX_LONGITUDE + 0.000004);
						NE = new LatLng(MAX_LATITUDE + 0.000004,MAX_LONGITUDE + 0.000004);
						SW = new LatLng(MIN_LATITUDE - 0.000004,MIN_LONGITUDE - 0.000004);
						SE = new LatLng(MAX_LATITUDE + 0.000004,MIN_LONGITUDE - 0.000004);
					}
					
					
					gplist.add(myPoint);
					
			    	postman.obtainMessage(TMap.PLOT_ON_OVERLAY,
							-1, -1, null).sendToTarget();

				break;
				case MAP_DRAW_OVERLAY:
					 //Get Generated Image passed in via Handler
					
					 Bitmap image = (Bitmap)msg.obj;
					 canvas.setImageBitmap(image);
					 
				break;
				case PLOT_ON_OVERLAY:

					//Update Overlay
					Thread FastDraw = new BitMapGenerateThread();
					FastDraw.start();
					
			    break;
			    
				case HOSTNAME_CONNECT:
					serverAddress = (String)msg.obj;
					Log.i("TMAP",serverAddress);
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
				break;

			}
		}
	};
	
	public void reset_action(){
		gplist.clear();
		gplist.add(new LatLng(0,0));
		postman.obtainMessage(TMap.PLOT_ON_OVERLAY,
					-1, -1, null).sendToTarget();
		pathPivots.clear();
	}
	
	public void connect_action() {
		
		//NetTask LTI = new NetTask();
		//AsyncTask test = LTI.execute();
		/*Button m =(Button)v;
		m.setText("Streaming");
		m.setEnabled(false);*/
		
		final HostnameDialog dia = new HostnameDialog();	
		dia.mContext = TMap.this;
		dia.setHandler(postman);
		
		runOnUiThread(new Runnable() {
            public void run() {
            	dia.show(getFragmentManager(), "dialogTMAPHD"); 
            }
        });

	}
	
	public Socket getSocketForBBB(){
		InetAddress serveraddr;
		Socket clisock = null;
		try {
			serveraddr = InetAddress.getByName(serverAddress);
			//Log.i("VIP",serveraddr.toString());
			clisock = new Socket(serveraddr,9000);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			postman.obtainMessage(TMap.SHOW_TOAST,
					-1, -1, "Unknown Host Error").sendToTarget();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			postman.obtainMessage(TMap.SHOW_TOAST,
					-1, -1, "IO Error").sendToTarget();
		}
		
		return clisock;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		getMenuInflater().inflate(R.menu.tmap, menu);
		myMenu = menu;
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	 public boolean onOptionsItemSelected(MenuItem item) {
	    	switch(item.getItemId()){
	    		case R.id.action_connect:
	    			connect_action();
	    			break;
	    		case R.id.action_reset:
	    			reset_action();
	    			break;
	    		case R.id.action_newpath:
	    			if(pathPivots.size() == colorScheme.size()){
	    				postman.obtainMessage(TMap.SHOW_TOAST,
								-1, -1, "No more color left in Color Scheme").sendToTarget();
	    				return true;
	    			}
	    			pathPivots.add(curpixel_idx);
	    			postman.obtainMessage(TMap.SHOW_TOAST,
	    					-1, -1, "Started new Path").sendToTarget();
	    			
	    			break;
	    		case R.id.action_expand:
	    			double dx = GeoUtil.distanceInMeter(NW, NE);
	    			Log.i("isoblue","distX " + dx);

	    			break;
	    	}
	    	return true;
	    }
	
	private class NetTask extends AsyncTask<URL, Integer, Long> {
		public String readcontent;
		public boolean halt;
		@Override
		protected Long doInBackground(URL... params) {
			Socket client = getSocketForBBB();
			if(client == null){
				Log.e("TMAP","NMAP ERROR");
				halt = true;
				return null;
			}
			halt = false;
			try {
				InputStream incomingStream = client.getInputStream();
				OutputStream outgoingStream = client.getOutputStream();
				
		        DataOutputStream out =
	                     new DataOutputStream(outgoingStream);
		        out.writeUTF("RQST=GPS");
		        
		        BufferedInputStream in = new BufferedInputStream(incomingStream,1024);
		        
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
	         //Log.i("VIP",readcontent);
			if(halt){
				return;
			}
	         String[] datax;
	         datax = readcontent.split("\\s+");
	         postman.obtainMessage(TMap.DONE_WITH_NETTASK,
 					-1, -1, datax).sendToTarget();
	    }

		
		
	}

	private class BitMapGenerateThread extends Thread{
		private Point getApproximatePoint(LatLng curr){
			double LL_width = GeoUtil.distanceInMeter(new LatLng(NE.latitude,0), new LatLng(SW.latitude,0));
			double LL_height = GeoUtil.distanceInMeter(new LatLng(0, NW.longitude), new LatLng(0,SW.longitude));
			double curr_NW_lat_diff = GeoUtil.distanceInMeter(new LatLng(curr.latitude, 0), new LatLng(NW.latitude,0));
			double Lat_dist = curr_NW_lat_diff/(LL_width);
			double NE_curr_long_diff = GeoUtil.distanceInMeter(new LatLng(0, curr.longitude), new LatLng(0,NE.longitude));
			double Long_dist = NE_curr_long_diff/(LL_height);
			
			//Log.i("RTKDEMO", "Lat_dist = " + Lat_dist);
			//Log.i("RTKDEMO", "Long_dist = " + Long_dist); //We can determine NW latitude by this relation
			
			return new Point((int)(Lat_dist*canvas_width),(int)(Long_dist*canvas_height));
		}
		
		public void run(){
			 //Generate Bitmap
			 
			 Bitmap image = Bitmap.createBitmap(canvas_width,canvas_height,Bitmap.Config.ARGB_8888);
			 
			 //Set Boundaries
			 Canvas canvas = new Canvas(image);
			 Paint redfp  = new Paint();
			 redfp.setColor(Color.BLACK);
			 redfp.setStrokeWidth(10);
			 
			 Paint greenfp  = new Paint();
			 greenfp.setColor(Color.BLUE);
			 greenfp.setStrokeWidth(10);
			 
			 Paint pathPaint = new Paint();
			 pathPaint.setColor(getResources().getColor(R.color.raserpink));
			 pathPaint.setStrokeWidth(2);
			 pathPaint.setAntiAlias(true);

			/*for(int i = 0; i< canvas_width; i++){
			 canvas.drawPoint(0, i, redfp);
			 canvas.drawPoint(i, 0, redfp);
			 canvas.drawPoint(canvas_width - 1, i, redfp);
			 canvas.drawPoint(i, canvas_width, redfp);
			 }*/
			 
			 //Calibration Points
			 Point R1 = getApproximatePoint(NE);
			 Point R2 = getApproximatePoint(SE);
			 Point R3 = getApproximatePoint(SW);
			 Point R4 = getApproximatePoint(NW);

			 canvas.drawPoint(R1.x, R1.y, greenfp);
			 canvas.drawPoint(R2.x, R2.y, greenfp);
			 canvas.drawPoint(R3.x, R3.y, greenfp);
			 canvas.drawPoint(R4.x, R4.y, greenfp);
			 
			 canvas.drawText(String.format("(%.7f,%.7f)",NW.latitude,NW.longitude), R4.x+10, R4.y+20,redfp);
			 canvas.drawText(String.format("(%.7f,%.7f)",NE.latitude,NE.longitude), R1.x-170, R1.y+20,redfp);
			 canvas.drawText(String.format("(%.7f,%.7f)",SE.latitude,SE.longitude), R2.x-170, R2.y-10,redfp);

			 
			 
			 double METER_W = GeoUtil.distanceInMeter(new LatLng(NE.latitude,0), new LatLng(SW.latitude,0));
			 
			 int j;
			 for(j = 0; j < 50; j++){
				 canvas.drawPoint(R3.x + j, R3.y, redfp);
			 }
			 //canvas.drawText("Width : " + String.format("%.3f", METER_W*100/canvas_width) + " cm per pixel", R3.x + 10, R3.y - 10, greenfp);
			 canvas.drawText(String.format("%.3f", METER_W*100*j/canvas_width) + " cm", R3.x + 10, R3.y - 10, redfp);

			 Log.i("uniduc", gplist.get(gplist.size() -1).latitude + " LAT");
			 
			 Point PreviousQ = null;
			 LatLng PreviousC = null;
			 int path_idx = 0; 
			 //Draw
			 for(curpixel_idx = 1; curpixel_idx < gplist.size(); curpixel_idx++){
				 LatLng C = gplist.get(curpixel_idx);
				 Point Q = getApproximatePoint(C);
				 
				if(PreviousQ != null && ! GeoUtil.SamePoint(PreviousQ,Q)){
						if(GeoUtil.distanceInMeter(C,PreviousC) < 1){
							//Flying is prohibited, you can't move 1 meter at a time
							//Unless you jump
							//Just don't jump
							
							 canvas.drawLine(PreviousQ.x, PreviousQ.y, Q.x, Q.y, pathPaint);
							 
							 if(curpixel_idx == gplist.size() - 1){
								 canvas.drawText(String.format("(%.7f,%.7f)",C.latitude,C.longitude),Q.x + (float)Math.random()*30,Q.y+(float)Math.random()*30,pathPaint);
							 }
							 
						}
				 }
				 				
				//Change color if user want new path
				 if(pathPivots.size() != 0 && curpixel_idx == pathPivots.get(path_idx)){
					 pathPaint.setColor(colorScheme.get(path_idx));
					 if(path_idx + 1 <= pathPivots.size() - 1){
						 path_idx++;
					 }
				 }
				 
				 PreviousQ = Q;
				 PreviousC = C;
			 }
			 
			postman.obtainMessage(TMap.MAP_DRAW_OVERLAY,
						-1, -1, image).sendToTarget();
						
		}
		
		
		
	}
}
