import 'package:flutter/material.dart';

import 'api_client.dart';
import 'fcm_service.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key, required this.api});
  final ApiClient api;

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen>
    with WidgetsBindingObserver {
  List<AppNotification> _items = [];
  String? _nextCursor;
  String? _error;
  bool _loading = false;
  bool _opening = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load({bool more = false}) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await widget.api.notifications(
        before: more ? _nextCursor : null,
      );
      if (!mounted) return;
      setState(() {
        // Een refresh vervangt de lijst, verder bladeren voegt alleen onbekende id's toe.
        final known = more ? _items.map((item) => item.id).toSet() : <String>{};
        _items = [
          if (more) ..._items,
          ...page.items.where((item) => known.add(item.id)),
        ];
        _nextCursor = page.nextCursor;
      });
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Meldingen laden is mislukt. Probeer het opnieuw.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _time(DateTime time) {
    final local = time.toLocal();
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(local.day)}-${two(local.month)}-${local.year} om ${two(local.hour)}:${two(local.minute)}';
  }

  Future<void> _open(AppNotification item) async {
    if (_opening) return;
    _opening = true;
    try {
      if (item.readAt == null) {
        try {
          final read = await widget.api.markNotificationRead(item.id);
          if (!mounted) return;
          setState(
            () => _items = [
              for (final old in _items)
                if (old.id == read.id) read else old,
            ],
          );
        } catch (_) {
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Als gelezen markeren is mislukt.')),
          );
        }
      }
      if (!mounted) return;
      final openTarget = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(item.title),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _time(item.createdAt),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                SelectableText(item.body),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Sluiten'),
            ),
            if (FcmService.targetForPushData(item.data) != null)
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Openen'),
              ),
          ],
        ),
      );
      if (openTarget == true && mounted) FcmService.handlePushData(item.data);
    } finally {
      _opening = false;
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Meldingen'),
      actions: [
        IconButton(
          tooltip: 'Vernieuwen',
          onPressed: _loading ? null : () => _load(),
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: () => _load(),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Je meldingen blijven hier staan, ook nadat je ze op je telefoon hebt geopend.',
            ),
          ),
          if (_loading && _items.isEmpty)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: CircularProgressIndicator(),
              ),
            ),
          if (!_loading && _error == null && _items.isEmpty)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Text(
                'Nog geen meldingen. Nieuwe meldingen worden hier bewaard.',
              ),
            ),
          for (final item in _items)
            ListTile(
              leading: Icon(
                item.readAt == null
                    ? Icons.notifications_active_outlined
                    : Icons.notifications_none,
                semanticLabel: item.readAt == null ? 'Ongelezen' : 'Gelezen',
                color: item.readAt == null
                    ? Theme.of(context).colorScheme.primary
                    : null,
              ),
              title: Text(
                item.title,
                style: TextStyle(
                  fontWeight: item.readAt == null
                      ? FontWeight.bold
                      : FontWeight.normal,
                ),
              ),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_time(item.createdAt)),
                  Text(item.body, maxLines: 2, overflow: TextOverflow.ellipsis),
                ],
              ),
              isThreeLine: true,
              onTap: () => _open(item),
            ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(_error!),
                  TextButton(
                    onPressed: () =>
                        _load(more: _items.isNotEmpty && _nextCursor != null),
                    child: const Text('Opnieuw proberen'),
                  ),
                ],
              ),
            ),
          if (_nextCursor != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: OutlinedButton(
                onPressed: _loading ? null : () => _load(more: true),
                child: Text(_loading ? 'Laden…' : 'Oudere meldingen laden'),
              ),
            ),
        ],
      ),
    ),
  );
}
