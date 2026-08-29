import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android-only runtime controls for VR platform-view playback
/// (陀螺仪视角 / 触控视角 / 初始视角).
class AndroidVrControls {
  AndroidVrControls._();

  static const MethodChannel _channel =
      MethodChannel('dev.flutter.video_player_android/vr_controls');

  static bool get isSupported => !kIsWeb && Platform.isAndroid;

  static Future<void> setSensorRotation({
    required int playerId,
    required bool enabled,
  }) {
    return _channel.invokeMethod<void>('setSensorRotation', {
      'playerId': playerId,
      'enabled': enabled,
    });
  }

  static Future<void> setTouchEnabled({
    required int playerId,
    required bool enabled,
  }) {
    return _channel.invokeMethod<void>('setTouchEnabled', {
      'playerId': playerId,
      'enabled': enabled,
    });
  }

  static Future<void> resetViewOrientation({required int playerId}) {
    return _channel.invokeMethod<void>('resetViewOrientation', {
      'playerId': playerId,
    });
  }

  static Future<bool> getSensorRotation({required int playerId}) async {
    final v = await _channel.invokeMethod<bool>('getSensorRotation', {
      'playerId': playerId,
    });
    return v ?? true;
  }

  static Future<bool> getTouchEnabled({required int playerId}) async {
    final v = await _channel.invokeMethod<bool>('getTouchEnabled', {
      'playerId': playerId,
    });
    return v ?? true;
  }
}
