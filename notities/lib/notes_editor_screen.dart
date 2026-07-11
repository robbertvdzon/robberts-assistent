import 'dart:async';

import 'package:flutter/material.dart';

import 'api_client.dart';

/// Toont de ene notitie-string in een bewerkbaar tekstvak. Slaat vanzelf op:
/// - 10 seconden na de laatste toetsaanslag (debounce), of
/// - meteen zodra de app naar de achtergrond gaat of gesloten wordt.
class NotesEditorScreen extends StatefulWidget {
  const NotesEditorScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<NotesEditorScreen> createState() => _NotesEditorScreenState();
}

class _NotesEditorScreenState extends State<NotesEditorScreen> with WidgetsBindingObserver {
  final _controller = TextEditingController();
  Timer? _debounce;
  var _loading = true;
  var _dirty = false;
  String? _error;
  String _status = '';

  static const _debounceDuration = Duration(seconds: 10);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  Future<void> _load() async {
    try {
      final text = await widget.api.getNotes();
      if (mounted) {
        setState(() {
          _controller.text = text;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  void _onChanged(String _) {
    _dirty = true;
    setState(() => _status = '');
    _debounce?.cancel();
    _debounce = Timer(_debounceDuration, _save);
  }

  Future<void> _save() async {
    if (!_dirty) return;
    _debounce?.cancel();
    _dirty = false;
    final text = _controller.text;
    try {
      await widget.api.saveNotes(text);
      if (mounted) setState(() => _status = 'Opgeslagen');
    } catch (e) {
      // Niet-opgeslagen wijzigingen blijven gewoon in het tekstvak staan; de
      // volgende debounce-tik of app-pauze probeert opnieuw.
      _dirty = true;
      if (mounted) setState(() => _status = 'Opslaan mislukt: $e');
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      _save();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _debounce?.cancel();
    if (_dirty) {
      // Best-effort: geen await mogelijk in dispose().
      widget.api.saveNotes(_controller.text);
    }
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notities'),
        actions: [
          if (_status.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Center(child: Text(_status, style: const TextStyle(fontSize: 12))),
            ),
          IconButton(
            tooltip: 'Uitloggen',
            icon: const Icon(Icons.logout),
            onPressed: widget.onLoggedOut,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
          : Padding(
              padding: const EdgeInsets.all(16),
              child: TextField(
                controller: _controller,
                onChanged: _onChanged,
                maxLines: null,
                expands: true,
                textAlignVertical: TextAlignVertical.top,
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  hintText: 'Typ hier je notities…',
                ),
              ),
            ),
    );
  }
}
