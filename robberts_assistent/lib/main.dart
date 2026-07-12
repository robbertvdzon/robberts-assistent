import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'api_client.dart';
import 'google_signin_button_stub.dart' if (dart.library.html) 'google_signin_button_web.dart' as gis_button;
import 'home_screen.dart';

void main() {
  runApp(const RobbertsAssistentApp());
}

/// De OAuth-web-client-ID komt via een build-time waarde (`--dart-define=GOOGLE_CLIENT_ID=...`).
const googleClientId = String.fromEnvironment('GOOGLE_CLIENT_ID', defaultValue: '');

/// Alleen `true` op PR-preview-builds. De preview-backend accepteert daar toch elke
/// (of geen) Authorization-header (`RA_PREVIEW_SKIP_GOOGLE_AUTH`), maar dit scherm wist
/// daar niets van en zou anders altijd een échte Google-popup blijven eisen — waardoor
/// een tester-agent zonder Google-account nooit voorbij het loginscherm komt.
const skipGoogleAuthPreview = bool.fromEnvironment('SKIP_GOOGLE_AUTH', defaultValue: false);

class RobbertsAssistentApp extends StatelessWidget {
  const RobbertsAssistentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Robberts Assistent',
      theme: ThemeData(colorSchemeSeed: Colors.deepPurple, useMaterial3: true),
      home: const RootScreen(),
    );
  }
}

/// Laadt de sessie en toont ofwel het login-scherm, ofwel de app-shell.
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
    if (kIsWeb) {
      googleSignIn.onCurrentUserChanged.listen(_handleGoogleAccount);
    }
  }

  Future<void> _handleGoogleAccount(GoogleSignInAccount? account) async {
    if (account == null || loading) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
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

  Future<void> _restoreSession() async {
    await api.restoreSession();
    if (skipGoogleAuthPreview && api.token == null) {
      api.token = 'preview';
    }
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
    return HomeScreen(api: api, onLoggedOut: _logout);
  }

  Widget _loginView() => Scaffold(
    body: Center(
      child: SizedBox(
        width: 420,
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.assistant, size: 56, color: Colors.deepPurple),
                const SizedBox(height: 16),
                const Text('Robberts Assistent', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                const Text('Log in met Google om verder te gaan.', style: TextStyle(color: Colors.black54)),
                const SizedBox(height: 24),
                if (kIsWeb)
                  Center(
                    child: loading
                        ? const Padding(
                            padding: EdgeInsets.symmetric(vertical: 10),
                            child: SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2)),
                          )
                        : SizedBox(height: 40, child: gis_button.renderGoogleButton()),
                  )
                else
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
