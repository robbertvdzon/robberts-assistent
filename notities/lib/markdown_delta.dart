/// Conversie tussen de platte markdown-tekst die de backend opslaat en het
/// Quill-`Delta`-formaat dat de editor gebruikt.
///
/// De notitie blijft server-side bewust één platte tekst-string (de assistent en
/// de dagelijkse briefing lezen 'm rechtstreeks), dus er wordt nooit Delta-JSON
/// weggeschreven. De ondersteunde opmaak is bewust minimaal:
///
/// - vet          → `**tekst**`
/// - cursief      → `*tekst*`
/// - onderstreept → `<u>tekst</u>`
/// - opsomming    → regel die begint met exact `- `
///
/// Alles daarbuiten (kopjes met `#`, tabellen, links, code, inspringingen,
/// genummerde lijsten, `* `-bullets) is platte tekst: het wordt letterlijk
/// getoond en letterlijk teruggeschreven. Er wordt niets ge-escaped en er
/// worden geen regels toegevoegd of weggegooid, zodat
/// `deltaToMarkdown(markdownToDelta(s)) == s` byte-identiek geldt voor elke
/// notitie zonder opmaak-markers.
///
/// Dit bestand heeft bewust geen Flutter-widget-afhankelijkheden, zodat de
/// conversie puur als unit-test te draaien is.
library;

import 'package:flutter_quill/quill_delta.dart';

const _bulletPrefix = '- ';
const _listAttributeKey = 'list';
const _bulletAttributeValue = 'bullet';

const _boldMarker = '**';
const _italicMarker = '*';
const _boldItalicMarker = '***';
const _underlineOpen = '<u>';
const _underlineClose = '</u>';

/// Zet een markdown-string om naar een Quill-`Delta`.
///
/// Elke regel wordt afzonderlijk geparseerd; opmaak-markers lopen niet over
/// regelgrenzen heen. De teruggegeven delta eindigt altijd op een newline,
/// zoals Quill vereist.
Delta markdownToDelta(String markdown) {
  final delta = Delta();
  for (final line in markdown.split('\n')) {
    var content = line;
    Map<String, dynamic>? blockAttributes;
    if (content.startsWith(_bulletPrefix)) {
      blockAttributes = {_listAttributeKey: _bulletAttributeValue};
      content = content.substring(_bulletPrefix.length);
    }
    for (final segment in _parseInline(content, const _Marks())) {
      if (segment.text.isEmpty) continue;
      delta.insert(segment.text, segment.marks.toAttributes());
    }
    delta.insert('\n', blockAttributes);
  }
  return delta;
}

/// Zet een Quill-`Delta` terug om naar een markdown-string.
///
/// De afsluitende newline die Quill intern altijd aanhoudt wordt afgeknipt,
/// zodat de roundtrip byte-identiek blijft.
String deltaToMarkdown(Delta delta) {
  final lines = <_Line>[];
  var current = <_Segment>[];
  for (final op in delta.toList()) {
    if (!op.isInsert) continue;
    final data = op.data;
    // Embeds (afbeeldingen e.d.) bestaan niet in deze app; negeren is veiliger
    // dan er JSON van maken.
    if (data is! String) continue;
    final marks = _Marks.fromAttributes(op.attributes);
    final parts = data.split('\n');
    for (var i = 0; i < parts.length; i++) {
      if (parts[i].isNotEmpty) current.add(_Segment(parts[i], marks));
      final isNewline = i < parts.length - 1;
      if (isNewline) {
        // Block-attributen (zoals de bullet) hangen in Quill aan het
        // newline-teken van de regel.
        final bullet = op.attributes?[_listAttributeKey] == _bulletAttributeValue;
        lines.add(_Line(current, bullet));
        current = <_Segment>[];
      }
    }
  }
  // Een document zonder afsluitende newline is voor Quill ongeldig, maar we
  // gooien de laatste regel liever niet weg als 'ie er toch is.
  if (current.isNotEmpty) lines.add(_Line(current, false));
  return lines.map(_renderLine).join('\n');
}

String _renderLine(_Line line) {
  final buffer = StringBuffer();
  if (line.bullet) buffer.write(_bulletPrefix);
  for (final segment in _mergeAdjacent(line.segments)) {
    buffer.write(segment.marks.wrap(segment.text));
  }
  return buffer.toString();
}

