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
import android.support.annotation.RequiresApi
import android.util.Log
import java.util.*
import java.util.jar.Manifest

class ClientActivity : AppCompatActivity() {

    lateinit var mBluetoothAdapter: BluetoothAdapter

    val TAG = "ClientActivity"
    val REQUET_ENABLE_BT = 1
    val REQUEST_FINE_LOCATION = 2
    val SCAN_PERIOD: Long = 10000


    var mScanning = false
    lateinit var mScanResults: HashMap<Any, Any>
    lateinit var mScanCallback: ScanCallback
    lateinit var mBluetoothLeScanner: BluetoothLeScanner
    lateinit var mHandler: Handler
    lateinit var mGatt: BluetoothGatt
    var mConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
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
            .setServiceUuid(ParcelUuid(UUID.fromString("abc")))
            .build()
        filters.add(scanFilter)
        var setting = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()

        mScanResults = HashMap()
        mScanCallback = BTleScanCallback()
        mBluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner
        mBluetoothLeScanner.startScan(filters, setting, mScanCallback)
        mScanning = true
        mHandler = Handler()
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD)
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
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            if (results != null) {
                for (result in results) {
                    addScanResult(result)
                }
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
                disconnectGattServer()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
        }
    }

    private fun disconnectGattServer() {
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
