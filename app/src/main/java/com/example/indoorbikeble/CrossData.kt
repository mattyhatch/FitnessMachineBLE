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
import kotlinx.android.synthetic.main.activity_cross_data2.*
import java.nio.charset.Charset
import java.util.*

class CrossData : AppCompatActivity() {
    //Advertising flag
    private var isAdvertising = false
        set(value) {
            field = value
            runOnUiThread {ad_button.text = if(value) "Stop Advertising" else "Advertise"}
        }

    //Variables for cross trainer data fields
    private var heartRate = 0
    private var instantSpeed = 0
    private var resistanceLevel = 0
    private var instantPower = 0
    private var steps = 0
    private var rampAngle = 0
    private var incline = 0

    //Uuids for bluetooth connection
    private val fitnessUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"))
    }
    private val crossUuid: ParcelUuid by lazy {
        ParcelUuid(UUID.fromString("00002ACE-0000-1000-8000-00805f9b34fb"))
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
    private lateinit var mCrossTrainerDataCharacteristic: BluetoothGattCharacteristic
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
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }

            if (characteristic != null) {
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_SUCCESS,offset,characteristic.value)
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
                mGattServer.sendResponse(device,requestId,
                    BluetoothGatt.GATT_INVALID_OFFSET,offset,null)
                return
            }
            mGattServer.sendResponse(device,requestId, BluetoothGatt.GATT_SUCCESS,offset,descriptor?.value)
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

    /**
     * Initializes our bluetooth characteristic,service, and server and then starts advertising
     */
    private fun setupFitnessMachine() {
        val deviceName = intent.extras?.getString("fitness")
        BluetoothAdapter.getDefaultAdapter().setName(deviceName)
        mCrossTrainerDataCharacteristic = BluetoothGattCharacteristic(
            crossUuid.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val clientDescriptor = BluetoothGattDescriptor(
            clientUuid,
            (BluetoothGattDescriptor.PERMISSION_READ)
        )
        clientDescriptor.setValue(byteArrayOf(0, 0))
        mCrossTrainerDataCharacteristic.addDescriptor(clientDescriptor)

        val charDescriptor = BluetoothGattDescriptor(
            charUuid,
            BluetoothGattDescriptor.PERMISSION_READ
        )
        val stringT = "Speed of the fitness machine in kilometers" +
                " per hour"
        charDescriptor.setValue(stringT.toByteArray(Charset.forName("UTF-8")))
        mCrossTrainerDataCharacteristic.addDescriptor(charDescriptor)

        bluetoothGattService = BluetoothGattService(
            fitnessUuid.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        bluetoothGattService.addCharacteristic(mCrossTrainerDataCharacteristic)
        mGattServer = mBluetoothManager.openGattServer(this,mGattServerCallback)
        mGattServer.addService(bluetoothGattService)
        isAdvertising = true
        advertiser.startAdvertising(settings,adData,mAdvScanResponse,advertisingCallback)
    }

    /**
     * Creates the Cross Trainer activity and sets the onClick functions for the buttons
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cross_data2)
        ad_button.setOnClickListener{
            if(isAdvertising) {
                disconnect()
            } else {
                setupFitnessMachine()
            }
        }
        notify_button.setOnClickListener{broadcastData()}
    }

    /**
     * Broadcasts our Cross Trainer data values to the BLE client
     * Does this by adding the values to the Treadmill characteristic and then sends it
     * over to the client through the mGattServer
     */
    private fun broadcastData() {
        heartRate = heart_rate1.text.toString().toInt()
        instantPower = instant_power1.text.toString().toInt()
        resistanceLevel= resist_level1.text.toString().toInt()
        val instantSpeedDouble = instant_speed1.text.toString().toDouble()
        instantSpeed = (instantSpeedDouble*100).toInt()
        steps = steps1.text.toString().toInt()
        val inclineDouble = incline1.text.toString().toDouble()
        incline = (inclineDouble*10).toInt()
        val rampAngleDouble = ramp_angle1.text.toString().toDouble()
        rampAngle = (rampAngleDouble*10).toInt()

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

        if(rampAngle > 100) {
            rampAngle = 100
        } else if(rampAngle < 0) {
            rampAngle = 0
        }

        if(steps > 300) {
            steps = 300
        } else if(steps < 0) {
            steps = 0
        }

        if(resistanceLevel > 1000) {
            resistanceLevel = 1000
        } else if(resistanceLevel < 0) {
            resistanceLevel = 0
        }

        if(instantPower > 2000) {
            instantPower = 2000
        } else if(instantPower < 0) {
            instantPower = 0
        }

        val bytesToSend = ByteArray(18)
        bytesToSend[0] = (0xC8).toByte()
        bytesToSend[1] = (0x09).toByte()
        bytesToSend[2] = (0x00).toByte()
        bytesToSend[3] = (instantSpeed and 0xff).toByte()
        bytesToSend[4] = (instantSpeed shr 8 and 0xff).toByte()
        bytesToSend[5] = (steps and 0xff).toByte()
        bytesToSend[6] = (steps shr 8 and 0xff).toByte()
        bytesToSend[7] = (steps and 0xff).toByte()
        bytesToSend[8] = (steps shr 8 and 0xff).toByte()
        bytesToSend[9] = (incline and 0xff).toByte()
        bytesToSend[10] = (incline shr 8 and 0xff).toByte()
        bytesToSend[11] = (rampAngle and 0xff).toByte()
        bytesToSend[12] = (rampAngle shr 8 and 0xff).toByte()
        bytesToSend[13] = (resistanceLevel and 0xff).toByte()
        bytesToSend[14] = (resistanceLevel shr 8 and 0xff).toByte()
        bytesToSend[15] = (instantPower and 0xff).toByte()
        bytesToSend[16] = (instantPower shr 8 and 0xff).toByte()
        bytesToSend[17] = (heartRate and 0xff).toByte()

        mCrossTrainerDataCharacteristic.setValue(bytesToSend)
        val indicate =(mCrossTrainerDataCharacteristic.properties == BluetoothGattCharacteristic.PROPERTY_INDICATE)
        mGattServer.notifyCharacteristicChanged(bluetoothDevice,mCrossTrainerDataCharacteristic,indicate)
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