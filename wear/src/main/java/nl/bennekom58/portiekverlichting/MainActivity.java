package nl.bennekom58.portiekverlichting;

import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

public class MainActivity extends WearableActivity implements
        DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    String currentState;
    ImageView backGround, foreGround;
    ImageButton sendPost;
    RelativeLayout overLay, mainContainer;
    GoogleApiClient googleClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Application keeps the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set up our google Play-Services client
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Connect all of our UI element
        sendPost = (ImageButton) findViewById(R.id.sendPost);
        overLay = (RelativeLayout) findViewById(R.id.overLay);
        mainContainer = (RelativeLayout) findViewById(R.id.mainContainer);
        backGround = (ImageView) findViewById(R.id.backGround);
        foreGround = (ImageView) findViewById(R.id.foreGround);
    }

    // On resuming activity, reconnect Play-Services
    @Override
    public void onResume(){
        super.onResume();
        googleClient.connect();
    }

    // Pause listener, disconnect Play-Services
    @Override
    public void onPause(){
        super.onPause();
        Wearable.DataApi.removeListener(googleClient, this);
        googleClient.disconnect();
    }

    // On successful connection to Play-Services, add data listener
    public void onConnected(Bundle connectionHint) {
        Log.d("debug", "Connected to Play-Services");
        Wearable.DataApi.addListener(googleClient, this);
        // Get the current state from the server
        getAppTheme();
    }

    // On suspended connection, remove Play-Services
    public void onConnectionSuspended(int cause) {
        Log.d("debug", "Connection to Play-Services suspended");
        Wearable.DataApi.removeListener(googleClient, this);
    }

    // On failed connection to Play-Services, remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
        Log.d("debug", "Connection to Play-Services failed");
        Wearable.DataApi.removeListener(googleClient, this);
    }

    // Get current theme from server
    public void getAppTheme() {

        // Bring the loading overLay visible and to the front
        overLay.setVisibility(View.VISIBLE);
        overLay.bringToFront();

        Log.d("debug", "Sending data-request");

        // Start API request to mobile
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/service");
        putDataMapRequest.getDataMap().putString("request", "state");
        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        putDataRequest.setUrgent();

        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleClient, putDataRequest);

        // Check result for possible errors
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
                    Log.d("debug", "Succesfully sent data-reqest");
                } else {
                    Log.d("debug", "Error in sending request to mobile");
                }
            }
        });
    }

    // Set app theme with string
    public void setAppTheme(String state) {
        switch (state) {
            case "AANset":
                backGround.setImageResource(R.drawable.bg_night);
                sendPost.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_on));
                foreGround.setImageResource(R.drawable.fg_night_on);
                break;
            case "AANrise":
                backGround.setImageResource(R.drawable.bg_day);
                sendPost.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_on));
                foreGround.setImageResource(R.drawable.fg_day);
                break;
            case "UITset":
                backGround.setImageResource(R.drawable.bg_night);
                sendPost.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_off));
                foreGround.setImageResource(R.drawable.fg_night_off);
                break;
            case "UITrise":
                backGround.setImageResource(R.drawable.bg_day);
                sendPost.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_off));
                foreGround.setImageResource(R.drawable.fg_day);
                break;
            default:
                Toast.makeText(getApplicationContext(), "ERROR setAppTheme", Toast.LENGTH_LONG).show();
        }
    }

    // Function triggered every time there is a data-change event
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d("debug", "Data-change event triggered");
        for(DataEvent event: dataEvents){

            // Data item changed
            if(event.getType() == DataEvent.TYPE_CHANGED){
                DataItem item = event.getDataItem();
                DataMapItem dataMapItem = DataMapItem.fromDataItem(item);

                // Response back from mobile message
                if(item.getUri().getPath().equals("/wear")){

                    // Turn overlay off and bring main content back to front
                    overLay.setVisibility(View.INVISIBLE);
                    mainContainer.bringToFront();

                    // Collect all of our info
                    String error = dataMapItem.getDataMap().getString("error");
                    String state = dataMapItem.getDataMap().getString("state");

                    // Success
                    if(error == null && !state.isEmpty()){
                        Log.d("debug","Succes=" + state);
                        currentState = state;
                        setAppTheme(currentState);
                    }
                    // Error
                    else {
                        Log.d("debug","Error=" + error);
                    }

                }
            }
        }
    }

    public void sendPost(View view) {

        // Bring the loading overLay visible and to the front
        overLay.setVisibility(View.VISIBLE);
        overLay.bringToFront();

        // Click listener for the overLay, touch to dismiss it in case the API fails or takes too long
        overLay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overLay.setVisibility(View.INVISIBLE);
                mainContainer.bringToFront();
            }
        });

        Log.d("debug", "Sending post-request");

        // Start API request to mobile
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/service");
        putDataMapRequest.getDataMap().putString("request", "switch");
        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        putDataRequest.setUrgent();

        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleClient, putDataRequest);

        // Check result for possible errors
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (!dataItemResult.getStatus().isSuccess()) {
                    Log.d("debug", "Error in sending request to mobile");
                }
            }
        });
    }
}
