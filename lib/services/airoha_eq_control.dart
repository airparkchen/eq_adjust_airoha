import 'package:flutter/services.dart';

class AirohaEqControl {
  static const MethodChannel _channel = MethodChannel('airoha_eq');

  // 取得Adaptive EQ偵測狀態（對應demo app的getAdaptiveEqDetectionStatus）
  static Future<bool?> getAdaptiveEqDetectionStatus() async {
    try {
      final result = await _channel.invokeMethod('getAdaptiveEqDetectionStatus');
      return result is bool ? result : null;
    } catch (e) {
      print('取得Adaptive EQ狀態失敗: $e');
      return null;
    }
  }

  // 設定Adaptive EQ偵測狀態（對應demo app的setAdaptiveEqDetectionStatus）
  static Future<bool> setAdaptiveEqDetectionStatus({required bool isOn}) async {
    try {
      final int status = isOn ? 0x01 : 0x02; // 對應demo app的設定值
      final bool result = await _channel.invokeMethod('setAdaptiveEqDetectionStatus', {
        'status': status,
      });
      return result;
    } catch (e) {
      print('設定Adaptive EQ狀態失敗: $e');
      return false;
    }
  }

  // 取得Adaptive EQ資訊（對應demo app的getAdaptiveEqInfo）
  static Future<Map<String, dynamic>?> getAdaptiveEqInfo() async {
    try {
      final result = await _channel.invokeMethod('getAdaptiveEqInfo');
      return result is Map ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('取得Adaptive EQ資訊失敗: $e');
      return null;
    }
  }

  // 取得所有EQ設定（對應demo app的getAllEQSettings）
  static Future<Map<String, dynamic>?> getAllEqSettings() async {
    try {
      final result = await _channel.invokeMethod('getAllEQSettings');
      return result is Map ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('取得EQ設定失敗: $e');
      return null;
    }
  }

  // 設定EQ參數（對應demo app的setEQSetting）
  static Future<bool> setEqSetting({
    required int categoryId,
    required List<Map<String, dynamic>> iirParams,
    required List<int> allSampleRates,
    required bool saveOrNot,
  }) async {
    try {
      final bool result = await _channel.invokeMethod('setEQSetting', {
        'categoryId': categoryId,
        'iirParams': iirParams,
        'allSampleRates': allSampleRates,
        'bandCount': 10,
        'saveOrNot': saveOrNot,
      });
      return result;
    } catch (e) {
      print('設定EQ失敗: $e');
      return false;
    }
  }
}