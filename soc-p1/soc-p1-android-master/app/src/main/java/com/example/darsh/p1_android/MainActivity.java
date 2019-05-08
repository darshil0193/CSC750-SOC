package com.example.darsh.p1_android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.TextView;
import android.widget.Toast;

// Ref: https://developer.android.com/studio/intro/
// Useful in general

public class MainActivity extends AppCompatActivity {

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Button button;
    private EditText username;
    private EditText host;
    private TextView totalDistanceTravelled;
    private TextView nextTimeIntervalValue;
    private TextView averageSpeedValue;
    private boolean userRequest = false;
    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        final Context context = this;
        button = findViewById(R.id.start_button);
        host = findViewById(R.id.host);
        username = findViewById(R.id.username);

        // Ref: https://www.youtube.com/watch?v=QNb_3QKSmMk
        // For working with locationManager and locationListener
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
               makeServiceRequest(location.getLatitude(), location.getLongitude());
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast toast = Toast.makeText(context, "Not enough permission", Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                locationManager.removeUpdates(this);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET
                }, 10);
            } else {
                configureButton();
            }
        } else {
            configureButton();
        }
    }

    private void init() {
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    return;
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    configureButton();
        }
    }

    private void configureButton() {
        button.setOnClickListener(new View.OnClickListener() {
            private Context context = getApplicationContext();

            @Override
            public void onClick(View v) {
                if(button.getText().toString().equals(getResources().getString(R.string.start))) {
                    userRequest = true;
                    button.setText(R.string.stop);
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Toast toast = Toast.makeText(context, "Not enough permission", Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                } else {
                    userRequest = false;
                    handler.removeCallbacks(runnable);
                    button.setText(R.string.start);
                }
            }
        });
    }

    private void makeServiceRequest(Double latitude, Double longitude) {
        // Ref: https://developer.android.com/training/volley/request
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        final String url = "http://" + host.getText().toString() + "/locationupdate";
        HashMap<String, String> params = new HashMap<String, String>();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date today = Calendar.getInstance().getTime();
        String currentDate = df.format(today);

        params.put("username", username.getText().toString());
        params.put("currentTimestamp", currentDate);
        params.put("latitude", Double.toString(latitude));
        params.put("longitude", Double.toString(longitude));


        JsonObjectRequest postRequest = new JsonObjectRequest(Request.Method.POST, url, new JSONObject(params), new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d("dpatelResponse", response.toString());
                try {
                    final Double totalDistanceTillNow = response.getDouble("totalDistanceTillNow");
                    final long updateInterval = response.getLong("updateInterval");
                    final Double updateIntervalDouble = (double)updateInterval/1000;

                    totalDistanceTravelled = findViewById(R.id.totalDistanceValue);
                    totalDistanceTravelled.setText(String.format("%.2f m", totalDistanceTillNow));

                    nextTimeIntervalValue = findViewById(R.id.nextTimeIntervalValue);
                    nextTimeIntervalValue.setText(String.format("%s s", updateIntervalDouble));

                    averageSpeedValue = findViewById(R.id.averageSpeedValue);
                    averageSpeedValue.setText(String.format("%s m/s", response.getDouble("averageSpeed")));

                    if (userRequest) {
                        handler.postDelayed(runnable, updateInterval);
                    }else{
                        handler.removeCallbacks(runnable);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast toast = Toast.makeText(getApplicationContext(), "Not enough permission", Toast.LENGTH_SHORT);
                    toast.show();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getApplicationContext(), "Server Error", Toast.LENGTH_LONG).show();
                Log.d("dpatelResponse", "onErrorResponse: " + error.getMessage());
            }
        });

        queue.add(postRequest);
    }
}
