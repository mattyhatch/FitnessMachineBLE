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

/**
 * Class for main activity (first page of the app)
 */
const val LOCATION_PERMISSION_REQUEST_CODE = 2
class MainActivity : AppCompatActivity() {

    //Location flag
    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)


    /**
     *  This function helps determine if the app has been granted access to a certain permission
     *
     * @param [permissionType] type of permission that is being checked
     * @return Returns true when [permissionType] is granted, returns false when it is not granted
     */

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Function override when they pick their option for location permission
     * If location permission is granted, do nothing, else ask for location permission again
     *
     * @param [requestCode] Lets us know if it was a location permission request or not
     * @param [grantResults] Lets us know if the permission was granted or not
     */
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

    /**
     * If location permission is granted, it does nothing, else it asks for location permission
     */
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

    /**
     * asks the user for permission for something(in our case, it will be location)
     * @param [permission] lets the system know what permission we are asking for
     * @param [requestCode] lets us know what permission we are asking for in the callback function
     */
    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    /**
     * Sets up our buttons with their respective onClick functions
     * Also sets the view for the main activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        location_button.setOnClickListener {location()}
        bike_button.setOnClickListener {indoorBike()}
        cross_button.setOnClickListener {crossTrainer()}
        tread_button.setOnClickListener {treadmill()}
    }

    /**
     * Checks if the user has location permission, if they do not, it asks them for permission
     */
    private fun location() {
        if(!isLocationPermissionGranted) {
            requestLocationPermission()
        }
    }

    /**
     * starts the indoorBike activity
     */
    private fun indoorBike() {
        location()
        val intent = Intent(this,BikeStats::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }

    /**
     * starts the Cross Trainer activity
     */
    private fun crossTrainer() {
        location()
        val intent = Intent(this,CrossData::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }

    /**
     * starts the Treadmill activity
     */
    private fun treadmill() {
        location()
        val intent = Intent(this,TreadmillData::class.java)
        intent.putExtra("fitness",fitness.text.toString())
        startActivity(intent)
    }
}