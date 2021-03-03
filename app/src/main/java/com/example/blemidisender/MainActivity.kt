package com.example.blemidisender

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*


class MainActivity : AppCompatActivity() {


    companion object {
        private const val TAG = "BLEMIDISender"
    }


    private val send: View by lazy {
        findViewById(R.id.send)
    }

    private val connectStatus: TextView by lazy {
        findViewById(R.id.connect_status)
    }
    private val sendStatus: TextView by lazy {
        findViewById(R.id.send_status)
    }
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val midiManager by lazy { (getSystemService(Context.MIDI_SERVICE) as MidiManager) }

    private val bluetoothLeAdvertiser by lazy {
        bluetoothManager.adapter.bluetoothLeAdvertiser
    }
    private var port: MidiInputPort? = null
    private var gattServer: BluetoothGattServer? = null
    private lateinit var midiGattService: BluetoothGattService
    private lateinit var midiCharacteristic: BluetoothGattCharacteristic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))


        /*connect.setOnClickListener {
            val usbDevice = midiManager.devices.firstOrNull { it.type == MidiDeviceInfo.TYPE_BLUETOOTH }

            if (usbDevice != null) {
                midiManager.openDevice(
                    usbDevice,
                    {

                        port = it.openInputPort(0)
                        if (port != null) {
                            connectStatus.text = "Connected"
                        } else {
                            connectStatus.text = "Cannot open port"
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } else {
                connectStatus.text = "Not found"
            }
        }*/


        send.setOnClickListener {
            sendStatus.text = try {
                val buffer = ByteArray(3)
                buffer[0] = (0x90.toByte() + 0x01.toByte()).toByte() // Note On - Channel 1
                buffer[1] = 0x3C.toByte() // pitch (Note C3)
                buffer[2] = 127.toByte() // velocity
                midiCharacteristic.value = buffer
                port!!.send(buffer, 0, 0)
                "Send OK"
            } catch (e: Exception) {
                "Send Fail: " + e.message
            }
        }

        // MIDI service

        // MIDI service
        midiCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3"),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ
                    or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        midiCharacteristic.value = ByteArray(3)
        midiCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        midiGattService = BluetoothGattService(
            UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        midiGattService.addCharacteristic(midiCharacteristic)


        startAdvertising()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }


    fun startAdvertising() {
        // register Gatt service to Gatt server

        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        }
        if (gattServer == null) {
            Log.d(TAG, "gattServer is null, check Bluetooth is ON.")
            return
        }

        // these service will be listened.
        // FIXME these didn't used for service discovery
        var serviceInitialized = false
        while (!serviceInitialized) {
            try {
                //gattServer.addService(informationGattService)
                gattServer!!.addService(midiGattService) // NullPointerException, DeadObjectException thrown here
                serviceInitialized = true
            } catch (e: java.lang.Exception) {
                Log.d(TAG, "Adding Service failed, retrying..")
                try {
                    gattServer!!.clearServices()
                } catch (ignored: Throwable) {
                }
                try {
                    Thread.sleep(100)
                } catch (ignored: InterruptedException) {
                }
            }
        }

        // set up advertising setting
        val advertiseSettings = AdvertiseSettings.Builder()
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .build()

        // set up advertising data
        val advertiseData = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(true)
            .setIncludeDeviceName(true)
            .build()

        // set up scan result
        val scanResult = AdvertiseData.Builder()
            .addServiceUuid(
                ParcelUuid.fromString(
                    UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700").toString()
                )
            )
            .build()
        bluetoothLeAdvertiser.startAdvertising(
            advertiseSettings,
            advertiseData,
            scanResult,
            object : AdvertiseCallback() {}
        )
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(TAG, "onConnectionStateChange")
            if (newState == BluetoothProfile.STATE_CONNECTED && device != null) {

                Log.d(TAG, "onConnectionStateChange1")

                midiManager.openBluetoothDevice(device, {
                    /*Log.d(TAG, "Ports=${it.info.outputPortCount}")
                    Log.d(TAG, "Conectado BLE ${it.info}")*/
                    port = it.openInputPort(0)
                    if (port != null) {
                        connectStatus.text = "Connected"
                    } else {
                        connectStatus.text = "Cannot open port"
                    }
                }, Handler(Looper.getMainLooper()))

            } else {
                Handler(Looper.getMainLooper()).post {
                    connectStatus.text = "Disconnected: $newState"
                }
            }

        }
    }
}