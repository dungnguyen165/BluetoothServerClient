package com.dungnguyen.bluetoothserverclient

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import kotlinx.android.synthetic.main.activity_client.*
import java.io.UnsupportedEncodingException
import java.util.*


class ClientActivity : AppCompatActivity() {

    lateinit var mBluetoothAdapter: BluetoothAdapter

    val TAG = "ClientActivity"
    val REQUET_ENABLE_BT = 1
    val REQUEST_FINE_LOCATION = 2


    var mScanning = false
    lateinit var mScanResults: HashMap<Any, Any>
    lateinit var mScanCallback: ScanCallback
    lateinit var mBluetoothLeScanner: BluetoothLeScanner
    lateinit var mHandler: Handler
    lateinit var mGatt: BluetoothGatt
    var mConnected = false
    var mEchoInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        send_message_button.setOnClickListener {
            sendMessage()
        }

        start_scanning_button.setOnClickListener {
            startScan()
        }

        stop_scanning_button.setOnClickListener {
            stopScan()
        }

        disconnect_button.setOnClickListener {
            disconnectGattServer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
        }
    }

    fun startScan() {
        if (!hasPermission() || mScanning) return
        val filters = mutableListOf<ScanFilter>()
        var scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Constants.SERVICE_UUID))
            .build()
        filters.add(scanFilter)
        var setting = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()

        mScanResults = HashMap()
        mScanCallback = BTleScanCallback()
        mBluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner
        mBluetoothLeScanner.startScan(filters, setting, mScanCallback)
        mScanning = true
        mHandler = Handler()
        mHandler.postDelayed(this::stopScan, Constants.SCAN_PERIOD)
    }

    fun stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback)
            scanComplete()
        }
        mScanning = false
    }

    fun scanComplete() {
        if (mScanResults.isEmpty()) {
            return
        }
        for (deviceAddress in mScanResults.keys) {
            Log.d(TAG, "Found device: $deviceAddress")
        }
    }

    inner class BTleScanCallback: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result != null) {
                addScanResult(result)
                Log.d(TAG, "Scan result: ${result.toString()}")
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            if (results != null) {
                for (result in results) {
                    addScanResult(result)
                }
                Log.d(TAG, "Scan results: ${results.toString()}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG,"BLE Scan Failed with code $errorCode")
        }

        fun addScanResult(result: ScanResult) {
//            var device = result.device
//            var deviceAddress = device.address
//            mScanResults[deviceAddress] = device

            stopScan()
            var bluetoothDevice = result.device
            Log.d(TAG, "Connecting to device: ${result.device}")
            connectDevice(bluetoothDevice)
        }
    }

    fun connectDevice(device: BluetoothDevice) {
        var gattClientCallBack = GattClientCallBack()
        mGatt = device.connectGatt(this, false, gattClientCallBack)
    }

    inner class GattClientCallBack: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.d(TAG,"Disconnect Server: GATT_FAILURE")
                disconnectGattServer()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG,"Disconnect Server: GATT_SUCCESS")
                disconnectGattServer()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to server")
                mConnected = true
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected to server")
                disconnectGattServer()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "Service discovered")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return
            }
            val service = gatt?.getService(Constants.SERVICE_UUID)
            Log.d(TAG, "Service: ${service.toString()}")
            val characteristic = service?.getCharacteristic(Constants.CHARACTERISTIC_ECHO_UUID)
            characteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            Log.d(TAG, "mInitialized before: $mEchoInitialized")
            mEchoInitialized = gatt!!.setCharacteristicNotification(characteristic, true)
            Log.d(TAG, "mInitialized after: $mEchoInitialized")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d("ClientActivity", "Characteristic send changed")
            if (characteristic != null) {
                var messageBytes = characteristic.value
                lateinit var messageString: String
                try {
                    messageString = String(messageBytes, charset("UTF-8"))
                } catch (ex: UnsupportedEncodingException) {
                    Log.d("ClientActivity", "Unable to convert message byte to string")
                }
                Log.d("ClientActivity", "Received message: $messageString")
            }
        }
    }

    fun sendMessage() {
        if (!mConnected || !mEchoInitialized) {
            Log.d(TAG, "Cannot send message, connected: $mConnected, minitialized: $mEchoInitialized")
            return
        }
        Log.d(TAG, "Sending message")
        var service = mGatt.getService(Constants.SERVICE_UUID)
        var characteristic = service.getCharacteristic(Constants.CHARACTERISTIC_ECHO_UUID)
        var message = message_edit_text.text.toString()
        var messageByte = ByteArray(0)
        try {
            messageByte = message.toByteArray(charset("UTF-8"))
        } catch (ex: UnsupportedEncodingException) {
            Log.d("ClientActivity", "Failed to convert message string to byte array.")
        }
        characteristic.setValue(messageByte)
        var success = mGatt.writeCharacteristic(characteristic)
        if (success) {
            Log.d(TAG, "Successfully sent message")
        }

    }

    private fun disconnectGattServer() {
        Log.d(TAG, "disconnecting from server")
        mConnected = false
        if (mGatt != null) {
            mGatt.disconnect()
            mGatt.close()
        }
    }

    fun hasPermission(): Boolean {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
            requestBluetoothEnable()
            return false
        } else if (!hasLocationPermission()) {
            requestLocationPermission()
            return false
        }
        return true
    }

    private fun requestLocationPermission() {
        var enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUET_ENABLE_BT)
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.")
    }

    private fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            TODO("VERSION.SDK_INT < M")
        }
    }

    private fun requestBluetoothEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_FINE_LOCATION)
        }
    }
}
