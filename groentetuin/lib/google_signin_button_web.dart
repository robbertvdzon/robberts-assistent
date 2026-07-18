import 'package:flutter/widgets.dart';
import 'package:google_sign_in_web/web_only.dart' as web_only;

/// De officiële Google Identity Services-knop. `GoogleSignIn.signIn()` is op web deprecated;
/// GIS vereist de eigen gerenderde knop, een geslaagde login komt binnen via `onCurrentUserChanged`.
Widget renderGoogleButton() => web_only.renderButton();
