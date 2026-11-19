import 'package:flutter/services.dart';

/// Bridge to the Android side (foreground voice service + screen lock).
class NativeService {
  static const _ch = MethodChannel('screen_control');

  // Say any of these (lowercase). Edit freely.
  static const triggers = ['turn off', 'screen off', 'lock screen', 'sleep'];

  Future<bool> isAdmin() async => (await _ch.invokeMethod<bool>('isAdmin')) ?? false;
  Future<void> requestAdmin() => _ch.invokeMethod('requestAdmin');
  Future<bool> lockScreen() async => (await _ch.invokeMethod<bool>('lockScreen')) ?? false;

  Future<void> startListening() => _ch.invokeMethod('startListening', {'triggers': triggers});
  Future<void> stopListening() => _ch.invokeMethod('stopListening');
  Future<bool> isListening() async => (await _ch.invokeMethod<bool>('isListening')) ?? false;

  Future<void> openBatterySettings() => _ch.invokeMethod('openBatterySettings');
}
