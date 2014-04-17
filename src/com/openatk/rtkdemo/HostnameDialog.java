package com.openatk.rtkdemo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class HostnameDialog extends DialogFragment {
	public Context mContext;
	public Handler postman;
	public String[] items = {"Yield Data"};
	
	public void setHandler(Handler h){
		postman = h;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
	    
	    LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        final View modifview = inflater.inflate(R.layout.title,null);
        TextView titleX = (TextView)modifview.findViewById(R.id.textView1);
        titleX.setText("Server Address");
	    builder.setCustomTitle(modifview);
	    
	    //TODO: Use builder.setView();
	    final View TextSpecifier = inflater.inflate(R.layout.ipspecifier,null);
	    builder.setView(TextSpecifier);
	    final TextView addressTxt = (TextView)TextSpecifier.findViewById(R.id.addressText);
	    
	    builder.setPositiveButton("Connect to Server", new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int id) {
	    		  postman.obtainMessage(TMap.HOSTNAME_CONNECT,
							-1, -1, addressTxt.getText().toString()).sendToTarget();  
	    	}
	   });
	
	    
	    return builder.create();
	}
	
}
