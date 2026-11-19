import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:turnoff/services/native_service.dart';
// import 'native_service.dart';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'Turn Off',
        theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.indigo),
        home: const HomePage(),
      );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  final _native = NativeService();
  bool _isAdmin = false;
  bool _listening = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final a = await _native.isAdmin();
    final l = await _native.isListening();
    if (mounted) setState(() { _isAdmin = a; _listening = l; });
  }

  Future<void> _toggle(bool on) async {
    if (on) {
      final mic = await Permission.microphone.request();
      await Permission.notification.request(); // Android 13+
      if (!mic.isGranted) return;
      await _native.startListening();
    } else {
      await _native.stopListening();
    }
    await Future.delayed(const Duration(milliseconds: 300));
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Turn Off by Voice')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Card(
            child: ListTile(
              leading: Icon(_isAdmin ? Icons.check_circle : Icons.warning,
                  color: _isAdmin ? Colors.green : Colors.orange),
              title: Text(_isAdmin ? 'Lock permission granted' : 'Device admin permission needed'),
              trailing: _isAdmin
                  ? null
                  : FilledButton(onPressed: _native.requestAdmin, child: const Text('Enable')),
            ),
          ),
          SwitchListTile(
            title: const Text('Run in background & listen'),
            subtitle: Text('Say: ${NativeService.triggers.join(' / ')}'),
            value: _listening,
            onChanged: _isAdmin ? _toggle : null,
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.battery_saver),
            title: const Text('Disable battery optimization'),
            subtitle: const Text('Set this app to "Unrestricted" so Android does not kill it'),
            onTap: _native.openBatterySettings,
          ),
          const SizedBox(height: 12),
          Center(
            child: OutlinedButton.icon(
              onPressed: _isAdmin ? _native.lockScreen : null,
              icon: const Icon(Icons.power_settings_new),
              label: const Text('Test: turn off screen now'),
            ),
          ),
        ],
      ),
    );
  }
}
