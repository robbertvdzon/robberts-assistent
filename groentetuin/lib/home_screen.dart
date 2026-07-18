import 'package:flutter/material.dart';

import 'api_client.dart';

/// Na het inloggen: één testknop die een geauthenticeerde call naar de assistent-backend
/// (`GET /api/v1/ping`) doet en het resultaat toont.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _loading = false;
  String? _result;
  bool _ok = false;

  Future<void> _runTest() async {
    setState(() {
      _loading = true;
      _result = null;
    });
    try {
      final body = await widget.api.ping();
      setState(() {
        _ok = body['status'] == 'ok';
        _result = _ok ? 'OK — backend antwoordde: $body' : 'Onverwacht antwoord: $body';
      });
    } catch (e) {
      setState(() {
        _ok = false;
        _result = 'Mislukt: $e';
      });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Groentetuin'),
        actions: [
          IconButton(
            onPressed: widget.onLoggedOut,
            icon: const Icon(Icons.logout),
            tooltip: 'Uitloggen',
          ),
        ],
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              FilledButton.icon(
                onPressed: _loading ? null : _runTest,
                icon: _loading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.wifi_tethering),
                label: Text(_loading ? 'Bezig...' : 'Test backend'),
              ),
              if (_result != null)
                Padding(
                  padding: const EdgeInsets.only(top: 20),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _ok ? Icons.check_circle : Icons.error,
                        color: _ok ? Colors.green : Colors.red,
                      ),
                      const SizedBox(width: 8),
                      Flexible(child: Text(_result!)),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
