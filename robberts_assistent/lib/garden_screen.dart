import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'api_client.dart';

const _gardenMonths = [
  'jan',
  'feb',
  'mrt',
  'apr',
  'mei',
  'jun',
  'jul',
  'aug',
  'sep',
  'okt',
  'nov',
  'dec',
];

String gardenDate(DateTime d) =>
    '${d.day} ${_gardenMonths[d.month - 1]} ${d.year}';

Future<List<AssistantAttachment>> _attachments(List<XFile> files) async {
  final result = <AssistantAttachment>[];
  for (final file in files) {
    final png = file.name.toLowerCase().endsWith('.png');
    result.add(
      AssistantAttachment(
        bytes: await file.readAsBytes(),
        filename: file.name.isEmpty ? 'foto.jpg' : file.name,
        contentType: file.mimeType ?? (png ? 'image/png' : 'image/jpeg'),
      ),
    );
  }
  return result;
}

/// Foto's kiezen: camera of galerij. Geeft een lege lijst als er niets is gekozen.
Future<List<AssistantAttachment>> pickGardenPhotos(BuildContext context) async {
  final source = await showModalBottomSheet<ImageSource>(
    context: context,
    builder: (sheet) => SafeArea(
      child: Wrap(
        children: [
          ListTile(
            leading: const Icon(Icons.photo_camera_outlined),
            title: const Text('Foto maken'),
            onTap: () => Navigator.pop(sheet, ImageSource.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: const Text('Uit galerij kiezen'),
            onTap: () => Navigator.pop(sheet, ImageSource.gallery),
          ),
        ],
      ),
    ),
  );
  if (source == null) return const [];
  final picker = ImagePicker();
  try {
    if (source == ImageSource.camera) {
      final file = await picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
        maxWidth: 1600,
        maxHeight: 1600,
      );
      return file == null ? const [] : _attachments([file]);
    }
    return _attachments(
      await picker.pickMultiImage(
        imageQuality: 80,
        maxWidth: 1600,
        maxHeight: 1600,
      ),
    );
  } catch (_) {
    return const [];
  }
}

/// Een moestuinfoto van de backend, met een kleine cache zodat scrollen niet opnieuw ophaalt.
class GardenPhoto extends StatefulWidget {
  const GardenPhoto({
    super.key,
    required this.api,
    required this.photoId,
    this.fit = BoxFit.cover,
  });

  final ApiClient api;
  final String photoId;
  final BoxFit fit;

  @override
  State<GardenPhoto> createState() => _GardenPhotoState();
}

class _GardenPhotoState extends State<GardenPhoto> {
  static final _cache = <String, Uint8List>{};
  Uint8List? _bytes;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void didUpdateWidget(GardenPhoto old) {
    super.didUpdateWidget(old);
    if (old.photoId != widget.photoId) _fetch();
  }

  Future<void> _fetch() async {
    final id = widget.photoId;
    final cached = _cache[id];
    if (cached != null) {
      setState(() => _bytes = cached);
      return;
    }
    try {
      final bytes = await widget.api.fetchGardenPhoto(id);
      _cache[id] = bytes;
      if (mounted && widget.photoId == id) setState(() => _bytes = bytes);
    } catch (_) {
      // Een ontbrekende foto mag het scherm niet breken; het vlak blijft leeg.
    }
  }

  @override
  Widget build(BuildContext context) {
    final bytes = _bytes;
    if (bytes == null) {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
      );
    }
    return Image.memory(
      bytes,
      fit: widget.fit,
      errorBuilder: (_, _, _) => const SizedBox.shrink(),
    );
  }
}

/// De sectie Moestuin: de plattegrond met de bakken en de vragen aan de moestuin-assistent.
class GardenScreen extends StatelessWidget {
  const GardenScreen({super.key, required this.api});

  final ApiClient api;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Moestuin'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Plattegrond'),
              Tab(text: 'Vragen'),
            ],
          ),
        ),
        body: TabBarView(
          // Slepen op de plattegrond mag geen tabwissel worden.
          physics: const NeverScrollableScrollPhysics(),
          children: [
            GardenPlanView(api: api),
            GardenChat(api: api),
          ],
        ),
      ),
    );
  }
}

/// De plattegrond: de bakken als vlakken op een (optionele) foto of tekening van de tuin. Tikken
/// opent een bak; in de bewerkstand zijn bakken te verslepen en te vergroten.
class GardenPlanView extends StatefulWidget {
  const GardenPlanView({super.key, required this.api});

  final ApiClient api;

  @override
  State<GardenPlanView> createState() => _GardenPlanViewState();
}

