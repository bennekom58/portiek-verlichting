package nl.bennekom58.portiekverlichting;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

 /*
    Background-service class for handling communications between the apps and server.
    No matter it's the handheld or wearable app, it requests from this service to
    update or switch the current state of the porch lights.
 */

public class MainActivity extends AppCompatActivity {

    private String currentState = "";
    public static boolean WEAR_AVAILABLE;
    private ProgressDialog progress;
    ImageView bgLayout, fgLayout;
    Animation uptodown,downtoup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup the title- and actionbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.app_name));
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);

        // Custom NavBar color for API levels 21:+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        // Get the 'initialState' string from the SplashActivity
        Intent intent = getIntent();
        String initialState = intent.getStringExtra("initialState");
        WEAR_AVAILABLE = intent.getBooleanExtra("wearAvailable", false);

        // Apply the theme according to the received 'currentState'
        setAppTheme(initialState);

        // Setup background animations
        bgLayout = (ImageView) findViewById(R.id.backGround);
        fgLayout = (ImageView) findViewById(R.id.foreGround);
        uptodown = AnimationUtils.loadAnimation(this,R.anim.uptodown);
        downtoup = AnimationUtils.loadAnimation(this,R.anim.downtoup);
        bgLayout.setAnimation(uptodown);
        fgLayout.setAnimation(downtoup);
    }

    @Override
    protected void onResume() {

        // Check if bg-service is running, launch if not
        if (WEAR_AVAILABLE && !RequestService.ACTIVE) {
            startService(new Intent(getApplicationContext(), RequestService.class));
            Toast.makeText(this, "Achtergrond service herstarten", Toast.LENGTH_SHORT).show();
        }

        // Update the state if necessary
        if (currentState.isEmpty()) {
            updateAppTheme();
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        currentState = "";

        // No need for the bg-service when there is no watch paired
        if (!WEAR_AVAILABLE && RequestService.ACTIVE) {
            stopService(new Intent(this, RequestService.class));
        }

        super.onPause();
    }

    // ActionBar builder function
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.items,menu);

        if (menu instanceof MenuBuilder) {
            MenuBuilder menuBuilder = (MenuBuilder) menu;
            menuBuilder.setOptionalIconsVisible(true);
        }

        return true;
    }

    // ActionBar handler function
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.HELP:

                // Display a 'help' toast notification pointing towards the button...
                Toast.makeText(getApplicationContext(),R.string.help_text + " >>>",Toast.LENGTH_SHORT).show();

                stopService(new Intent(getApplicationContext(), RequestService.class));

                return true;

            default:
                super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Set app theme according to parameter (AANset, AANrise, UITset, UITrise)
    public void setAppTheme(String state) {
        ImageButton myImageButton = (ImageButton) findViewById(R.id.sendPost);
        ImageView myImageView1 = (ImageView) findViewById(R.id.backGround);
        ImageView myImageView2 = (ImageView) findViewById(R.id.foreGround);

        switch (state) {
            case "AANset":
                myImageView1.setImageResource(R.drawable.bg_night);
                myImageButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_on));
                myImageView2.setImageResource(R.drawable.fg_night_on);
                break;
            case "AANrise":
                myImageView1.setImageResource(R.drawable.bg_day);
                myImageButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_on));
                myImageView2.setImageResource(R.drawable.fg_day);
                break;
            case "UITset":
                myImageView1.setImageResource(R.drawable.bg_night);
                myImageButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_off));
                myImageView2.setImageResource(R.drawable.fg_night_off);
                break;
            case "UITrise":
                myImageView1.setImageResource(R.drawable.bg_day);
                myImageButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lightbulb_off));
                myImageView2.setImageResource(R.drawable.fg_day);
                break;
            default:
                Toast.makeText(getApplicationContext(), R.string.error_set_theme, Toast.LENGTH_SHORT).show();
        }
    }

    // Function to switch between day/night themes
    public void switchAppTheme() {
        switch (currentState) {
            case "AANset":
                currentState = "UITset";
                setAppTheme(currentState);
                break;
            case "AANrise":
                currentState = "UITrise";
                setAppTheme(currentState);
                break;
            case "UITset":
                currentState = "AANset";
                setAppTheme(currentState);
                break;
            case "UITrise":
                currentState = "AANrise";
                setAppTheme(currentState);
                break;
        }
    }

    // Sending switch request to the server, then receive new updated state back
    public void sendPostRequest() {
        new PostClass(this).execute();
    }

    private class PostClass extends AsyncTask<String, Void, Void> {
        private final Context context;

        private PostClass(Context c){
            this.context = c;
        }

        protected void onPreExecute(){
            // Create a progress dialog during the request
            progress= new ProgressDialog(this.context);
            progress.setMessage(context.getResources().getString(R.string.progress_dialog_switching));
            progress.show();
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                URL url = new URL("https://www.bennekom58.nl/cgi-bin/portiek.py");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                String urlParameters = "portiek=schakel";
                connection.setRequestMethod("POST");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");
                connection.setDoOutput(true);
                DataOutputStream dStream = new DataOutputStream(connection.getOutputStream());
                dStream.writeBytes(urlParameters);
                dStream.flush();
                dStream.close();
                int responseCode = connection.getResponseCode();

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switchAppTheme();  // Switch application theme
                        progress.dismiss();  // Dismiss the progress dialog
                    }
                });
            } catch (MalformedURLException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " MalformedURLException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " IOException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute() {
            // Make sure progress is dismissed even when request fails
            progress.dismiss();
        }
    }

    // Get 'currentState' from server
    public void updateAppTheme() {
        new GetClass(this).execute();
    }

    private class GetClass extends AsyncTask<String, Void, Void> {
        private final Context context;

        private GetClass(Context c){
            this.context = c;
        }

        protected void onPreExecute(){
            progress= new ProgressDialog(this.context);
            progress.setMessage(context.getResources().getString(R.string.progress_dialog_updating));
            progress.show();
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                // Prepare HTTP GET request
                URL url = new URL("https://www.bennekom58.nl/cgi-bin/portiek.py");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
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
                currentState = responseOutput.toString();

                // Initiate the background change on the main interface
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setAppTheme(currentState);

                        // Finally dismiss the progress dialog
                        progress.dismiss();
                    }
                });

            } catch (MalformedURLException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " MalformedURLException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), R.string.error + " IOException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return null;
        }

        // Just to make sure that progress dialog is dismissed
        protected void onPostExecute() {
            progress.dismiss();
        }
    }
}
