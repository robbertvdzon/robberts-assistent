import 'package:flutter/widgets.dart';
import 'package:flutter_quill/flutter_quill.dart' show FlutterQuillLocalizations;

export 'markdown_delta.dart';
export 'note_documents_screen.dart';
export 'note_versions_screen.dart';
export 'notes_api.dart';
export 'notes_editor_screen.dart';

/// De Quill-editor heeft zijn eigen localizations-delegate nodig; een app die
/// de editor toont zet deze in `MaterialApp.localizationsDelegates`.
const LocalizationsDelegate<FlutterQuillLocalizations> notesEditorLocalizationsDelegate =
    FlutterQuillLocalizations.delegate;
