package com.jordann.maptest;

import java.lang.reflect.Array;

/**
 * Created by jordan_n on 8/22/2014.
 */
public class Stop {
    private double mLatitude;
    private double mLongitude;
    private String mName;
    private int[] mShuttleETAs = new int[3];


    public int[] getShuttleETAs() {
        return mShuttleETAs;
    }

    public void setShuttleETAs(int[] shuttleETAs) {
        mShuttleETAs = shuttleETAs;
    }


    public void setShuttleETA(int index, int ETA){
        mShuttleETAs[index] = ETA;
    }



    public double getLatitude() {
        return mLatitude;
    }

    public void setLatitude(double latitude) {
        mLatitude = latitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public void setLongitude(double longitude) {
        mLongitude = longitude;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }
}