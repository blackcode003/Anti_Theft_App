import 'package:permission_handler/permission_handler.dart';
import 'package:speech_to_text/speech_to_text.dart';

/// Keeps listening (restarting automatically) and fires [onCommand]
/// when one of the trigger phrases is heard.
class VoiceService {
  final void Function(String words) onWords;
  final void Function() onCommand;

  VoiceService({required this.onWords, required this.onCommand});

  final SpeechToText _stt = SpeechToText();
  bool _active = false;

  // Add / change your trigger phrases here (lowercase).
  static const List<String> triggers = [
    'good night'
    'jarvis,please turn off screen',
  ];

  Future<bool> start() async {
    if (!await Permission.microphone.request().isGranted) return false;

    final ok = await _stt.initialize(
      onStatus: (s) {
        // Recognizer stops after silence; restart to keep listening.
        if (_active && (s == 'done' || s == 'notListening')) _listen();
      },
      onError: (_) {
        if (_active) Future.delayed(const Duration(seconds: 1), _listen);
      },
    );
    if (!ok) return false;

    _active = true;
    _listen();
    return true;
  }

  void _listen() {
    if (!_active || _stt.isListening) return;
    _stt.listen(
      onResult: (r) {
        final text = r.recognizedWords.toLowerCase();
        onWords(text);
        if (triggers.any(text.contains)) {
          onCommand();
          _stt.stop();
        }
      },
      listenFor: const Duration(seconds: 30),
      pauseFor: const Duration(seconds: 5),
      listenOptions: SpeechListenOptions(partialResults: true),
    );
  }

  Future<void> stop() async {
    _active = false;
    await _stt.stop();
  }
}