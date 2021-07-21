package com.example.indoorbikeble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert

const val LOCATION_PERMISSION_REQUEST_CODE = 2
class MainActivity : AppCompatActivity() {

    //Location flag
    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions:Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        when(requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if(grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                }
                else
                {
                    return
                }
            }
        }
    }

    //Asks the user for location permission
    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    //Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        location_button.setOnClickListener {location()}
        bike_button.setOnClickListener {indoorBike()}
        cross_button.setOnClickListener {crossTrainer()}
        tread_button.setOnClickListener {treadmill()}
    }

    //Asks the user for location permission
    private fun location() {
        if(!isLocationPermissionGranted) {
            requestLocationPermission()
        }
    }

    //Starts the Indoor Bike Activity
    private fun indoorBike() {
        if(!isLocationPermissionGranted) {
            requestLocationPermission()
        }
        val intent = Intent(this,BikeStats::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }

    //Starts the Cross Trainer Activity
    private fun crossTrainer() {
        if(!isLocationPermissionGranted) {
            requestLocationPermission()
        }
        val intent = Intent(this,CrossData::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }

    //Starts the Treadmill Activity
    private fun treadmill() {
        if(!isLocationPermissionGranted) {
            requestLocationPermission()
        }
        val intent = Intent(this,TreadmillData::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }
}