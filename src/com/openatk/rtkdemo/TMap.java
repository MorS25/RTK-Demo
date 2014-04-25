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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

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
	public ArrayList<RTKPoint> gplist;
	public LatLng referenceLatLng;
	public Menu myMenu;
	private String serverAddress;

	private ImageView canvas;
	private int canvas_width, canvas_height;
	
	private int curpixel_idx;
	public ArrayList<Integer> pathPivots;
	public ArrayList<Integer> colorScheme;

	//NW to NE : Top
	private LatLng NW = new LatLng(40.429932932,-86.911030507); //Top Left
	private LatLng NE = new LatLng(40.429932932,-86.911030507); //Long_dist Right
	//SW to SE : Bottom
	private LatLng SW = new LatLng(40.429932932,-86.911053802); //Bottom Left
	private LatLng SE = new LatLng(40.429932932,-86.911053802); //Bottom Right
	
	private double MIN_LATITUDE, MAX_LATITUDE;
	private double MIN_LONGITUDE, MAX_LONGITUDE;
	private double LATDELTA = 0.000011;
	private double LONDELTA = 0.00001;
	
    private static final int SWIPE_MAX_OFF_PATH = 1000;
    private GestureDetector gestureDetector;
    
    View.OnTouchListener gestureListener;
    
    
    private boolean useManualScrolling;
    
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tmap);
		
		ActionBar bar = getActionBar();
		bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.raserorange)));
		pathPivots = new ArrayList<Integer>();
		
    	gplist = new ArrayList<RTKPoint>();
    	canvas_width = 500;
    	canvas_height = 900;
		canvas = (ImageView)this.findViewById(R.id.imageView1);
		curpixel_idx = 0;
		colorScheme = new ArrayList<Integer>();
		colorScheme.add(getResources().getColor(R.color.isoblue));
		colorScheme.add(getResources().getColor(R.color.kayak));
		colorScheme.add(Color.GREEN);
		colorScheme.add(getResources().getColor(R.color.turtle));
		
		
		//Assign gesture listener
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };
        canvas.setOnTouchListener(gestureListener);
        useManualScrolling = false;
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
					setLabelValue(j[1] + ": " + j[2] + "," + j[3] + "(Q:" + j[5] + ")");
					LatLng myCoordinate = new LatLng(Double.parseDouble(j[2]),Double.parseDouble(j[3]));
					int QValue = Integer.parseInt(j[5]);
					
					RTKPoint myPoint = new RTKPoint(QValue, myCoordinate);
					
					//if(QValue == 1){
					if(gplist.size() == 0){
						//We "may" use this as reference point to set screen boundary

						referenceLatLng = myPoint.coordinate;
						//Init Max-Min 
						//TODO: Use Heap? Maybe overkill for demo
						if(!useManualScrolling){
							MIN_LATITUDE = myPoint.coordinate.latitude;
							MIN_LONGITUDE = myPoint.coordinate.longitude;
							MAX_LATITUDE = MIN_LATITUDE;
							MAX_LONGITUDE = MIN_LONGITUDE;
						}
					}else{ 
						//If gplist has data, check it against current point
						if(GeoUtil.SameLatLng(myPoint.coordinate, gplist.get(gplist.size() - 1).coordinate)){
							//don't feed repetitive point over and over
							//save some memory
							break;
						}
						
						if(!useManualScrolling){
						
							//Rescale 4 calibration points
							if(myPoint.coordinate.latitude > MAX_LATITUDE){
								MAX_LATITUDE = myPoint.coordinate.latitude;
							}
							if(myPoint.coordinate.longitude > MAX_LONGITUDE){
								MAX_LONGITUDE = myPoint.coordinate.longitude;
							}
							if(myPoint.coordinate.latitude < MIN_LATITUDE){
								MIN_LATITUDE = myPoint.coordinate.latitude;
							}
							if(myPoint.coordinate.longitude < MIN_LONGITUDE){
								MIN_LONGITUDE = myPoint.coordinate.longitude;
							}
						
							updateBounds();
						}
						
					}
					
					
						gplist.add(myPoint);
						postman.obtainMessage(TMap.PLOT_ON_OVERLAY,
								-1, -1, null).sendToTarget();
					//}
					
					
			    	

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
	
	void updateBounds(){
		NW = new LatLng(MIN_LATITUDE - LATDELTA,MAX_LONGITUDE + LONDELTA);
		NE = new LatLng(MAX_LATITUDE + LATDELTA,MAX_LONGITUDE + LONDELTA);
		SW = new LatLng(MIN_LATITUDE - LATDELTA,MIN_LONGITUDE - LONDELTA);
		SE = new LatLng(MAX_LATITUDE + LATDELTA,MIN_LONGITUDE - LONDELTA);
	}
	
	public void reset_action(){
		gplist.clear();
		gplist.add(new RTKPoint(2,new LatLng(0,0)));
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
			double STEPSIZE = 0.00000025;

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
	    			/*
	    			   NW------NE
	    			   |        |
	    			   |        |
	    			   |        |
	    			   SW------SE
	    			  	    			 */
	    			Log.i("Tmap","Expansion Mode");
	    			useManualScrolling=false;
	    			
	    			break;
	    		case R.id.action_zoomin:
	    			useManualScrolling = true;

//	    			if(MAX_LATITUDE - STEPSIZE > MIN_LATITUDE -STEPSIZE){
		    			MIN_LATITUDE += STEPSIZE;
		    			MAX_LATITUDE -= STEPSIZE;
		    			
		    			Log.i("maxLat",MAX_LATITUDE + "," + MAX_LONGITUDE);
		    			updateBounds();
//	    			}else{
//	    				postman.obtainMessage(TMap.SHOW_TOAST,
//	    						-1, -1, "Max Zoom").sendToTarget();
//	    		x`	}

	    			break;
	    			
	    		case R.id.action_zoomout:

	    			MIN_LATITUDE -= STEPSIZE;
	    			MAX_LATITUDE += STEPSIZE;
	    			
	    			updateBounds();
	    			
	    			break;
	    	}
	    	return true;
	    }
	
	public class MyGestureDetector extends SimpleOnGestureListener {
		
	        @Override
	        public boolean onScroll(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
	            try {
	                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
	                    return false;
	                
	                double dx = e2.getX() - e1.getX();
	                double dy = e2.getY() - e1.getY();
	                Log.i("scroll motion", "dx: " + dx + " dy: " + dy);
	                useManualScrolling = true;
	                
	                float DELTA_SCROLL = 0.0000001f;
	                
	                if(dx > 0){
	                	NE = new LatLng(NE.latitude - DELTA_SCROLL,NE.longitude);
						SE = new LatLng(SE.latitude - DELTA_SCROLL,SE.longitude);
						SW = new LatLng(SW.latitude - DELTA_SCROLL,SW.longitude);
						NW = new LatLng(NW.latitude - DELTA_SCROLL,NW.longitude);
	                }else if(dx < 0){
	                	NE = new LatLng(NE.latitude + DELTA_SCROLL,NE.longitude);
						SE = new LatLng(SE.latitude + DELTA_SCROLL,SE.longitude);
						SW = new LatLng(SW.latitude + DELTA_SCROLL,SW.longitude);
						NW = new LatLng(NW.latitude + DELTA_SCROLL,NW.longitude);
	                }
					
	                if(dy > 0){
	                	NE = new LatLng(NE.latitude,NE.longitude+ DELTA_SCROLL);
						SE = new LatLng(SE.latitude,SE.longitude+ DELTA_SCROLL);
						SW = new LatLng(SW.latitude,SW.longitude+ DELTA_SCROLL);
						NW = new LatLng(NW.latitude,NW.longitude+ DELTA_SCROLL);
	                }else if(dx < 0){
	                	NE = new LatLng(NE.latitude,NE.longitude - DELTA_SCROLL);
						SE = new LatLng(SE.latitude,SE.longitude - DELTA_SCROLL);
						SW = new LatLng(SW.latitude,SW.longitude - DELTA_SCROLL);
						NW = new LatLng(NW.latitude,NW.longitude - DELTA_SCROLL);
	                }
	                
	                
	            } catch (Exception e) {
	                // nothing
	            }
	            return false;
	        }

	            @Override
	        public boolean onDown(MotionEvent e) {
	              return true;
	        }
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
			 Paint greenfp  = new Paint();
			 Paint gridFp  = new Paint();
			 Paint pathPaint = new Paint();
			 Paint grayfp  = new Paint();	 

			 gridFp.setColor(Color.GRAY);
			 gridFp.setStrokeWidth(1);
			 gridFp.setStyle(Style.STROKE);
			 gridFp.setPathEffect(new DashPathEffect(new float[] {10,20}, 0));
			 pathPaint.setColor(getResources().getColor(R.color.raserpink));
			 pathPaint.setStrokeWidth(2);
			 pathPaint.setAntiAlias(true);
			 greenfp.setColor(Color.BLUE);
			 greenfp.setStrokeWidth(10);
			 redfp.setColor(Color.BLACK);
			 redfp.setStrokeWidth(10);
			 grayfp.setColor(Color.GRAY);
			 grayfp.setStrokeWidth(1);


			 
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
			 
			 
			 double METER_W = GeoUtil.distanceInMeter(new LatLng(NE.latitude,0), new LatLng(NW.latitude,0));
			 double METER_H = GeoUtil.distanceInMeter(new LatLng(0,NW.longitude), new LatLng(0,SW.longitude));

			 int j;
			 for(j = 0; j < 50; j++){
				 canvas.drawPoint(R3.x + j, R3.y, redfp);
			 }
			 
			 //canvas.drawText("Width : " + String.format("%.3f", METER_W*100/canvas_width) + " cm per pixel", R3.x + 10, R3.y - 10, greenfp);
			 
			 canvas.drawText(String.format("%.3f", METER_W*100*j/canvas_width) + " cm", R3.x + 5, R3.y - 10, redfp);
			 
			 double unit_width = METER_W*100*j/canvas_width;
			 double unit_height = METER_H*100*j/canvas_height;

			 int k;
			 for(k=R3.x;k<=(canvas_width/unit_width);k++){ //k is number of unit block
				 canvas.drawLine(k*j, 0, k*j, canvas_height, gridFp);
				 
			 }
			 for(k=R3.x;k<=(canvas_height/unit_height);k++){ //k is number of unit block
				 canvas.drawLine(0, k*j, canvas_width, k*j, gridFp);
				 
			 }
			 
			 Log.i("uniduc", gplist.get(gplist.size() -1).coordinate.latitude + " LAT");
			 
			 Point PreviousQ = null;
			 LatLng PreviousC = null;
			 int path_idx = 0; 
			 //Draw
			 for(curpixel_idx = 1; curpixel_idx < gplist.size(); curpixel_idx++){
				 RTKPoint C_RTK = gplist.get(curpixel_idx);
				 LatLng C = C_RTK.coordinate;
				 Point Q = getApproximatePoint(C);
				 
				if(PreviousQ != null && ! GeoUtil.SamePoint(PreviousQ,Q)){
						if(GeoUtil.distanceInMeter(C,PreviousC) < 1){
							//Flying is prohibited, you can't move 1 meter at a time
							//Unless you jump
							//Just don't jump
							
							 
							 if(C_RTK.QValue != 1){
								 canvas.drawCircle(Q.x, Q.y,5, grayfp);
							 }else{
								 canvas.drawLine(PreviousQ.x, PreviousQ.y, Q.x, Q.y, pathPaint);
							 }
							 
							 if(curpixel_idx == gplist.size() - 1){
								 //show coordinate
								 float locaX = Q.x + (float)Math.random()*30;
								 float locaY = Q.y+(float)Math.random()*30;
								 
								 canvas.drawText(String.format("(%.7f,%.7f)",C.latitude,C.longitude),locaX,locaY,pathPaint);
								 canvas.drawCircle(Q.x, Q.y,3, pathPaint);
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
