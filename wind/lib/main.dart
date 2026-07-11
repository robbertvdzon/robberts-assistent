import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'wind_data.dart';

void main() {
  runApp(const WindApp());
}

class WindApp extends StatelessWidget {
  const WindApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Wind',
      theme: ThemeData(
        colorSchemeSeed: Colors.lightBlue,
        useMaterial3: true,
      ),
      home: const WindHomePage(),
    );
  }
}

/// Handmatig te openen scherm. Toont dezelfde waarden als de
/// spraak-/notificatie-flow, heeft knoppen die exact diezelfde flow
/// (TTS + Android-notificatie, geen zichtbaar scherm) triggeren, én een
/// doorlopende spraakassistent die luistert tot je "stop met luisteren"
/// zegt.
class WindHomePage extends StatefulWidget {
  const WindHomePage({super.key});

  @override
  State<WindHomePage> createState() => _WindHomePageState();
}

class _WindHomePageState extends State<WindHomePage> {
  static const _answersChannel = MethodChannel('nl.vdzon.wind/answers');
  static const _assistantChannel = MethodChannel('nl.vdzon.wind/assistant');
  static const _assistantStatusChannel = EventChannel(
    'nl.vdzon.wind/assistant_status',
  );

  bool _listening = false;
  String _status = 'De assistent luistert nog niet.';

  @override
  void initState() {
    super.initState();
    _assistantStatusChannel.receiveBroadcastStream().listen((event) {
      final text = event as String;
      setState(() {
        _status = text;
        if (text == 'Gestopt met luisteren.') {
          _listening = false;
        } else if (text == 'Ik luister…') {
          _listening = true;
        }
      });
    });
  }

  Future<void> _trigger(String method) async {
    try {
      await _answersChannel.invokeMethod(method);
    } on PlatformException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Kon niet starten: ${e.message}')),
        );
      }
    }
  }

  Future<void> _toggleAssistant() async {
    if (_listening) {
      await _assistantChannel.invokeMethod('stop');
    } else {
      setState(() => _listening = true);
      await _assistantChannel.invokeMethod('start');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Wind')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _AnswerCard(
              icon: Icons.air,
              title: 'Huidige windsnelheid',
              body: WindData.windSpeedAnswer,
              buttonLabel: 'Hoe hard waait het',
              onPressed: () => _trigger('openWindSpeed'),
            ),
            const SizedBox(height: 12),
            _AnswerCard(
              icon: Icons.wb_cloudy,
              title: 'Voorspelling',
              body: WindData.forecastAnswer,
              buttonLabel: 'Wat is de voorspelling',
              onPressed: () => _trigger('openForecast'),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Icon(_listening ? Icons.mic : Icons.mic_off, size: 32),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Text(
                            'Spraakassistent',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(_status),
                    const SizedBox(height: 12),
                    FilledButton.tonal(
                      onPressed: _toggleAssistant,
                      child: Text(
                        _listening ? 'Stop met luisteren' : 'Start luisteren',
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Zeg: "wat is de wind", "wat is de voorspelling" of '
                      '"stop met luisteren".',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AnswerCard extends StatelessWidget {
  const _AnswerCard({
    required this.icon,
    required this.title,
    required this.body,
    required this.buttonLabel,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String body;
  final String buttonLabel;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(icon, size: 32),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: textTheme.titleMedium),
                      const SizedBox(height: 4),
                      Text(body, style: textTheme.bodyLarge),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            FilledButton.tonal(
              onPressed: onPressed,
              child: Text(buttonLabel),
            ),
          ],
        ),
      ),
    );
  }
}