class _GardenPlanViewState extends State<GardenPlanView> {
  GardenPlan? _plan;
  List<GardenBed> _beds = [];
  String? _error;
  bool _editing = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final plan = await widget.api.getGardenPlan();
      if (mounted) {
        setState(() {
          _plan = plan;
          _beds = plan.beds;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'De plattegrond kon niet worden geladen.');
      }
    }
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _addBed() async {
    final name = await _askName(context, title: 'Nieuwe bak');
    if (name == null) return;
    try {
      // Midden op de plattegrond; daarna te verslepen.
      final bed = await widget.api.createGardenBed(
        GardenBed(id: '', name: name, x: 0.4, y: 0.4, width: 0.2, height: 0.2),
      );
      if (mounted) {
        setState(() {
          _beds = [..._beds, bed];
          _editing = true;
        });
      }
    } catch (_) {
      _snack('De bak kon niet worden toegevoegd.');
    }
  }

  Future<void> _chooseBackground() async {
    final photos = await pickGardenPhotos(context);
    if (photos.isEmpty) return;
    try {
      final image = await decodeImageFromList(photos.first.bytes);
      final plan = await widget.api.setGardenBackground(
        photos.first,
        image.width / image.height,
      );
      if (mounted) setState(() => _plan = plan);
    } catch (_) {
      _snack('De achtergrond kon niet worden opgeslagen.');
    }
  }

