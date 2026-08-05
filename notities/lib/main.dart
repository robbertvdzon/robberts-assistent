import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart' show FlutterQuillLocalizations;
import 'package:google_sign_in/google_sign_in.dart';

import 'api_client.dart';
import 'notes_editor_screen.dart';

void main() {
  runApp(const NotitiesApp());
}

/// De OAuth-web-client-ID komt via een build-time waarde (`--dart-define=GOOGLE_CLIENT_ID=...`).
const googleClientId = String.fromEnvironment('GOOGLE_CLIENT_ID', defaultValue: '');

/// Donker thema: zwarte achtergrond met witte, goed leesbare letters.
final notitiesDarkTheme = ThemeData(
  brightness: Brightness.dark,
  useMaterial3: true,
  scaffoldBackgroundColor: Colors.black,
  colorScheme: const ColorScheme.dark(surface: Colors.black),
  appBarTheme: const AppBarTheme(
    backgroundColor: Colors.black,
    foregroundColor: Colors.white,
    iconTheme: IconThemeData(color: Colors.white),
  ),
  textSelectionTheme: const TextSelectionThemeData(
    cursorColor: Colors.white,
    selectionColor: Color(0x66FFFFFF),
    selectionHandleColor: Colors.white,
  ),
  inputDecorationTheme: const InputDecorationTheme(
    hintStyle: TextStyle(color: Colors.white54),
  ),
);

/// Achtergrond van het bewerkbare tekstvlak in de editor: iets lichter dan het
/// zwart van de AppBar en de opmaakbalk, zodat zichtbaar is waar de menu's
/// ophouden en de notitie begint.
///
/// Bewust een losse constante en géén onderdeel van [notitiesDarkTheme]: het
/// thema (scaffold, surface, AppBar) blijft zwart, zodat de overige schermen
/// — documentenlijst, versiegeschiedenis en inloggen — ongewijzigd blijven.
const notitiesEditorBackground = Color(0xFF404040);

class NotitiesApp extends StatelessWidget {
  const NotitiesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Notities',
      theme: notitiesDarkTheme,
      // flutter_quill heeft zijn eigen localizations-delegate nodig.
      localizationsDelegates: FlutterQuillLocalizations.localizationsDelegates,
      supportedLocales: FlutterQuillLocalizations.supportedLocales,
      home: const RootScreen(),
    );
  }
}

/// Laadt de sessie en toont ofwel het login-scherm, ofwel de notitie-editor.
class RootScreen extends StatefulWidget {
  const RootScreen({super.key});

  @override
  State<RootScreen> createState() => _RootScreenState();
}

class _RootScreenState extends State<RootScreen> {
  final api = ApiClient();
  final googleSignIn = GoogleSignIn(
    clientId: googleClientId.isEmpty ? null : googleClientId,
    scopes: const ['email'],
  );
  var initialized = false;
  var loading = false;
  String? error;

  @override
  void initState() {
    super.initState();
    _restoreSession();
  }

  Future<void> _restoreSession() async {
    await api.restoreSession();
    if (!mounted) return;
    setState(() => initialized = true);
  }

  Future<void> _loginWithGoogle() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final account = await googleSignIn.signIn();
      if (account == null) return; // Gebruiker annuleerde de Google-popup.
      final auth = await account.authentication;
      final idToken = auth.idToken;
      if (idToken == null) {
        throw Exception('Geen Google ID-token ontvangen. Controleer de OAuth-client-ID.');
      }
      await api.loginWithGoogle(idToken);
    } catch (e) {
      error = e.toString();
      await googleSignIn.signOut().catchError((_) => null);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _logout() async {
    await api.clearSession();
    await googleSignIn.signOut().catchError((_) => null);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    if (!initialized) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (api.token == null) return _loginView();
    return NotesEditorScreen(api: api, onLoggedOut: _logout);
  }

  Widget _loginView() => Scaffold(
    body: Center(
      child: SizedBox(
        width: 420,
        child: Card(
          color: const Color(0xFF1E1E1E),
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.edit_note, size: 56, color: Colors.white),
                const SizedBox(height: 16),
                const Text(
                  'Notities',
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: Colors.white),
                ),
                const SizedBox(height: 4),
                const Text('Log in met Google om verder te gaan.', style: TextStyle(color: Colors.white70)),
                const SizedBox(height: 24),
                FilledButton.icon(
                  onPressed: loading ? null : _loginWithGoogle,
                  icon: loading
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.login),
                  label: Text(loading ? 'Inloggen...' : 'Inloggen met Google'),
                ),
                if (error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text(error!, style: const TextStyle(color: Colors.red)),
                  ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
