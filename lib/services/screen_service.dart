import 'package:flutter/services.dart';

/// Talks to native Android code that locks the screen.
class ScreenService {
  static const _channel = MethodChannel('screen_control');

  Future<bool> isAdmin() async =>
      (await _channel.invokeMethod<bool>('isAdmin')) ?? false;

  Future<void> requestAdmin() => _channel.invokeMethod('requestAdmin');

  Future<bool> lockScreen() async =>
      (await _channel.invokeMethod<bool>('lockScreen')) ?? false;
}
