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

const _underlineOpen = '<u>';
const _underlineClose = '</u>';

/// De drie ondersteunde inline-kenmerken, in de vaste nestvolgorde waarin ze
/// worden weggeschreven (underline buitenom, dan bold, dan italic).
enum _Mark { underline, bold, italic }

const _markOpen = {_Mark.underline: _underlineOpen, _Mark.bold: '**', _Mark.italic: '*'};
const _markClose = {_Mark.underline: _underlineClose, _Mark.bold: '**', _Mark.italic: '*'};

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
    // Let op: `_parseInline` gooit nooit tekens weg — een marker zonder inhoud
    // (bv. `<u></u>` of `******`) blijft letterlijke tekst.
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
  buffer.write(_renderSegments(_mergeAdjacent(line.segments), const _Marks()));
  return buffer.toString();
}

/// Schrijft de segmenten van één regel genest weg, in een vaste volgorde:
/// underline buitenom, dan bold, dan italic (`<u>***tekst***</u>`).
///
/// Nesten (in plaats van elk segment los omwikkelen) is nodig omdat opmaak zich
/// over meerdere segmenten kan uitstrekken: `**a *b* c**` moet één bold-paar
/// opleveren en niet `**a *****b***** c**`.
String _renderSegments(List<_Segment> segments, _Marks active) {
  final buffer = StringBuffer();
  var i = 0;
  while (i < segments.length) {
    final mark = segments[i].marks.firstMissing(active);
    if (mark == null) {
      buffer.write(segments[i].text);
      i++;
      continue;
    }
    // Alle aaneengesloten segmenten met dezelfde buitenste opmaak vallen binnen
    // hetzelfde markerpaar.
    var end = i;
    while (end < segments.length && segments[end].marks.has(mark)) {
      end++;
    }
    buffer.write(_markOpen[mark]);
    buffer.write(_renderSegments(segments.sublist(i, end), active.with_(mark)));
    buffer.write(_markClose[mark]);
    i = end;
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
      final start = i + _underlineOpen.length;
      final end = text.indexOf(_underlineClose, start);
      // Een leeg `<u></u>` is geen opmaak maar letterlijke tekst: anders zouden
      // die tekens bij het opslaan verdwijnen.
      if (end > start) {
        flush();
        segments.addAll(
          _parseInline(text.substring(start, end), marks.copyWith(underline: true)),
        );
        i = end + _underlineClose.length;
        continue;
      }
    }
    if (text[i] == '*') {
      final run = _starRunLength(text, i);
      // Volgorde van schrijven is vast (underline buiten, dan bold, dan
      // italic), dus `***` is altijd vet + cursief. Een langere run (`****`)
      // hoort bij geen enkele marker en blijft letterlijke tekst.
      final opened = switch (run) {
        3 when !marks.bold && !marks.italic => marks.copyWith(bold: true, italic: true),
        2 when !marks.bold => marks.copyWith(bold: true),
        1 when !marks.italic => marks.copyWith(italic: true),
        _ => null,
      };
      if (opened != null) {
        // De sluiter moet een `*`-run van exact dezelfde lengte zijn: een losse
        // `*` mag geen halve `**` opslokken (en andersom).
        final end = _findStarRun(text, i + run, run);
        if (end != -1) {
          flush();
          segments.addAll(_parseInline(text.substring(i + run, end), opened));
          i = end + run;
          continue;
        }
      }
      // Geen (geldige) marker: de hele run letterlijk overnemen, zodat een
      // volgende `*` niet alsnog als opener wordt gelezen.
      buffer.write(text.substring(i, i + run));
      i += run;
      continue;
    }
    buffer.write(text[i]);
    i++;
  }
  flush();
  return segments;
}

/// De lengte van de ononderbroken `*`-reeks die op [start] begint.
int _starRunLength(String text, int start) {
  var length = 0;
  while (start + length < text.length && text[start + length] == '*') {
    length++;
  }
  return length;
}

/// Zoekt vanaf [from] de eerstvolgende `*`-reeks met exact lengte [length].
///
/// Reeksen van een andere lengte worden in hun geheel overgeslagen, zodat een
/// enkele `*` nooit een teken uit een `**`-paar afsnoept.
int _findStarRun(String text, int from, int length) {
  var i = from;
  while (i < text.length) {
    if (text[i] != '*') {
      i++;
      continue;
    }
    final run = _starRunLength(text, i);
    if (run == length) return i;
    i += run;
  }
  return -1;
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

  bool has(_Mark mark) => switch (mark) {
    _Mark.underline => underline,
    _Mark.bold => bold,
    _Mark.italic => italic,
  };

  /// Het buitenste kenmerk dat dit segment wél heeft en [active] nog niet, in
  /// de vaste nestvolgorde; `null` als er niets meer te openen valt.
  _Mark? firstMissing(_Marks active) {
    for (final mark in _Mark.values) {
      if (has(mark) && !active.has(mark)) return mark;
    }
    return null;
  }

  _Marks with_(_Mark mark) => switch (mark) {
    _Mark.underline => copyWith(underline: true),
    _Mark.bold => copyWith(bold: true),
    _Mark.italic => copyWith(italic: true),
  };

  @override
  bool operator ==(Object other) =>
      other is _Marks && other.bold == bold && other.italic == italic && other.underline == underline;

  @override
  int get hashCode => Object.hash(bold, italic, underline);
}
