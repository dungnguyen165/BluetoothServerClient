package com.dungnguyen.bluetoothserverclient

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import kotlinx.android.synthetic.main.activity_server.*
import java.util.*

class ServerActivity : AppCompatActivity() {

    val TAG = "ServerActivity"

    lateinit var mBluetoothManger: BluetoothManager
    lateinit var mBluetoothAdapter: BluetoothAdapter
    lateinit var mBluetoothLeAdvertiser: BluetoothLeAdvertiser
    lateinit var mGattServer: BluetoothGattServer
    val mAdvertiseCallback = AdvertiseCallback()

    var mDevices = mutableListOf<BluetoothDevice>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        mBluetoothManger = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManger.adapter
    }

    override fun onResume() {
        super.onResume()
        // Check if Bluetooth is enable
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
            var enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            finish()
            return
        }

        // Check if Bluetooth LE is supported
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
            return
        }

        // Check if Bluetooth Advertiser is supported
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported) {
            finish()
            return
        }
        mBluetoothLeAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser

        var gattServerCallback = GattServerCallback()
        mGattServer = mBluetoothManger.openGattServer(this, gattServerCallback)
        setupServer()
        startAdvertising()
    }

    private fun startAdvertising() {
        if (mBluetoothLeAdvertiser == null) {
            return
        }

        var setting = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()

        var parcelUuid = ParcelUuid(Constants.SERVICE_UUID)
        var data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(parcelUuid)
            .build()

        mBluetoothLeAdvertiser.startAdvertising(setting, data, mAdvertiseCallback)

    }

    private fun setupServer() {
        var service = BluetoothGattService(Constants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        var writeCharacteristic = BluetoothGattCharacteristic(
            Constants.CHARACTERISTIC_ECHO_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(writeCharacteristic)
        mGattServer.addService(service)
    }

    inner class GattServerCallback: BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED && device != null) {
                mDevices.add(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mDevices.remove(device)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            if (characteristic != null) {
                if (characteristic.uuid.equals(Constants.CHARACTERISTIC_ECHO_UUID)) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    if (value != null) {
                        var messageString= String(value, charset("UTF-8"))
                        Log.d(TAG, "$messageString")
                        var length: Int = value.size
                        var reversed = ByteArray(length)
                        for (i in 0..(length - 1)) {
                            reversed[i] = value[length - (i + 1)]
                        }
                        characteristic.value = reversed
                        for (device in mDevices) {
                            mGattServer.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    }
                }
            }
        }
    }

    inner class AdvertiseCallback: android.bluetooth.le.AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG,"Peripheral advertising started.")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.d(TAG, "Peripheral advertising failed: $errorCode")
        }
    }

    override fun onPause() {
        super.onPause()
        stopAdvertising()
        stopServer()
    }

    private fun stopAdvertising() {
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback)
        }
    }

    private fun stopServer() {
        if (mGattServer != null) {
            mGattServer.close()
        }
    }

}
