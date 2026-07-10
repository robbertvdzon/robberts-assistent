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
/// spraak-/notificatie-flow, én heeft knoppen die exact diezelfde flow
/// (TTS + Android-notificatie, geen zichtbaar scherm) triggeren — zo test je
/// met de hand precies het pad dat straks ook "Hey Google" gebruikt.
class WindHomePage extends StatelessWidget {
  const WindHomePage({super.key});

  static const _channel = MethodChannel('nl.vdzon.wind/answers');

  Future<void> _trigger(BuildContext context, String method) async {
    try {
      await _channel.invokeMethod(method);
    } on PlatformException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Kon niet starten: ${e.message}')),
        );
      }
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
              onPressed: () => _trigger(context, 'openWindSpeed'),
            ),
            const SizedBox(height: 12),
            _AnswerCard(
              icon: Icons.wb_cloudy,
              title: 'Voorspelling',
              body: WindData.forecastAnswer,
              buttonLabel: 'Wat is de voorspelling',
              onPressed: () => _trigger(context, 'openForecast'),
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
