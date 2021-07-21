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

    //Initializes our bluetooth characteristic,service, and server and then starts advertising
    private fun setupFitnessMachine() {
        val temp3 = intent.extras?.getString("fitness")
        BluetoothAdapter.getDefaultAdapter().setName(temp3)
        mIndoorBikeDataCharacteristic = BluetoothGattCharacteristic(
            bikeUuid.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val temp = BluetoothGattDescriptor(
            clientUuid,
            (BluetoothGattDescriptor.PERMISSION_READ)
        )
        temp.setValue(byteArrayOf(0, 0))
        mIndoorBikeDataCharacteristic.addDescriptor(temp)

        val temp2 = BluetoothGattDescriptor(
            charUuid,
            BluetoothGattDescriptor.PERMISSION_READ
        )
        val stringT = "Speed of the fitness machine in kilometers" +
                " per hour"
        temp2.setValue(stringT.toByteArray(Charset.forName("UTF-8")))
        mIndoorBikeDataCharacteristic.addDescriptor(temp2)

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

    //Broadcasts our Indoor Bike data values to the BLE client
    private fun broadcastData() {
        heartRate = heart_rate.text.toString().toInt()
        instantPower = instant_power.text.toString().toInt()
        val temp3 = instant_cadence.text.toString().toDouble()
        instantCadence = (temp3*2).toInt()
        val temp = instant_speed.text.toString().toDouble()
        instantSpeed = (temp*100).toInt()
        val temp2 = average_speed.text.toString().toDouble()
        averageSpeed = (temp2*100).toInt()

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

    //Disconnects us from the client and stops advertising
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