import 'package:flutter/material.dart';

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

/// Eenvoudig, handmatig te openen scherm dat dezelfde waarden toont als de
/// spraak-/notificatie-flow. Zo is de app ook zonder "Hey Google" te testen.
class WindHomePage extends StatelessWidget {
  const WindHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Wind')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: const [
            _AnswerCard(
              icon: Icons.air,
              title: 'Huidige windsnelheid',
              body: WindData.windSpeedAnswer,
            ),
            SizedBox(height: 12),
            _AnswerCard(
              icon: Icons.wb_cloudy,
              title: 'Voorspelling',
              body: WindData.forecastAnswer,
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
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
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
      ),
    );
  }
}
