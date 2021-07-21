package com.example.indoorbikeble

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import kotlinx.android.synthetic.main.activity_treadmill_data.*
import java.nio.charset.Charset
import java.util.*

class TreadmillData : AppCompatActivity() {
    //Advertising flag
    private var isAdvertising = false
        set(value) {
            field = value
            runOnUiThread {ad_button2.text = if(value) "Stop Advertising" else "Advertise"}
        }

    //Variables for treadmill data fields
    private var incline = 0
    private var instantSpeed = 0
    private var rampAngle = 0
    private var heartRate = 0

    //Uuids for bluetooth connection
    private val fitnessUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"))
    }
    private val treadUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb"))
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
    private val mBluetoothManager: BluetoothManager by lazy {
        getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }
    private var bluetoothDevice: BluetoothDevice? = null
    private lateinit var bluetoothGattService: BluetoothGattService
    private lateinit var mTreadmillDataCharacteristic: BluetoothGattCharacteristic
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
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }

            if (characteristic != null) {
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_SUCCESS,offset,characteristic.value)
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
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }
            mGattServer.sendResponse(device,requestId, BluetoothGatt.GATT_SUCCESS,offset,descriptor?.value)
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
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                } else if(Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor?.setValue(value)
                } else if(supportNotifications && Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor?.setValue(value)
                } else if(supportIndications && Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
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
        mTreadmillDataCharacteristic = BluetoothGattCharacteristic(
            treadUuid.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val temp = BluetoothGattDescriptor(
            clientUuid,
            (BluetoothGattDescriptor.PERMISSION_READ)
        )
        temp.setValue(byteArrayOf(0, 0))
        mTreadmillDataCharacteristic.addDescriptor(temp)

        val temp2 = BluetoothGattDescriptor(
            charUuid,
            BluetoothGattDescriptor.PERMISSION_READ
        )
        val stringT = "Speed of the fitness machine in kilometers" +
                " per hour"
        temp2.setValue(stringT.toByteArray(Charset.forName("UTF-8")))
        mTreadmillDataCharacteristic.addDescriptor(temp2)

        bluetoothGattService = BluetoothGattService(
            fitnessUuid.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        bluetoothGattService.addCharacteristic(mTreadmillDataCharacteristic)
        mGattServer = mBluetoothManager.openGattServer(this,mGattServerCallback)
        mGattServer.addService(bluetoothGattService)
        isAdvertising = true
        advertiser.startAdvertising(settings,adData,mAdvScanResponse,advertisingCallback)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treadmill_data)
        ad_button2.setOnClickListener{
            if(isAdvertising) {
                disconnect()
            } else {
                setupFitnessMachine()
            }
        }
        notify_button2.setOnClickListener{broadcastData()}
    }

    //Broadcasts our Treadmill data values to the BLE client
    private fun broadcastData() {
        heartRate = heart_rate2.text.toString().toInt()
        val temp3 = ramp_angle2.text.toString().toDouble()
        rampAngle = (temp3*10).toInt()
        val temp1 = incline2.text.toString().toDouble()
        incline = (temp1*10).toInt()
        val temp = instant_speed2.text.toString().toDouble()
        instantSpeed = (temp*100).toInt()
        //min and max values
        if(heartRate > 200) {
            heartRate = 200
        } else if(heartRate < 20) {
            heartRate = 20
        }
        if(instantSpeed > 3000) {
            instantSpeed = 3000
        } else if (instantSpeed < 0) {
            instantSpeed = 0
        }
        if(rampAngle > 100) {
            rampAngle = 100
        } else if(rampAngle < 0) {
            rampAngle = 0
        }

        val bytesToSend = ByteArray(9)
        bytesToSend[0] = (0x08).toByte()
        bytesToSend[1] = (0x01).toByte()
        bytesToSend[2] = (instantSpeed and 0xff).toByte()
        bytesToSend[3] = (instantSpeed shr 8 and 0xff).toByte()
        bytesToSend[4] = (incline and 0xff).toByte()
        bytesToSend[5] = (incline shr 8 and 0xff).toByte()
        bytesToSend[6] = (rampAngle and 0xff).toByte()
        bytesToSend[7] = (rampAngle shr 8 and 0xff).toByte()
        bytesToSend[8] = (heartRate and 0xff).toByte()


        mTreadmillDataCharacteristic.setValue(bytesToSend)
        val indicate =(mTreadmillDataCharacteristic.properties == BluetoothGattCharacteristic.PROPERTY_INDICATE)
        mGattServer.notifyCharacteristicChanged(bluetoothDevice,mTreadmillDataCharacteristic,indicate)
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