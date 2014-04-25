package com.openatk.rtkdemo;

import com.google.android.gms.maps.model.LatLng;

public class RTKPoint {
	public LatLng coordinate;
	public int QValue;
	
	public RTKPoint(int Q,LatLng co){
		QValue = Q;
		coordinate = co;
	}
}
