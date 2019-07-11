package nl.bennekom58.portiekverlichting;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

 /*
    Splashscreen activity which allows the app to initialize connections in the background
    and presents the user with an up-to-date animated interface.
 */

public class SplashActivity extends AppCompatActivity {

    public static boolean WEAR_AVAILABLE = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Custom navbar color for API level 21:+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        // Check if there is a wearable attached to this device
        WEAR_AVAILABLE = checkForWear();
        if (WEAR_AVAILABLE) {

            // Make the watch logo visible on the splash-screen
            ImageView watch_logo = (ImageView) findViewById(R.id.watch_logo);
            watch_logo.setVisibility(View.VISIBLE);
        }

        // Display a hint in Toast message as soon as possible
        Toast.makeText(this, R.string.connecting, Toast.LENGTH_SHORT).show();

        // Initialize and start the application with optional bg-service
        initApplication();
    }

    // Check whether an Android Wear paired with the mobile
    public boolean checkForWear() {
        boolean WEAR_AVAILABLE = false;

        BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.WEARABLE_WRIST_WATCH) {
                    WEAR_AVAILABLE = true;
                }
            }
        }

        return WEAR_AVAILABLE;
    }

    public void initApplication() {
        new SplashActivity.GetClass().execute();
    }

    private class GetClass extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                // Perform a HTTP GET request to download the current state
                URL url = new URL("https://www.bennekom58.nl/cgi-bin/portiek.py");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");

                // Response code of the request
                int responseCode = connection.getResponseCode();

                // Parse the response into a string-variable
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder responseOutput = new StringBuilder();
                while ((line = br.readLine()) != null) { responseOutput.append(line); }
                br.close();
                String initialState = responseOutput.toString();

                // Launch the main interface and optionally the background service
                if (!initialState.isEmpty()) {

                    // Start background service if it's not already running
                    if (!RequestService.ACTIVE && SplashActivity.WEAR_AVAILABLE) {
                        Intent serviceIntent = new Intent(getApplicationContext(), RequestService.class);
                        serviceIntent.putExtra("initialState", initialState);
                        startService(serviceIntent);
                        finish();
                    }

                    // Start main interface, passing some variables
                    Intent mainIntent = new Intent(getApplicationContext(), MainActivity.class);
                    mainIntent.putExtra("initialState", initialState);
                    mainIntent.putExtra("wearAvailable", SplashActivity.WEAR_AVAILABLE);
                    startActivity(mainIntent);
                    finish();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.error + " null@initialState", Toast.LENGTH_SHORT).show();
                    System.exit(0);
                }

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
}
