package nl.bennekom58.portiekverlichting;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class RequestService extends Service implements
        DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    GoogleApiClient googleClient;
    public static boolean ACTIVE = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String currentState = "";

        // Mark the service as active
        ACTIVE = true;

        // Get the initial state from the SplashActivity
        if (intent.hasExtra("initialState")) {
            currentState = intent.getStringExtra("initialState");
        } else {

            // Otherwise update it here
            updateState();
        }

        // Setup the a Play-Services client
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Connect client to the Play-Services
        googleClient.connect();

        sendState(currentState);

        // Keep service running in the background
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Mark the service inactive
        ACTIVE = false;

        // Disconnect the client from Play-Services
        Wearable.DataApi.removeListener(googleClient, this);
        googleClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Report that there is no binding available to this service
        return null;
    }

    // On successful connection to Play-Services add data listener
    public void onConnected(Bundle connectionHint) {
        Wearable.DataApi.addListener(googleClient, this);
    }

    // On suspended connection, remove Play-Services
    public void onConnectionSuspended(int cause) {
        Wearable.DataApi.removeListener(googleClient, this);
    }

    // On failed connection to Play-Services remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
        Wearable.DataApi.removeListener(googleClient, this);
    }

    // Function watching for data-event from Play-Services
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event: dataEvents){

            // Data item changed
            if (event.getType() == DataEvent.TYPE_CHANGED){
                DataItem item = event.getDataItem();
                DataMapItem dataMapItem = DataMapItem.fromDataItem(item);

                // Check if it is the right path
                if (item.getUri().getPath().equals("/service")){

                    // Extract and evaluate the message
                    String request = dataMapItem.getDataMap().getString("request");

                    switch (request) {
                        case "state":
                            updateState();
                            break;
                        case "switch":
                            switchState();
                            break;
                    }
                }
            }
        }
    }

    // Function to launch switch class
    public void switchState() {
        new RequestService.PostClass().execute();
    }

    // Class to switch porch lights
    private class PostClass extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                // Prepare HTTP POST request
                URL url = new URL("https://www.bennekom58.nl/cgi-bin/portiek.py");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                String urlParameters = "portiek=schakel";
                connection.setRequestMethod("POST");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");
                connection.setDoOutput(true);

                // Make the request
                DataOutputStream dStream = new DataOutputStream(connection.getOutputStream());
                dStream.writeBytes(urlParameters);
                dStream.flush();
                dStream.close();

                // Transaction response code (200 = OK, rest is not :)
                int responseCode = connection.getResponseCode();

                // Process the response output
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder responseOutput = new StringBuilder();
                while ((line = br.readLine()) != null) { responseOutput.append(line); }
                br.close();
                String req_result = responseOutput.toString();

                // Send the updated state back to the applications
                sendState(req_result);
                // TODO report state also to the main app on handheld

            } catch (MalformedURLException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " MalformedURLException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " IOException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return null;
        }
    }

    // Function to launch update class
    public void updateState() {
        new RequestService.GetClass().execute();
    }

    // Class to update the current state
    private class GetClass extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                // Prepare HTTP GET request
                URL url = new URL("https://www.bennekom58.nl/cgi-bin/portiek.py");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");

                // Transaction response code (200 = OK, rest is not :)
                int responseCode = connection.getResponseCode();

                // Process the response output
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder responseOutput = new StringBuilder();
                while ((line = br.readLine()) != null) { responseOutput.append(line); }
                br.close();
                String req_result = responseOutput.toString();

                // Send the updated state back to the applications
                sendState(req_result);
                // TODO report state also to the main app on handheld

            } catch (MalformedURLException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " MalformedURLException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " IOException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            return null;
        }
    }

    // Function to send current state to apps
    public void sendState(String state) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/wear");
        putDataMapRequest.getDataMap().putString("state", state);

        // Time is required, otherwise request is dropped as duplicate.
        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();

        // Send it immediately
        putDataRequest.setUrgent();

        // Sending the request
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleClient, putDataRequest);

        // Check result for possible errors
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (!dataItemResult.getStatus().isSuccess()) {
                    Toast.makeText(getApplicationContext(), R.string.error_comm_wear, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
