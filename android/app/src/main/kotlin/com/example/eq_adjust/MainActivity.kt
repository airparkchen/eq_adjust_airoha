package com.example.eq_adjust

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import com.airoha.sdk.AirohaSDK
import com.airoha.sdk.AirohaConnector
import com.airoha.sdk.api.control.AirohaDeviceControl
import com.airoha.sdk.api.control.PEQControl
import com.airoha.sdk.api.control.AirohaDeviceListener
import com.airoha.sdk.api.message.AirohaBaseMsg
import com.airoha.sdk.api.message.AirohaEQPayload
import com.airoha.sdk.api.message.AirohaEQSettings
import com.airoha.sdk.api.message.AirohaAdaptiveEqInfo
import com.airoha.sdk.api.utils.AirohaStatusCode
import com.airoha.sdk.api.utils.AirohaEQBandType
import java.util.LinkedList

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

    // 測試用連接部份，但實際沒連接
    private fun connectBoundDevice(result: MethodChannel.Result) {
        try {
            airohaConnector?.let { connector ->
                // 註冊連接監聽器
                connector.registerConnectionListener(object : AirohaConnector.AirohaConnectionListener {
                    override fun onStatusChanged(status: Int) {
                        runOnUiThread {
                            methodChannel?.invokeMethod("onStatusChanged", mapOf("status" to status))
                        }
                    }

                    override fun onDataReceived(msg: AirohaBaseMsg) {
                        // 處理接收到的數據
                    }
                })

                result.success(true)
            } ?: run {
                result.error("CONNECT_ERROR", "SDK未初始化", null)
            }
        } catch (e: Exception) {
            result.error("CONNECT_ERROR", "連接失敗: ${e.message}", null)
        }
    }

    private fun disconnect(result: MethodChannel.Result) {
        try {
            airohaConnector?.disconnect()
            result.success(null)
        } catch (e: Exception) {
            result.error("DISCONNECT_ERROR", "斷開連接失敗: ${e.message}", null)
        }
    }

    private fun getAdaptiveEqDetectionStatus(result: MethodChannel.Result) {
        try {
            deviceControl?.getAdaptiveEqDetectionStatus(object : AirohaDeviceListener {
                override fun onRead(code: AirohaStatusCode, msg: AirohaBaseMsg) {
                    runOnUiThread {
                        if (code == AirohaStatusCode.STATUS_SUCCESS) {
                            val status = msg.msgContent as Int
                            // 根據API文檔：0-Freeze, 1-Enable, 2-Disable
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
            // 根據API文檔：1-Enable, 2-Disable
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
                                        "bandCount" to payload.bandCount.toInt()  // bandCount是float，轉為int
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
            // 基於API文檔創建EQ Payload
            val eqPayload = AirohaEQPayload()
            eqPayload.setAllSampleRates(allSampleRates.toIntArray())
            eqPayload.setBandCount(bandCount.toFloat())  // setBandCount需要float參數
            eqPayload.setIndex(categoryId)

            // 創建IIR參數列表
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

            // 呼叫API設定EQ
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