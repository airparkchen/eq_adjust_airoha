package com.example.eq_adjust

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

import com.airoha.sdk.AirohaSDK
import com.airoha.sdk.AirohaConnector
import com.airoha.sdk.api.control.AirohaDeviceControl
import com.airoha.sdk.api.control.PEQControl
import com.airoha.sdk.api.control.AirohaDeviceListener
import com.airoha.sdk.api.device.AirohaDevice
import com.airoha.sdk.api.device.ApiStrategy
import com.airoha.sdk.api.message.AirohaBaseMsg
import com.airoha.sdk.api.message.AirohaEQPayload
import com.airoha.sdk.api.message.AirohaEQSettings
import com.airoha.sdk.api.message.AirohaAdaptiveEqInfo
import com.airoha.sdk.api.utils.AirohaStatusCode
import com.airoha.sdk.api.utils.ConnectionProtocol
import com.airoha.sdk.api.utils.ConnectionUUID
import java.util.LinkedList

// 新增：實作ApiStrategy（基於demo app的TestDeviceStrategy）
class TestDeviceStrategy : ApiStrategy() {
    // 這個類別通常包含裝置特定的策略邏輯
    // 暫時使用空實作，如果需要特定邏輯再補充
}

@SuppressLint("MissingPermission")
class MainActivity: FlutterActivity() {
    private val CHANNEL_SDK = "airoha_sdk"
    private val CHANNEL_EQ = "airoha_eq"
    private val PERMISSION_REQUEST_CODE = 100

    // Airoha SDK變數
    private var airohaSDK: AirohaSDK? = null
    private var airohaConnector: AirohaConnector? = null
    private var deviceControl: AirohaDeviceControl? = null
    private var eqControl: PEQControl? = null
    private var methodChannel: MethodChannel? = null

