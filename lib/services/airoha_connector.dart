import 'package:flutter/services.dart';

class AirohaConnector {
  static const MethodChannel _channel = MethodChannel('airoha_sdk');

  // 連接狀態常數（對應demo app中的狀態）
  static const int CONNECTED = 3;
  static const int CONNECTED_WRONG_ROLE = 4;
  static const int DISCONNECTED = 0;

  // 連接狀態回調
  Function(int)? _onStatusChanged;

  static final AirohaConnector _instance = AirohaConnector._internal();
  factory AirohaConnector() => _instance;
  AirohaConnector._internal() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  // 處理來自原生端的回調
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onStatusChanged':
        final int status = call.arguments['status'];
        _onStatusChanged?.call(status);
        break;
    }
  }

  // 註冊連接狀態監聽器（對應demo app的registerConnectionListener）
  void registerConnectionListener(Function(int) onStatusChanged) {
    _onStatusChanged = onStatusChanged;
  }

  // 取消註冊監聽器（對應demo app的unregisterConnectionListener）
  void unregisterConnectionListener() {
    _onStatusChanged = null;
  }

  // 連接已配對的裝置（對應demo app的connectBoundDevice）
  Future<bool> connectBoundDevice() async {
    try {
      final bool result = await _channel.invokeMethod('connectBoundDevice');
      return result;
    } catch (e) {
      print('連接裝置失敗: $e');
      return false;
    }
  }

  // 斷開連接（對應demo app的disconnect）
  Future<void> disconnect() async {
    try {
      await _channel.invokeMethod('disconnect');
    } catch (e) {
      print('斷開連接失敗: $e');
    }
  }

  // 初始化SDK（對應demo app的AirohaSDK.getInst().init）
  Future<bool> initSDK() async {
    try {
      final bool result = await _channel.invokeMethod('initSDK');
      return result;
    } catch (e) {
      print('初始化SDK失敗: $e');
      return false;
    }
  }
}