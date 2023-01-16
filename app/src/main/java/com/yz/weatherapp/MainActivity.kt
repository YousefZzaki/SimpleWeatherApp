package com.yz.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri

import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.yz.weatherapp.Constants.BASE_URL
import com.yz.weatherapp.Constants.METRIC_UNITS
import com.yz.weatherapp.Constants.WEATHER_PREFERENCE_NAME
import com.yz.weatherapp.Constants.WEATHER_RESPONSE_DATA
import com.yz.weatherapp.databinding.ActivityMainBinding
import com.yz.weatherapp.models.WeatherResponse
import com.yz.weatherapp.api.ApiKey.APP_ID
import com.yz.weatherapp.utils.Utils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "TAG"
        private lateinit var progressBar: Dialog
    }

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private lateinit var mSharedPreferences: SharedPreferences

    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        //Set SwipeToRefreshLayout
        binding?.mainLayout?.setOnRefreshListener {
            requestLocationData()
            binding?.mainLayout?.isRefreshing = false
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Set SharedPreferences for the weather
        mSharedPreferences = getSharedPreferences(WEATHER_PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupIU()

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Location is turned off", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        } else {
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "Permissions are denied",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialog()
                }
            }).onSameThread().check()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showRationalDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("The access permission to the location enable it from application settings")
            .setPositiveButton("SETTINGS") { _, _ ->
                try {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    Log.e("uri: ", uri.toString())
                    Log.e("uri package: ", packageName)
                    settingsIntent.data = uri
                    startActivity(settingsIntent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }


    private fun setupIU() {

        val weatherResponseJson = mSharedPreferences.getString(WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJson!!.isNullOrEmpty()){
            val weatherResponse = Gson().fromJson(weatherResponseJson, WeatherResponse::class.java)

            for (item in weatherResponse.weather){
                Log.e("Weather: ", item.toString())
                binding?.tvDescription?.text = item.description
                when(item.icon){
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloudy)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloudy_partly)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloudy)
                    "09d" -> binding?.ivMain?.setImageResource(R.drawable.rainy)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rainy)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloudy)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloudy_partly)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloudy)
                    "09n" -> binding?.ivMain?.setImageResource(R.drawable.rainy)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.rainy)
                }
            }
            val degreeVal = getUnit(weatherResponse)
            binding?.tvMinimum?.text = weatherResponse.main.temp_min.toString() + degreeVal
            binding?.tvMaximum?.text = weatherResponse.main.temp_max.toString() + degreeVal
            binding?.tvHumidity?.text = weatherResponse.main.humidity.toString() + degreeVal
            binding?.tvWindSpeed?.text = weatherResponse.wind.speed.toString()
            binding?.tvCityName?.text = weatherResponse.name
            binding?.tvCountryName?.text = weatherResponse.sys.country
            binding?.tvSunrise?.text = fromUnix(weatherResponse.sys.sunrise)
            binding?.tvSunset?.text = fromUnix(weatherResponse.sys.sunset)
            binding?.tvToday?.text = getCurrentTime()
        }
    }

    private fun getCurrentTime(): String {
        val time = Date()
        val sdf = SimpleDateFormat("EEE, d MMM h:mm a", Locale.getDefault())
        return sdf.format(time)
    }

    private fun fromUnix(timex: Long): String {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("hh:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }


    private fun getUnit(weatherResponse: WeatherResponse): String{
//        val country = application.resources.configuration.locales.toString()
        val country = weatherResponse.sys.country
        var degree = "°C"

        if (country == "US" || country == "UK" || country == "LR" ||country == "MM")
            degree = "°F"

        return degree
    }

    private fun progressBar(dismiss: Boolean = false) {
        if (dismiss) {
            progressBar.dismiss()
        } else {
            progressBar = Dialog(this)
            progressBar.apply{
                setContentView(R.layout.progress_bar)
                setCancelable(false)
                create()
                show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallBack,
            Looper.myLooper()
        )
    }

    private val mLocationCallBack = object : LocationCallback() {
        override  fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val mLatitude = mLastLocation?.latitude
            val mLongitude = mLastLocation?.longitude
            Log.e("LatLon", mLatitude.toString())
            Log.e("LatLon", mLongitude.toString())
            getWeatherData(mLatitude!!, mLongitude!!)
        }
    }

    private  fun getWeatherData(lat: Double, lon: Double) {
        if (Utils.isNetworkAvailable(this)) {
            progressBar()
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                lat,
                lon,
                METRIC_UNITS,
                APP_ID
            )

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()
                        Log.e(TAG, "Weather: $weatherList")

                        val weatherResponseJson = Gson().toJson(weatherList)
                        val sharedPreferencesEditor = mSharedPreferences.edit()
                        sharedPreferencesEditor.putString(WEATHER_RESPONSE_DATA, weatherResponseJson)
                        sharedPreferencesEditor.apply()
                        setupIU()
                    } else {
                        when (response.code()) {
                            404 -> Log.e("Error", "Not found")
                            400 -> Log.e("Error", "Bad connection")
                            else -> Log.e("Error", "Generic error")
                        }
                    }
                    progressBar(true)
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t.message.toString())
                    progressBar(true)
                    throw t
                }
            })

        }
    }
}