    // 藍牙相關變數（基於DeviceSearchPresenter）
    private val SPP_UUID = "00000000-0000-0000-0099-AABBCCDDEEFF"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var bluetoothLeAudio: BluetoothLeAudio? = null
    private var isConnected = false
    private var isChecking = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_SDK)

        // 設定SDK方法通道
        methodChannel!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "initSDK" -> {
                    initSDK(result)
                }
                "connectBoundDevice" -> {
                    connectBoundDevice(result)
                }
                "disconnect" -> {
                    disconnect(result)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // 設定EQ方法通道
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_EQ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getAdaptiveEqDetectionStatus" -> {
                    getAdaptiveEqDetectionStatus(result)
                }
                "setAdaptiveEqDetectionStatus" -> {
                    val status = call.argument<Int>("status") ?: 0
                    setAdaptiveEqDetectionStatus(status, result)
                }
                "getAdaptiveEqInfo" -> {
                    getAdaptiveEqInfo(result)
                }
                "getAllEQSettings" -> {
                    getAllEQSettings(result)
                }
                "setEQSetting" -> {
                    val categoryId = call.argument<Int>("categoryId") ?: 0
                    val iirParams = call.argument<List<Map<String, Any>>>("iirParams") ?: emptyList()
                    val allSampleRates = call.argument<List<Int>>("allSampleRates") ?: emptyList()
                    val bandCount = call.argument<Int>("bandCount") ?: 0
                    val saveOrNot = call.argument<Boolean>("saveOrNot") ?: false
                    setEQSetting(categoryId, iirParams, allSampleRates, bandCount, saveOrNot, result)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        initializeBluetooth()
    }

    private fun requestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // 初始化藍牙（基於DeviceSearchPresenter）
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 設定A2DP Profile監聽器
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    bluetoothA2dp = proxy as BluetoothA2dp
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    bluetoothA2dp = null
                }
            }
        }, BluetoothProfile.A2DP)

        // 設定LEA Profile監聽器 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.LE_AUDIO) {
                        bluetoothLeAudio = proxy as BluetoothLeAudio
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.LE_AUDIO) {
                        bluetoothLeAudio = null
                    }
                }
            }, BluetoothProfile.LE_AUDIO)
        }
    }

    private fun initSDK(result: MethodChannel.Result) {
        try {
            airohaSDK = AirohaSDK.getInst()
            airohaSDK?.init(this)
            airohaConnector = airohaSDK?.airohaDeviceConnector
            deviceControl = airohaSDK?.airohaDeviceControl
            eqControl = airohaSDK?.airohaEQControl

            result.success(true)
        } catch (e: Exception) {
            result.error("INIT_ERROR", "初始化SDK失敗: ${e.message}", null)
        }
    }

    // 基於DeviceSearchPresenter.connectBoundDevice()的實作
    private fun connectBoundDevice(result: MethodChannel.Result) {
        try {
            airohaConnector?.let { connector ->
                // 註冊連接監聽器
                connector.registerConnectionListener(object : AirohaConnector.AirohaConnectionListener {
                    override fun onStatusChanged(status: Int) {
                        runOnUiThread {
                            Log.d("MainActivity", "連接狀態變更: $status")

                            // 記錄所有狀態碼以便除錯
                            val statusText = when (status) {
                                1012 -> "CONNECTED"  // 實際的連接成功狀態
                                1011 -> "CONNECTING" // 實際的連接中狀態
                                AirohaConnector.DISCONNECTED -> "DISCONNECTED"
                                AirohaConnector.CONNECTION_ERROR -> "CONNECTION_ERROR"
                                AirohaConnector.INITIALIZATION_FAILED -> "INITIALIZATION_FAILED"
                                AirohaConnector.WAITING_CONNECTABLE -> "WAITING_CONNECTABLE"
                                AirohaConnector.DISCONNECTING -> "DISCONNECTING"
                                else -> "UNKNOWN_STATUS_$status"
                            }
                            Log.d("MainActivity", "狀態說明: $statusText")

                            when (status) {
                                1012 -> { // 實際的CONNECTED狀態
                                    isConnected = true
                                    Log.d("MainActivity", "✓ 連接成功!")
                                    methodChannel?.invokeMethod("onStatusChanged", mapOf("status" to status))
                                }
                                AirohaConnector.DISCONNECTED -> {
                                    isConnected = false
                                    Log.d("MainActivity", "✗ 連接已斷開")
                                    methodChannel?.invokeMethod("onStatusChanged", mapOf("status" to status))
                                }
                                AirohaConnector.CONNECTION_ERROR -> {
                                    isConnected = false
                                    Log.e("MainActivity", "✗ 連接錯誤")
                                }
                                AirohaConnector.INITIALIZATION_FAILED -> {
                                    isConnected = false
                                    Log.e("MainActivity", "✗ 初始化失敗")
                                }
                                1011 -> { // 實際的CONNECTING狀態
                                    Log.d("MainActivity", "⏳ 正在連接...")
                                }
                                AirohaConnector.WAITING_CONNECTABLE -> {
                                    Log.d("MainActivity", "⏳ 等待可連接...")
                                }
                                AirohaConnector.DISCONNECTING -> {
                                    Log.d("MainActivity", "⏳ 正在斷開...")
                                }
                                else -> {
                                    Log.w("MainActivity", "⚠ 未知狀態碼: $status")
                                }
                            }
                        }
                    }

                    override fun onDataReceived(msg: AirohaBaseMsg) {
                        Log.d("MainActivity", "收到數據: ${msg.javaClass.simpleName}")
                    }
                })

                // 開始檢查已配對裝置（基於DeviceSearchPresenter邏輯）
                startDeviceCheck(result)

            } ?: run {
                result.error("CONNECT_ERROR", "SDK未初始化", null)
            }
        } catch (e: Exception) {
            result.error("CONNECT_ERROR", "連接失敗: ${e.message}", null)
        }
    }

    // 基於DeviceSearchPresenter.checkBondDevice()的實作
    private fun startDeviceCheck(result: MethodChannel.Result) {
        isChecking = true

        CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            val maxAttempts = 60 // 30秒超時

            while (isChecking && attempts < maxAttempts) {
                attempts++
                Log.d("MainActivity", "檢查裝置，嘗試次數: $attempts")

                if (findAndConnectDevice()) {
                    runOnUiThread {
                        result.success(true)
                    }
                    break
                }

                delay(500)
            }

            if (attempts >= maxAttempts && !isConnected) {
                runOnUiThread {
                    result.error("TIMEOUT", "連接超時", null)
                }
            }

            isChecking = false
        }
    }

    // 基於DeviceSearchPresenter.findConnectedDevice()的實作
    private fun findAndConnectDevice(): Boolean {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled || adapter.state != BluetoothAdapter.STATE_ON) {
                return false
            }

            val bondedDevices = adapter.bondedDevices
            Log.d("MainActivity", "已配對裝置數量: ${bondedDevices.size}")

            for (device in bondedDevices) {
                Log.d("MainActivity", "檢查裝置: ${device.name}, ${device.address}")

                // 檢查是否為Airoha裝置且已連接A2DP
                if (isAirohaDevice(device) && isA2dpConnected(device.address)) {
                    Log.d("MainActivity", "找到Airoha A2DP裝置: ${device.name}")
                    connectClassicDevice(device)
                    return true
                }
                // 檢查LEA連接 (Android 13+)
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isLeaConnected(device.address)) {
                    Log.d("MainActivity", "找到Airoha LEA裝置: ${device.name}")
                    connectLeaDevice(device)
                    return true
                }
            }
        }
        return false
    }

    // 基於DeviceSearchPresenter.isAirohaDevice()的實作
    private fun isAirohaDevice(device: BluetoothDevice): Boolean {
        val uuids = device.uuids
        if (uuids == null) return false

        for (uuid in uuids) {
            if (uuid.uuid.toString().uppercase() == SPP_UUID) {
                return true
            }
        }
        return false
    }

    // 基於DeviceSearchPresenter.isA2dpConnected()的實作
    private fun isA2dpConnected(address: String): Boolean {
        bluetoothA2dp?.let { a2dp ->
            val device = bluetoothAdapter?.getRemoteDevice(address)
            device?.let {
                return a2dp.getConnectionState(it) == BluetoothProfile.STATE_CONNECTED
            }
        }
        return false
    }

    // 基於DeviceSearchPresenter.isLEAConnected()的實作
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isLeaConnected(address: String): Boolean {
        bluetoothLeAudio?.let { lea ->
            val device = bluetoothAdapter?.getRemoteDevice(address)
            device?.let {
                return lea.getConnectionState(it) == BluetoothProfile.STATE_CONNECTED
            }
        }
        return false
    }

    // 基於DeviceSearchPresenter.connectClassicDevice()的實作
    private fun connectClassicDevice(bluetoothDevice: BluetoothDevice) {
        Log.d("MainActivity", "開始連接Classic裝置: ${bluetoothDevice.name}, ${bluetoothDevice.address}")

        val airohaDevice = AirohaDevice()
        airohaDevice.setApiStrategy(TestDeviceStrategy())
        airohaDevice.setTargetAddr(bluetoothDevice.address)
        airohaDevice.setDeviceName(bluetoothDevice.name)
        airohaDevice.setPreferredProtocol(ConnectionProtocol.PROTOCOL_SPP)

        Log.d("MainActivity", "AirohaDevice設定完成 - MAC: ${bluetoothDevice.address}, 協定: SPP")

        try {
            val connectionUUID = ConnectionUUID(UUID.fromString(SPP_UUID))
            Log.d("MainActivity", "開始呼叫SDK connect方法...")
            airohaConnector?.connect(airohaDevice, connectionUUID)
            Log.d("MainActivity", "SDK connect方法已呼叫")
        } catch (e: Exception) {
            Log.e("MainActivity", "連接過程發生錯誤: ${e.message}", e)
        }
    }

    // 基於DeviceSearchPresenter.connectLEADevice()的實作
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun connectLeaDevice(bluetoothDevice: BluetoothDevice) {
        Log.d("MainActivity", "開始連接LEA裝置: ${bluetoothDevice.name}, ${bluetoothDevice.address}")

        val airohaDevice = AirohaDevice()
        airohaDevice.setApiStrategy(TestDeviceStrategy())
        airohaDevice.setTargetAddr(bluetoothDevice.address)
        airohaDevice.setDeviceName(bluetoothDevice.name)
        airohaDevice.setPreferredProtocol(ConnectionProtocol.PROTOCOL_LEA)

        Log.d("MainActivity", "AirohaDevice設定完成 - MAC: ${bluetoothDevice.address}, 協定: LEA")

        try {
            Log.d("MainActivity", "開始呼叫SDK connect方法...")
            airohaConnector?.connect(airohaDevice)
            Log.d("MainActivity", "SDK connect方法已呼叫")
        } catch (e: Exception) {
            Log.e("MainActivity", "連接過程發生錯誤: ${e.message}", e)
        }
    }

    private fun disconnect(result: MethodChannel.Result) {
        try {
            isChecking = false
            airohaConnector?.disconnect()
            result.success(null)
        } catch (e: Exception) {
            result.error("DISCONNECT_ERROR", "斷開連接失敗: ${e.message}", null)
        }
    }

    // EQ相關方法保持不變
    private fun getAdaptiveEqDetectionStatus(result: MethodChannel.Result) {
        try {
            deviceControl?.getAdaptiveEqDetectionStatus(object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        if (code == AirohaStatusCode.STATUS_SUCCESS) {
                            val status = msg.msgContent as Int
                            result.success(status == 1)
                        } else {
                            result.error("GET_STATUS_ERROR", code.description, null)
                        }
                    }
                }

                override fun onChanged(code: AirohaStatusCode, msg: AirohaBaseMsg) {}
            })
        } catch (e: Exception) {
            result.error("GET_STATUS_ERROR", "取得狀態失敗: ${e.message}", null)
        }
    }

    private fun setAdaptiveEqDetectionStatus(status: Int, result: MethodChannel.Result) {
        try {
            deviceControl?.setAdaptiveEqDetectionStatus(status, object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {}

                override fun onChanged(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        result.success(code == AirohaStatusCode.STATUS_SUCCESS)
                    }
                }
            })
        } catch (e: Exception) {
            result.error("SET_STATUS_ERROR", "設定狀態失敗: ${e.message}", null)
        }
    }

    private fun getAdaptiveEqInfo(result: MethodChannel.Result) {
        try {
            deviceControl?.getAdaptiveEqInfo(object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        if (code == AirohaStatusCode.STATUS_SUCCESS) {
                            val info = msg.msgContent as AirohaAdaptiveEqInfo
                            val data = mapOf(
                                "leftIndex" to info.leftIndex,
                                "rightIndex" to info.rightIndex
                            )
                            result.success(data)
                        } else {
                            result.error("GET_INFO_ERROR", code.description, null)
                        }
                    }
                }

                override fun onChanged(code: AirohaStatusCode, msg: AirohaBaseMsg) {}
            })
        } catch (e: Exception) {
            result.error("GET_INFO_ERROR", "取得資訊失敗: ${e.message}", null)
        }
    }

    private fun getAllEQSettings(result: MethodChannel.Result) {
        try {
            eqControl?.getAllEQSettings(object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        if (code == AirohaStatusCode.STATUS_SUCCESS) {
                            val eqSettingsList = msg.msgContent as LinkedList<AirohaEQSettings>
                            val settingsData = mutableListOf<Map<String, Any>>()

                            for (setting in eqSettingsList) {
                                val settingMap = mutableMapOf<String, Any>(
                                    "categoryId" to setting.categoryId
                                )

                                setting.eqPayload?.let { payload ->
                                    val iirParamsList = mutableListOf<Map<String, Any>>()
                                    payload.iirParams?.let { params ->
                                        for (param in params) {
                                            iirParamsList.add(mapOf(
                                                "bandType" to param.bandType,
                                                "frequency" to param.frequency,
                                                "gainValue" to param.gainValue,
                                                "qValue" to param.qValue
                                            ))
                                        }
                                    }

                                    settingMap["eqPayload"] = mapOf(
                                        "iirParams" to iirParamsList,
                                        "allSampleRates" to payload.allSampleRates.toList(),
                                        "bandCount" to payload.bandCount.toInt()
                                    )
                                }

                                settingsData.add(settingMap)
                            }

                            result.success(mapOf("eqSettingsList" to settingsData))
                        } else {
                            result.error("GET_SETTINGS_ERROR", code.description, null)
                        }
                    }
                }

                override fun onChanged(code: AirohaStatusCode, msg: AirohaBaseMsg) {}
            })
        } catch (e: Exception) {
            result.error("GET_SETTINGS_ERROR", "取得設定失敗: ${e.message}", null)
        }
    }

    private fun setEQSetting(
        categoryId: Int,
        iirParams: List<Map<String, Any>>,
        allSampleRates: List<Int>,
        bandCount: Int,
        saveOrNot: Boolean,
        result: MethodChannel.Result
    ) {
        try {
            val eqPayload = AirohaEQPayload()
            eqPayload.setAllSampleRates(allSampleRates.toIntArray())
            eqPayload.setBandCount(bandCount.toFloat())
            eqPayload.setIndex(categoryId)

            val params = LinkedList<AirohaEQPayload.EQIDParam>()
            for (param in iirParams) {
                val bandInfo = AirohaEQPayload.EQIDParam()
                bandInfo.setBandType((param["bandType"] as Number).toInt())
                bandInfo.setFrequency((param["frequency"] as Number).toFloat())
                bandInfo.setGainValue((param["gainValue"] as Number).toFloat())
                bandInfo.setQValue((param["qValue"] as Number).toFloat())
                params.add(bandInfo)
            }
            eqPayload.setIirParams(params)

            eqControl?.setEQSetting(categoryId, eqPayload, saveOrNot, object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {}

                override fun onChanged(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        result.success(code == AirohaStatusCode.STATUS_SUCCESS)
                    }
                }
            })
        } catch (e: Exception) {
            result.error("SET_EQ_ERROR", "設定EQ失敗: ${e.message}", null)
        }
    }
}