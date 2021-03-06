package edu.oregonstate.beaverbus;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jordan_n on 8/21/2014.
 * <p/>
 * CLASS - ShuttleUpdater
 * Contains repeatable logic to repeat the inner poll task
 */
public class ShuttleUpdater {
    private static final String TAG = "ShuttleUpdater";

    private static ShuttleUpdater sShuttleUpdater;
    private static MapState sMapState;
    private OnMapStateUpdate listener;
    private pollNewDataTask currentTask;
    private Handler mHandler;
    private Runnable r;
    private static final long ASYNC_DELAY = 5000;
    private boolean isBusy = false, stop = true;

    private static String shuttleUrl;
    private static String estimatesUrl;

    private static final int NORTH_ROUTE_ID = 7, EAST_ROUTE_ID = 8, WEST_ROUTE_ID = 9, WEST2_ROUTE_ID = 13;

    public interface OnMapStateUpdate {
        void onPostShuttleRequest(boolean success);
    }

    private ShuttleUpdater(OnMapStateUpdate listener) {
        super();
        this.listener = listener;
        sMapState = MapState.get();
        mHandler = new Handler();

        shuttleUrl = sMapState.getShuttleLocationsUrl();
        estimatesUrl = sMapState.getShuttleEstimatesUrl();
    }

    public static ShuttleUpdater get(OnMapStateUpdate listener) {
        if (sShuttleUpdater == null) {
            sShuttleUpdater = new ShuttleUpdater(listener);
        }
        return sShuttleUpdater;
    }

    private void startHandler() {
        r = new Runnable() {
            @Override
            public void run() {
                if (!isBusy) {
                    currentTask = new pollNewDataTask(shuttleUrl);
                    currentTask.execute();
                }
                if (!stop) startHandler();
            }
        };
        mHandler.postDelayed(r, ASYNC_DELAY);

    }

    public void stopShuttleUpdater() {
        stop = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        mHandler.removeCallbacks(r);
    }

    public void startShuttleUpdater() {

        if (stop) {
            stop = false;
            currentTask = new pollNewDataTask(shuttleUrl);
            currentTask.execute();
            startHandler();
        }
    }

    /**
     * CLASS - pollNewDataTask
     * Pulls the shuttle JSON URL.
     * Determines online states of shuttles.
     * Updates locations and estimates
     */
    private class pollNewDataTask extends AsyncTask<String, Void, Boolean> {
        private static final String TAG = "pollNewDataTask";

        private String url;
        private JSONArray JSONShuttles;
        private JSONArray JSONEstimates;

        public pollNewDataTask(String url) {
            super();
            this.url = url;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            JSONGetter getter = new JSONGetter();
            JSONGetter estGetter = new JSONGetter();
            JSONShuttles = getter.getJSONFromUrl(url);
            JSONEstimates = estGetter.getJSONFromUrl(estimatesUrl);

            if (JSONShuttles != null && JSONEstimates != null) {
                return true;
            }
            return false;
        }


        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            parseJSON();
            sMapState.refreshSelectedStopMarker();
            if (!stop) listener.onPostShuttleRequest(success);
        }

