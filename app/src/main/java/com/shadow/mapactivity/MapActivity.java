package com.shadow.mapactivity;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.button.MaterialButton;
import com.mancj.materialsearchbar.MaterialSearchBar;
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MapActivity";

    private GoogleMap mGoogleMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;

    private PlacesClient mPlacesClient;
    private List<AutocompletePrediction> mPredictionList;

    private Location mLastKnownLocation;
    private LocationCallback mLocationCallback;

    private MaterialSearchBar mMaterialSearchBar;
    private View mMap;
    private MaterialButton addMarkerButton;
    private final float DEFAULT_ZOOM = 18;
    private final int RESOLVABLE_API_EXCEPTION_RC = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
        } else {
            startActivity(new Intent(this, PermissionsActivity.class));
        }

        //Register the Views
        mMaterialSearchBar = findViewById(R.id.map_search_bar);
        addMarkerButton = findViewById(R.id.add_marker_btn);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mMap = mapFragment.getView();

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        Places.initialize(this, getString(R.string.google_api_key));
        mPlacesClient = Places.createClient(this);

        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

        mMaterialSearchBar.setOnSearchActionListener(new MaterialSearchBar.OnSearchActionListener() {
            @Override
            public void onSearchStateChanged(boolean enabled) {

            }

            @Override
            public void onSearchConfirmed(CharSequence text) {
                startSearch(text.toString(), true, null, true);
            }

            @Override
            public void onButtonClicked(int buttonCode) {
                if (buttonCode == MaterialSearchBar.BUTTON_NAVIGATION) {
                    //Open Navigation Drawer
                } else if (buttonCode == MaterialSearchBar.BUTTON_BACK) {
                    mMaterialSearchBar.closeSearch();
                }
            }
        });

        mMaterialSearchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                FindAutocompletePredictionsRequest predictionsRequest = FindAutocompletePredictionsRequest.builder().setCountry("ke")
                        .setTypeFilter(TypeFilter.ADDRESS)
                        .setSessionToken(token)
                        .setQuery(s.toString())
                        .build();

                mPlacesClient.findAutocompletePredictions(predictionsRequest).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FindAutocompletePredictionsResponse predictionsResponse = task.getResult();

                        if (predictionsResponse != null) {
                            mPredictionList = predictionsResponse.getAutocompletePredictions();

                            List<String> locationsList = new ArrayList<>();


                            /*
                            mPredictionList.forEach(autocompletePrediction -> {
                                locationsList.add(autocompletePrediction.getFullText(null).toString());
                            }); */

                            for (AutocompletePrediction prediction : mPredictionList)
                                locationsList.add(prediction.getFullText(null).toString());

                            mMaterialSearchBar.updateLastSuggestions(locationsList);

                            if (!mMaterialSearchBar.isSuggestionsVisible()) {
                                mMaterialSearchBar.showSuggestionsList();
                            }
                        }

                    } else {

                        Log.e(TAG, "onTextChanged: prediction fetch unsuccessful", task.getException());
                    }
                });
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mMaterialSearchBar.setSuggestionsClickListener(new SuggestionsAdapter.OnItemViewClickListener() {
            @Override
            public void OnItemClickListener(int position, View v) {
                if (position > mPredictionList.size()) {
                    return;
                }

                AutocompletePrediction selectedPrediction = mPredictionList.get(position);

                String suggestion = mMaterialSearchBar.getLastSuggestions().get(position).toString();
                mMaterialSearchBar.setText(suggestion);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mMaterialSearchBar.clearSuggestions();
                    }
                }, 1000);

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

                if (imm != null)
                    imm.hideSoftInputFromWindow(mMaterialSearchBar.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

                String placeId = selectedPrediction.getPlaceId();

                List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG);

                FetchPlaceRequest fetchPlaceRequest = FetchPlaceRequest.builder(placeId, placeFields).build();

                mPlacesClient.fetchPlace(fetchPlaceRequest).addOnSuccessListener(fetchPlaceResponse -> {

                    Place place = fetchPlaceResponse.getPlace();
                    Log.i(TAG, "OnItemClickListener: Place found: Name - " + place.getName());

                    LatLng latLng = place.getLatLng();

                    if (latLng != null) {
                        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));
                    }

                }).addOnFailureListener(e -> {

                    if (e instanceof ApiException) {
                        ApiException apiException = (ApiException) e;

                        apiException.printStackTrace();

                        Log.i(TAG, "OnItemClickListener: Place not found");
                    } else {

                        Log.e(TAG, "OnItemClickListener: fetching material selected place", e);
                    }

                });
            }

            @Override
            public void OnItemDeleteListener(int position, View v) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        //Basic Setup and Init
        mGoogleMap = googleMap;
        mGoogleMap.setMyLocationEnabled(true);
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);

        //Moving the current location to the bottom
        if ((mMap != null) && (mMap.findViewById(Integer.parseInt("1")) != null)) {

            View locationButton = ((View) mMap.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));

            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 48, 96);
        }

        //Check GPS and Enable it
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);

        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            getDeviceLocation();
        }).addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                try {
                    resolvableApiException.startResolutionForResult(this, RESOLVABLE_API_EXCEPTION_RC);
                } catch (IntentSender.SendIntentException ex) {
                    ex.printStackTrace();
                    Log.e(TAG, "onMapReady: resolving an api exception", ex);
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESOLVABLE_API_EXCEPTION_RC) {
            if (resultCode == RESULT_OK) {
                getDeviceLocation();
            }
        }
    }

    private void getDeviceLocation() {
        mFusedLocationProviderClient.getLastLocation()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mLastKnownLocation = task.getResult();

                        if (mLastKnownLocation != null) {
                            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d(TAG, "getDeviceLocation: mLastKnownLocation is null");
                            LocationRequest locationRequest = LocationRequest.create();
                            locationRequest.setInterval(10000);
                            locationRequest.setFastestInterval(5000);
                            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                            mLocationCallback = new LocationCallback() {
                                @Override
                                public void onLocationResult(LocationResult locationResult) {
                                    super.onLocationResult(locationResult);

                                    if (locationResult == null) {
                                        return;
                                    }

                                    mLastKnownLocation = locationResult.getLastLocation();
                                    mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                    mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
                                }
                            };

                            mFusedLocationProviderClient.requestLocationUpdates(locationRequest, mLocationCallback, null);
                        }
                    } else {
                        Log.e(TAG, "getDeviceLocation: taskFailed", task.getException());
                    }
                });
    }
}