  Future<void> _open(GardenBed bed) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => GardenBedScreen(api: widget.api, bedId: bed.id),
      ),
    );
    await _load();
  }

  void _change(GardenBed bed, GardenBed changed) {
    setState(
      () => _beds = [for (final b in _beds) b.id == bed.id ? changed : b],
    );
  }

  Future<void> _save(String bedId) async {
    final bed = _beds.firstWhere((b) => b.id == bedId);
    try {
      await widget.api.updateGardenBed(bed);
    } catch (_) {
      _snack('De plek van de bak kon niet worden opgeslagen.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final plan = _plan;
    if (plan == null) {
      return Center(
        child: _error != null
            ? Text(_error!)
            : const CircularProgressIndicator(),
      );
    }
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    return ListView(
      padding: EdgeInsets.fromLTRB(
        16,
        12,
        16,
        16 + MediaQuery.viewPaddingOf(context).bottom,
      ),
      // Tijdens het bewerken sleep je bakken; de lijst mag dan niet meescrollen.
      physics: _editing ? const NeverScrollableScrollPhysics() : null,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 4,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            FilterChip(
              key: const ValueKey('plattegrond-bewerken'),
              label: const Text('Bewerken'),
              selected: _editing,
              onSelected: (v) => setState(() => _editing = v),
            ),
            ActionChip(
              key: const ValueKey('bak-toevoegen'),
              avatar: const Icon(Icons.add, size: 18),
              label: const Text('Bak'),
              onPressed: _addBed,
            ),
            ActionChip(
              avatar: const Icon(Icons.image_outlined, size: 18),
              label: const Text('Achtergrond'),
              onPressed: _chooseBackground,
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          _editing
              ? 'Sleep een bak om hem te verplaatsen; sleep het hoekje rechtsonder om hem groter of kleiner te maken.'
              : _beds.isEmpty
              ? 'Kies een foto of tekening van je tuin als achtergrond en voeg je bakken toe.'
              : 'Tik op een bak om te zien wat erin staat of om foto\'s toe te voegen.',
          style: muted,
        ),
        const SizedBox(height: 8),
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: AspectRatio(
            aspectRatio: plan.aspectRatio,
            child: LayoutBuilder(
              builder: (context, constraints) {
                final w = constraints.maxWidth;
                final h = constraints.maxHeight;
                return Stack(
                  key: const ValueKey('plattegrond'),
                  children: [
                    Positioned.fill(
                      child: plan.backgroundPhotoId.isEmpty
                          ? ColoredBox(color: const Color(0xFFE6EEDF))
                          : GardenPhoto(
                              api: widget.api,
                              photoId: plan.backgroundPhotoId,
                              fit: BoxFit.fill,
                            ),
                    ),
                    for (final bed in _beds)
                      Positioned(
                        left: bed.x * w,
                        top: bed.y * h,
                        width: bed.width * w,
                        height: bed.height * h,
                        child: _bed(bed, w, h),
                      ),
                  ],
                );
              },
            ),
          ),
        ),
        const SizedBox(height: 16),
        for (final bed in _beds)
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.grid_view_outlined),
            title: Text(bed.name),
            subtitle: Text(
              bed.entries.isEmpty
                  ? 'Nog niets vastgelegd'
                  : '${gardenDate(bed.entries.first.date)}: '
                        '${bed.entries.first.text.isEmpty ? 'foto\'s' : bed.entries.first.text}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            onTap: () => _open(bed),
          ),
      ],
    );
  }

  Widget _bed(GardenBed bed, double w, double h) {
    final scheme = Theme.of(context).colorScheme;
    final box = Container(
      decoration: BoxDecoration(
        color: scheme.primary.withValues(alpha: _editing ? 0.55 : 0.4),
        border: Border.all(color: scheme.primary, width: 2),
        borderRadius: BorderRadius.circular(6),
      ),
      alignment: Alignment.center,
      padding: const EdgeInsets.all(2),
      child: Text(
        bed.name,
        textAlign: TextAlign.center,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: scheme.onPrimary,
          fontWeight: FontWeight.w700,
          fontSize: 12,
          shadows: const [Shadow(blurRadius: 3)],
        ),
      ),
    );
    if (!_editing) {
      return GestureDetector(
        key: ValueKey('bak-${bed.id}'),
        onTap: () => _open(bed),
        child: box,
      );
    }
    return Stack(
      children: [
        Positioned.fill(
          child: GestureDetector(
            key: ValueKey('bak-${bed.id}'),
            onPanUpdate: (d) => _change(
              bed,
              bed.copyWith(
                x: (bed.x + d.delta.dx / w).clamp(0.0, 1.0 - bed.width),
                y: (bed.y + d.delta.dy / h).clamp(0.0, 1.0 - bed.height),
              ),
            ),
            onPanEnd: (_) => _save(bed.id),
            child: box,
          ),
        ),
        Positioned(
          right: 0,
          bottom: 0,
          child: GestureDetector(
            key: ValueKey('bak-formaat-${bed.id}'),
            onPanUpdate: (d) => _change(
              bed,
              bed.copyWith(
                width: (bed.width + d.delta.dx / w).clamp(0.05, 1.0 - bed.x),
                height: (bed.height + d.delta.dy / h).clamp(0.05, 1.0 - bed.y),
              ),
            ),
            onPanEnd: (_) => _save(bed.id),
            child: Container(
              width: 22,
              height: 22,
              decoration: BoxDecoration(
                color: scheme.primary,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Icon(
                Icons.open_in_full,
                size: 14,
                color: scheme.onPrimary,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

Future<String?> _askName(
  BuildContext context, {
  required String title,
  String initial = '',
}) {
  final controller = TextEditingController(text: initial);
  return showDialog<String>(
    context: context,
    builder: (dialog) => AlertDialog(
      title: Text(title),
      content: TextField(
        key: const ValueKey('bak-naam'),
        controller: controller,
        autofocus: true,
        decoration: const InputDecoration(labelText: 'Naam van de bak'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialog),
          child: const Text('Annuleren'),
        ),
        FilledButton(
          onPressed: () {
            final name = controller.text.trim();
            if (name.isNotEmpty) Navigator.pop(dialog, name);
          },
          child: const Text('Opslaan'),
        ),
      ],
    ),
  );
}

/// Eén bak: de geschiedenis van wat erin heeft gestaan, met foto's. Hier leg je vast wat er nu
/// staat en stel je vragen over deze bak.
class GardenBedScreen extends StatefulWidget {
  const GardenBedScreen({super.key, required this.api, required this.bedId});

  final ApiClient api;
  final String bedId;

  @override
  State<GardenBedScreen> createState() => _GardenBedScreenState();
}

class _GardenBedScreenState extends State<GardenBedScreen> {
  GardenBed? _bed;
  String? _error;
  final _text = TextEditingController();
  final _photos = <AssistantAttachment>[];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final bed = await widget.api.getGardenBed(widget.bedId);
      if (mounted) setState(() => _bed = bed);
    } catch (_) {
      if (mounted) setState(() => _error = 'De bak kon niet worden geladen.');
    }
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _pick() async {
    final photos = await pickGardenPhotos(context);
    if (mounted && photos.isNotEmpty) setState(() => _photos.addAll(photos));
  }

  Future<void> _saveEntry() async {
    if (_text.text.trim().isEmpty && _photos.isEmpty) return;
    setState(() => _saving = true);
    try {
      final bed = await widget.api.addGardenBedEntry(
        widget.bedId,
        text: _text.text.trim(),
        photos: List.of(_photos),
      );
      if (!mounted) return;
      setState(() {
        _bed = bed;
        _text.clear();
        _photos.clear();
      });
    } catch (_) {
      _snack('Vastleggen is mislukt.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _rename() async {
    final bed = _bed;
    if (bed == null) return;
    final name = await _askName(
      context,
      title: 'Naam wijzigen',
      initial: bed.name,
    );
    if (name == null) return;
    try {
      final updated = await widget.api.updateGardenBed(
        bed.copyWith(name: name),
      );
      if (mounted) setState(() => _bed = updated);
    } catch (_) {
      _snack('De naam kon niet worden gewijzigd.');
    }
  }

  Future<void> _delete() async {
    final sure = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: const Text('Bak verwijderen?'),
        content: const Text(
          'De bak en alles wat erover is vastgelegd verdwijnt van de plattegrond.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, true),
            child: const Text('Verwijderen'),
          ),
        ],
      ),
    );
    if (sure != true) return;
    try {
      await widget.api.deleteGardenBed(widget.bedId);
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      _snack('Verwijderen is mislukt.');
    }
  }

  Future<void> _deleteEntry(GardenBedEntry entry) async {
    try {
      await widget.api.deleteGardenBedEntry(widget.bedId, entry.id);
      await _load();
    } catch (_) {
      _snack('Verwijderen is mislukt.');
    }
  }

  void _showPhoto(String photoId) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            backgroundColor: Colors.black,
            foregroundColor: Colors.white,
          ),
          body: Center(
            child: InteractiveViewer(
              child: GardenPhoto(
                api: widget.api,
                photoId: photoId,
                fit: BoxFit.contain,
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bed = _bed;
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    return Scaffold(
      appBar: AppBar(
        title: Text(bed?.name ?? 'Bak'),
        actions: [
          if (bed != null)
            PopupMenuButton<String>(
              onSelected: (v) => v == 'naam' ? _rename() : _delete(),
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'naam', child: Text('Naam wijzigen')),
                PopupMenuItem(value: 'weg', child: Text('Bak verwijderen')),
              ],
            ),
        ],
      ),
      body: bed == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : ListView(
              padding: EdgeInsets.fromLTRB(
                16,
                16,
                16,
                16 + MediaQuery.viewPaddingOf(context).bottom,
              ),
              children: [
                Text('Vastleggen', style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                TextField(
                  key: const ValueKey('bak-tekst'),
                  controller: _text,
                  minLines: 1,
                  maxLines: 4,
                  decoration: const InputDecoration(
                    labelText: 'Wat staat er nu in?',
                    border: OutlineInputBorder(),
                  ),
                ),
                if (_photos.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 72,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: _photos.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 6),
                      itemBuilder: (_, i) => GestureDetector(
                        onTap: () => setState(() => _photos.removeAt(i)),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: Image.memory(
                            _photos[i].bytes,
                            width: 72,
                            height: 72,
                            fit: BoxFit.cover,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 8),
                Row(
                  children: [
                    OutlinedButton.icon(
                      onPressed: _saving ? null : _pick,
                      icon: const Icon(Icons.add_a_photo_outlined),
                      label: const Text('Foto\'s'),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: _saving ? null : _saveEntry,
                      child: _saving
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Vastleggen'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => Scaffold(
                          appBar: AppBar(title: Text('Vraag over ${bed.name}')),
                          body: GardenChat(api: widget.api, bed: bed),
                        ),
                      ),
                    ),
                    icon: const Icon(Icons.chat_outlined),
                    label: const Text('Vraag stellen over deze bak'),
                  ),
                ),
                const Divider(height: 32),
                Text('Geschiedenis', style: theme.textTheme.titleSmall),
                if (bed.entries.isEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text('Nog niets vastgelegd.', style: muted),
                  ),
                for (final entry in bed.entries)
                  Padding(
                    padding: const EdgeInsets.only(top: 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(gardenDate(entry.date), style: muted),
                            ),
                            IconButton(
                              tooltip: 'Verwijderen',
                              visualDensity: VisualDensity.compact,
                              icon: const Icon(Icons.delete_outline, size: 20),
                              onPressed: () => _deleteEntry(entry),
                            ),
                          ],
                        ),
                        if (entry.text.isNotEmpty) Text(entry.text),
                        if (entry.photoIds.isNotEmpty) ...[
                          const SizedBox(height: 6),
                          Wrap(
                            spacing: 6,
                            runSpacing: 6,
                            children: [
                              for (final id in entry.photoIds)
                                GestureDetector(
                                  onTap: () => _showPhoto(id),
                                  child: ClipRRect(
                                    borderRadius: BorderRadius.circular(8),
                                    child: SizedBox(
                                      width: 104,
                                      height: 104,
                                      child: GardenPhoto(
                                        api: widget.api,
                                        photoId: id,
                                      ),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
              ],
            ),
    );
  }
}

class _GardenMessage {
  _GardenMessage({
    required this.fromUser,
    required this.text,
    this.images = const [],
  });

  final bool fromUser;
  final String text;
  final List<Uint8List> images;
}

/// Vragen aan de moestuin-assistent, met foto's. Met [bed] gaat het gesprek over die bak: de
/// assistent kent dan de geschiedenis en de laatste foto's ervan.
class GardenChat extends StatefulWidget {
  const GardenChat({super.key, required this.api, this.bed});

  final ApiClient api;
  final GardenBed? bed;

  @override
  State<GardenChat> createState() => _GardenChatState();
}

class _GardenChatState extends State<GardenChat>
    with AutomaticKeepAliveClientMixin {
  final _controller = TextEditingController();
  final _scroll = ScrollController();
  final _messages = <_GardenMessage>[];
  final _pending = <AssistantAttachment>[];
  String? _conversationId;
  bool _sending = false;
  String? _error;

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final photos = await pickGardenPhotos(context);
    if (mounted && photos.isNotEmpty) setState(() => _pending.addAll(photos));
  }

  Future<void> _send() async {
    final text = _controller.text.trim();
    if (text.isEmpty && _pending.isEmpty) return;
    final photos = List.of(_pending);
    setState(() {
      _messages.add(
        _GardenMessage(
          fromUser: true,
          text: text,
          images: [for (final p in photos) p.bytes],
        ),
      );
      _pending.clear();
      _controller.clear();
      _sending = true;
      _error = null;
    });
    _toBottom();
    try {
      final reply = await widget.api.gardenChat(
        message: text,
        conversationId: _conversationId,
        bedId: widget.bed?.id,
        photos: photos,
      );
      if (!mounted) return;
      setState(() {
        _conversationId = reply.conversationId;
        _messages.add(_GardenMessage(fromUser: false, text: reply.reply));
      });
    } catch (_) {
      if (mounted) setState(() => _error = 'Versturen is mislukt.');
    } finally {
      if (mounted) setState(() => _sending = false);
      _toBottom();
    }
  }

  void _toBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final scheme = Theme.of(context).colorScheme;
    return Column(
      children: [
        Expanded(
          child: _messages.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      widget.bed == null
                          ? 'Stel een vraag over je moestuin.\nJe kunt er foto\'s bij sturen. '
                                'De assistent weet wat er in je bakken staat.'
                          : 'Stel een vraag over ${widget.bed!.name}.\nDe assistent kent de '
                                'geschiedenis en de laatste foto\'s van deze bak.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: scheme.onSurfaceVariant),
                    ),
                  ),
                )
              : SelectionArea(
                  child: ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.all(12),
                    itemCount: _messages.length,
                    itemBuilder: (_, i) => _bubble(_messages[i], scheme),
                  ),
                ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Text(_error!, style: TextStyle(color: scheme.error)),
          ),
        if (_pending.isNotEmpty)
          SizedBox(
            height: 76,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              scrollDirection: Axis.horizontal,
              itemCount: _pending.length,
              separatorBuilder: (_, _) => const SizedBox(width: 6),
              itemBuilder: (_, i) => GestureDetector(
                onTap: () => setState(() => _pending.removeAt(i)),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.memory(
                    _pending[i].bytes,
                    width: 64,
                    height: 64,
                    fit: BoxFit.cover,
                  ),
                ),
              ),
            ),
          ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: Row(
              children: [
                IconButton(
                  onPressed: _sending ? null : _pick,
                  icon: const Icon(Icons.add_a_photo_outlined),
                  tooltip: 'Foto toevoegen',
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('moestuin-vraag'),
                    controller: _controller,
                    minLines: 1,
                    maxLines: 4,
                    decoration: const InputDecoration(
                      hintText: 'Stel je vraag',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                _sending
                    ? const Padding(
                        padding: EdgeInsets.all(10),
                        child: SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                      )
                    : IconButton.filled(
                        key: const ValueKey('moestuin-verstuur'),
                        onPressed: _send,
                        icon: const Icon(Icons.send),
                      ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _bubble(_GardenMessage m, ColorScheme scheme) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: m.fromUser
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          if (m.images.isNotEmpty)
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final bytes in m.images)
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.memory(
                      bytes,
                      width: 96,
                      height: 96,
                      fit: BoxFit.cover,
                    ),
                  ),
              ],
            ),
          if (m.text.isNotEmpty)
            Container(
              margin: const EdgeInsets.only(top: 4),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              constraints: const BoxConstraints(maxWidth: 320),
              decoration: BoxDecoration(
                color: m.fromUser
                    ? scheme.primaryContainer
                    : scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Text(m.text),
            ),
        ],
      ),
    );
  }
}