        //TODO: reduce number of string conversions here and JSONGetter
        private void parseJSON() {
            Gson gson = new Gson();
            boolean[] onlineStates = {false, false, false, false};

            if (JSONShuttles != null) {
                try {
                    for (int i = 0; i < JSONShuttles.length(); i++) {
                        String json = null;

                        JSONObject rawJson = JSONShuttles.getJSONObject(i);
                        json = rawJson.toString();
                        Shuttle shuttle = gson.fromJson(json, Shuttle.class);
                        shuttle.setOnline(true);

                        switch (shuttle.getRouteID()) {
                            case NORTH_ROUTE_ID:
                                sMapState.setShuttle(0, shuttle);
                                onlineStates[0] = true;
                                break;
                            case WEST_ROUTE_ID:
                                sMapState.setShuttle(1, shuttle);
                                onlineStates[1] = true;
                                break;
                               /* if (sMapState.getShuttles().get(1).getVehicleId() == shuttle.getVehicleId()) {
                                    sMapState.setShuttle(1, shuttle);
                                    onlineStates[1] = true;
                                } else if (sMapState.getShuttles().get(2).getVehicleId() == shuttle.getVehicleId()) {
                                    sMapState.setShuttle(2, shuttle);
                                    onlineStates[2] = true;
                                } else if (!sMapState.getShuttles().get(1).isOnline()) {
                                    sMapState.setShuttle(1, shuttle);
                                    onlineStates[1] = true;
                                } else if (!sMapState.getShuttles().get(2).isOnline()) {
                                    sMapState.setShuttle(2, shuttle);
                                    onlineStates[2] = true;
                                }
                                break;*/
                            case EAST_ROUTE_ID:
                                sMapState.setShuttle(3, shuttle);
                                onlineStates[3] = true;
                                break;
                            case WEST2_ROUTE_ID:
                                sMapState.setShuttle(2, shuttle);
                                onlineStates[2] = true;
                                break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            ArrayList<Shuttle> shuttles = sMapState.getShuttles();

            for (int i = 0; i < 4; i++) {
                Shuttle shuttle = shuttles.get(i);
                if (!onlineStates[i]) shuttle.setOnline(false);
                else shuttle.setOnline(true);
            }

            if (JSONEstimates != null) {
                try {
                    for (int i = 0, len = JSONEstimates.length(); i < len; i++) {
                        JSONObject jsonObj = JSONEstimates.getJSONObject(i);
                        HashMap<Integer, Stop> stopsMap = sMapState.getStopsMap();

                        Integer stopId = jsonObj.getInt("RouteStopID");
                        int routeId = jsonObj.getInt("RouteID");
                        Stop stop = stopsMap.get(stopId);

                        if (stop != null) {
                            JSONArray estimates = jsonObj.getJSONArray("VehicleEstimates");
                            int estimate0[] = {-1, -1};
                            int estimate1[] = {-1, -1};
                            JSONObject estObj0 = estimates.getJSONObject(0);
                            if (estObj0 != null) {
                                if (estObj0.getBoolean("OnRoute")) {
                                    estimate0[0] = estObj0.getInt("VehicleID");
                                    estimate0[1] = estObj0.getInt("SecondsToStop") / 60;
                                    if (estimate0[1] == 0) estimate0[1] = 1;
                                }
                            }
                            if (estimates.length() > 1) {
                                JSONObject estObj1 = estimates.getJSONObject(1);
                                if (estObj1 != null) {
                                    if (estObj1.getBoolean("OnRoute")) {
                                        estimate1[0] = estObj1.getInt("VehicleID");
                                        estimate1[1] = estObj1.getInt("SecondsToStop") / 60;
                                        if (estimate1[1] == 0) estimate1[1] = 1;
                                    }
                                }
                            }

                            switch (routeId) {
                                case NORTH_ROUTE_ID: //North
                                    stop.setShuttleETA(0, estimate0[1]);
                                    break;
                                case EAST_ROUTE_ID: //East
                                    stop.setShuttleETA(3, estimate0[1]);
                                    break;
                                case WEST_ROUTE_ID:   //West
                                    stop.setShuttleETA(1, estimate0[1]);
                                    break;
                                   /* if (shuttles.get(1).getVehicleId() == estimate0[0]) {
                                        stop.setShuttleETA(1, estimate0[1]);
                                    } else if (shuttles.get(1).getVehicleId() == estimate1[0]) {
                                        stop.setShuttleETA(1, estimate1[1]);
                                    }
                                    if (shuttles.get(2).getVehicleId() == estimate0[0]) {
                                        stop.setShuttleETA(2, estimate0[1]);
                                    } else if (shuttles.get(2).getVehicleId() == estimate1[0]) {
                                        stop.setShuttleETA(2, estimate1[1]);
                                    }*/
                                case WEST2_ROUTE_ID:
                                    stop.setShuttleETA(2, estimate0[1]);
                                    break;
                                default:
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}