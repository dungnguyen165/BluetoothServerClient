package com.dungnguyen.bluetoothserverclient

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launch_server_button.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }

        launch_client_button.setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
    }
}