/// Voegt opeenvolgende stukken met dezelfde opmaak samen, zodat er
/// `**ab**` uitkomt in plaats van `**a****b**`.
List<_Segment> _mergeAdjacent(List<_Segment> segments) {
  final merged = <_Segment>[];
  for (final segment in segments) {
    if (merged.isNotEmpty && merged.last.marks == segment.marks) {
      merged[merged.length - 1] = _Segment(merged.last.text + segment.text, segment.marks);
    } else {
      merged.add(segment);
    }
  }
  return merged;
}

/// Parseert de inline-opmaak van één regel. Een niet-afgesloten marker blijft
/// letterlijke tekst.
List<_Segment> _parseInline(String text, _Marks marks) {
  final segments = <_Segment>[];
  final buffer = StringBuffer();

  void flush() {
    if (buffer.isEmpty) return;
    segments.add(_Segment(buffer.toString(), marks));
    buffer.clear();
  }

  var i = 0;
  while (i < text.length) {
    if (!marks.underline && text.startsWith(_underlineOpen, i)) {
      final end = text.indexOf(_underlineClose, i + _underlineOpen.length);
      if (end != -1) {
        flush();
        segments.addAll(
          _parseInline(text.substring(i + _underlineOpen.length, end), marks.copyWith(underline: true)),
        );
        i = end + _underlineClose.length;
        continue;
      }
    }
    if (text[i] == '*') {
      var run = 0;
      while (i + run < text.length && text[i + run] == '*') {
        run++;
      }
      // Volgorde van schrijven is vast (underline buiten, dan bold, dan
      // italic), dus `***` is altijd vet + cursief.
      if (run >= 3 && !marks.bold && !marks.italic) {
        final end = text.indexOf(_boldItalicMarker, i + 3);
        if (end != -1) {
          flush();
          segments.addAll(
            _parseInline(text.substring(i + 3, end), marks.copyWith(bold: true, italic: true)),
          );
          i = end + 3;
          continue;
        }
      } else if (run == 2 && !marks.bold) {
        final end = text.indexOf(_boldMarker, i + 2);
        if (end != -1) {
          flush();
          segments.addAll(_parseInline(text.substring(i + 2, end), marks.copyWith(bold: true)));
          i = end + 2;
          continue;
        }
      } else if (run == 1 && !marks.italic) {
        final end = text.indexOf(_italicMarker, i + 1);
        if (end != -1) {
          flush();
          segments.addAll(_parseInline(text.substring(i + 1, end), marks.copyWith(italic: true)));
          i = end + 1;
          continue;
        }
      }
    }
    buffer.write(text[i]);
    i++;
  }
  flush();
  return segments;
}

/// Eén regel uit het document: de inline-stukken plus of het een bullet is.
class _Line {
  const _Line(this.segments, this.bullet);

  final List<_Segment> segments;
  final bool bullet;
}

/// Een aaneengesloten stuk tekst met dezelfde inline-opmaak.
class _Segment {
  const _Segment(this.text, this.marks);

  final String text;
  final _Marks marks;
}

/// De drie ondersteunde inline-opmaakkenmerken.
class _Marks {
  const _Marks({this.bold = false, this.italic = false, this.underline = false});

  factory _Marks.fromAttributes(Map<String, dynamic>? attributes) => _Marks(
    bold: attributes?['bold'] == true,
    italic: attributes?['italic'] == true,
    underline: attributes?['underline'] == true,
  );

  final bool bold;
  final bool italic;
  final bool underline;

  bool get isEmpty => !bold && !italic && !underline;

  _Marks copyWith({bool? bold, bool? italic, bool? underline}) => _Marks(
    bold: bold ?? this.bold,
    italic: italic ?? this.italic,
    underline: underline ?? this.underline,
  );

  Map<String, dynamic>? toAttributes() {
    if (isEmpty) return null;
    return {
      if (bold) 'bold': true,
      if (italic) 'italic': true,
      if (underline) 'underline': true,
    };
  }

  /// Schrijft de markers in een vaste, deterministische nestvolgorde:
  /// underline buitenom, dan bold, dan italic (`<u>***tekst***</u>`).
  String wrap(String text) {
    var result = text;
    if (italic) result = '$_italicMarker$result$_italicMarker';
    if (bold) result = '$_boldMarker$result$_boldMarker';
    if (underline) result = '$_underlineOpen$result$_underlineClose';
    return result;
  }

  @override
  bool operator ==(Object other) =>
      other is _Marks && other.bold == bold && other.italic == italic && other.underline == underline;

  @override
  int get hashCode => Object.hash(bold, italic, underline);
}
