package com.example.indoorbikeble

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_bike_stats.*
import java.nio.charset.Charset
import java.util.*

/**
 * Class for Indoor Bike activity
 */
class BikeStats : AppCompatActivity() {
    //Advertising flag
    private var isAdvertising = false
        set(value) {
            field = value
            runOnUiThread {ad_button1.text = if(value) "Stop Advertising" else "Advertise"}
        }

    //Variables for indoor bike data fields
    private var instantPower = 0
    private var instantSpeed = 0
    private var averageSpeed = 0
    private var instantCadence = 0
    private var heartRate = 0

    //Uuids for bluetooth connection
    private val fitnessUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"))
    }
    private val bikeUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb"))
    }
    private val charUuid = UUID
        .fromString("00002901-0000-1000-8000-00805f9b34fb")
    private val clientUuid = UUID
        .fromString("00002902-0000-1000-8000-00805f9b34fb")


    //Advertising variables
    private val advertiser: BluetoothLeAdvertiser by lazy {
        BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser
    }
    private val settings: AdvertiseSettings by lazy {
        AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
    }
    private val adData: AdvertiseData by lazy {
        AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(fitnessUuid)
            .addServiceData(fitnessUuid, "Data".toByteArray(Charset.forName("UTF-8")))
            .build()
    }
    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
        override fun onStartFailure(errorCode:Int) {
            isAdvertising = false
            Log.e("Advertising","Failed")
        }
    }
    private val mAdvScanResponse: AdvertiseData by lazy {
        AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .build()
    }


    //Bluetooth variables
    private val mBluetoothManager:BluetoothManager by lazy {
        getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }
    private var bluetoothDevice: BluetoothDevice? = null
    private lateinit var bluetoothGattService: BluetoothGattService
    private lateinit var mIndoorBikeDataCharacteristic: BluetoothGattCharacteristic
    private lateinit var mGattServer: BluetoothGattServer
    private val mGattServerCallback = object: BluetoothGattServerCallback() {
        /**
         * If the connection state changes to connected, we want to create an object of the device it is connected to
         * Else(when the connection state changes to disconnected), we want to get rid of the object of the device
         * that we used to be connected to
         * @param [device] object of the device that changed connection states with us
         * @param [status] indicates of we are connected or not
         * @param [newState] indicates the new connection state we are in
         */
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
                if(status == BluetoothGatt.GATT_SUCCESS) {
                if(newState == BluetoothGatt.STATE_CONNECTED) {
                    bluetoothDevice = device
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    bluetoothDevice = null
                }
            } else {
                bluetoothDevice = null
            }
        }
        /**
         * On read request, we want to send over the values of our characteristic
         * @param [device] object of the device we are connected to
         * @param [requestId] Id of the request
         * @param [characteristic] the characteristic the client is trying to read
         * @param [offset] Offset into the value of the characteristic
         */
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if(offset != 0) {
                mGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }

            if (characteristic != null) {
                mGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,offset,characteristic.value)
            }
        }
        /**
         * On descriptor read request, we want to send the value of the descriptor to the client that is requesting
         *
         * @param [device] object of the device that we are connected to
         * @param [requestId] Id of the request
         * @param [offset] Offset into the value of the characteristic
         * @param [descriptor] The descriptor that the client wants to read
         */
        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            if(offset != 0) {
                mGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }
            mGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,offset,descriptor?.value)
        }
        /**
         * the client has requested to write to the descriptor, so we send our response to that request
         * @param [device] object of the device we are connected to
         * @param [requestId] Id of the request
         * @param [descriptor] the descriptor that the client is trying to write to
         * @param [preparedWrite] true if the write operation should be queued for later
         * @param [responseNeeded] true if the client requires a response
         * @param [offset] Offset into the given value
         * @param [value] the value that the client wants to assign to the descriptor
         */
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            var status = BluetoothGatt.GATT_SUCCESS
            if(descriptor?.uuid == clientUuid) {
                val char =descriptor?.characteristic
                val supportNotifications =(char?.properties != 0 && BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                val supportIndications = (char?.properties != 0 && BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                if(!(supportNotifications || supportIndications)) {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                } else if (value?.size != 2) {
                    status =BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                } else if(Arrays.equals(value,BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor?.setValue(value)
                } else if(supportNotifications && Arrays.equals(value,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor?.setValue(value)
                } else if(supportIndications && Arrays.equals(value,BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor?.setValue(value)
                } else {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                }
            } else {
                status = BluetoothGatt.GATT_SUCCESS
                descriptor?.setValue(value)
            }
            if(responseNeeded) {
                mGattServer.sendResponse(device,requestId,status,0,null)
            }
        }
    }

    /**
     * Initializes our bluetooth characteristic,service, and server and then starts advertising
     */
    private fun setupFitnessMachine() {
        val deviceName = intent.extras?.getString("fitness")
        BluetoothAdapter.getDefaultAdapter().setName(deviceName)
        mIndoorBikeDataCharacteristic = BluetoothGattCharacteristic(
            bikeUuid.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val clientDescriptor = BluetoothGattDescriptor(
            clientUuid,
            (BluetoothGattDescriptor.PERMISSION_READ)
        )
        clientDescriptor.setValue(byteArrayOf(0, 0))
        mIndoorBikeDataCharacteristic.addDescriptor(clientDescriptor)

        val charDescriptor = BluetoothGattDescriptor(
            charUuid,
            BluetoothGattDescriptor.PERMISSION_READ
        )
        val stringT = "Data for Indoor Bike"
        charDescriptor.setValue(stringT.toByteArray(Charset.forName("UTF-8")))
        mIndoorBikeDataCharacteristic.addDescriptor(charDescriptor)

        bluetoothGattService = BluetoothGattService(
            fitnessUuid.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        bluetoothGattService.addCharacteristic(mIndoorBikeDataCharacteristic)
        mGattServer = mBluetoothManager.openGattServer(this,mGattServerCallback)
        mGattServer.addService(bluetoothGattService)
        isAdvertising = true
        advertiser.startAdvertising(settings,adData,mAdvScanResponse,advertisingCallback)
    }
    /**
     * Creates the Indoor Bike activity and sets the onClick functions for the buttons
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bike_stats)
        ad_button1.setOnClickListener{
            if(isAdvertising) {
                disconnect()
            } else {
                setupFitnessMachine()
            }
        }
        notify_button1.setOnClickListener{broadcastData()}
    }

    /**
     * Broadcasts our Indoor Bike data values to the BLE client
     * Does this by adding the values to the Treadmill characteristic and then sends it
     * over to the client through the mGattServer
     */
    private fun broadcastData() {
        heartRate = heart_rate.text.toString().toInt()
        instantPower = instant_power.text.toString().toInt()
        val instantCadenceDouble = instant_cadence.text.toString().toDouble()
        instantCadence = (instantCadenceDouble*2).toInt()
        val instantSpeedDouble = instant_speed.text.toString().toDouble()
        instantSpeed = (instantSpeedDouble*100).toInt()
        val averageSpeedDouble = average_speed.text.toString().toDouble()
        averageSpeed = (averageSpeedDouble*100).toInt()

        //min and max values
        if(heartRate > 200) {
            heartRate = 200
        } else if(heartRate < 20) {
            heartRate = 20
        }

        if(instantSpeed > 10000) {
            instantSpeed = 10000
        } else if (instantSpeed < 0) {
            instantSpeed = 0
        }

        if(averageSpeed > 10000) {
            averageSpeed = 10000
        } else if (averageSpeed < 0) {
            averageSpeed = 0
        }

        if(instantCadence > 400) {
            instantCadence = 400
        } else if(instantCadence < 0) {
            instantCadence = 0
        }

        if(instantPower > 2000) {
            instantPower = 2000
        } else if(instantPower < 0) {
            instantPower = 0
        }

        val bytesToSend = ByteArray(11)
        bytesToSend[0] = (0x46).toByte()
        bytesToSend[1] = (0x02).toByte()
        bytesToSend[2] = (instantSpeed and 0xff).toByte()
        bytesToSend[3] = (instantSpeed shr 8 and 0xff).toByte()
        bytesToSend[4] = (averageSpeed and 0xff).toByte()
        bytesToSend[5] = (averageSpeed shr 8 and 0xff).toByte()
        bytesToSend[6] = (instantCadence and 0xff).toByte()
        bytesToSend[7] = (instantCadence shr 8 and 0xff).toByte()
        bytesToSend[8] = (instantPower and 0xff).toByte()
        bytesToSend[9] = (instantPower shr 8 and 0xff).toByte()
        bytesToSend[10] = (heartRate and 0xff).toByte()

        mIndoorBikeDataCharacteristic.setValue(bytesToSend)
        val indicate =(mIndoorBikeDataCharacteristic.properties == BluetoothGattCharacteristic.PROPERTY_INDICATE)
        mGattServer.notifyCharacteristicChanged(bluetoothDevice,mIndoorBikeDataCharacteristic,indicate)
    }

    /**
     * Disconnects us from the client and stops advertising
     */
    private fun disconnect() {
        if(bluetoothDevice != null) {
            mGattServer.cancelConnection(bluetoothDevice)
            bluetoothDevice = null
        }
        if(isAdvertising) {
            advertiser.stopAdvertising(advertisingCallback)
            isAdvertising = false
        }
    }
}