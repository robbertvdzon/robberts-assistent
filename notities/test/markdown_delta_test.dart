import 'package:flutter_quill/quill_delta.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/markdown_delta.dart';

/// Hulpje: de ops van een delta als eenvoudig JSON, zodat een verwachting
/// leesbaar blijft.
List<Map<String, dynamic>> _ops(Delta delta) => delta.toJson();

void main() {
  group('markdownToDelta', () {
    test('vet', () {
      expect(_ops(markdownToDelta('een **vet** woord')), [
        {'insert': 'een '},
        {
          'insert': 'vet',
          'attributes': {'bold': true},
        },
        // Delta voegt aangrenzende inserts met dezelfde opmaak samen, dus de
        // afsluitende newline hangt aan het laatste platte stuk.
        {'insert': ' woord\n'},
      ]);
    });

    test('cursief', () {
      expect(_ops(markdownToDelta('*schuin*')), [
        {
          'insert': 'schuin',
          'attributes': {'italic': true},
        },
        {'insert': '\n'},
      ]);
    });

    test('onderstreept', () {
      expect(_ops(markdownToDelta('<u>streep</u>')), [
        {
          'insert': 'streep',
          'attributes': {'underline': true},
        },
        {'insert': '\n'},
      ]);
    });

    test('bullet wordt een list-attribuut op de newline', () {
      expect(_ops(markdownToDelta('- melk')), [
        {'insert': 'melk'},
        {
          'insert': '\n',
          'attributes': {'list': 'bullet'},
        },
      ]);
    });

    test('gecombineerde opmaak op dezelfde tekst', () {
      expect(_ops(markdownToDelta('<u>***alles***</u>')), [
        {
          'insert': 'alles',
          'attributes': {'bold': true, 'italic': true, 'underline': true},
        },
        {'insert': '\n'},
      ]);
    });

    test('een niet-afgesloten marker blijft letterlijke tekst', () {
      expect(_ops(markdownToDelta('**niet afgesloten')), [
        {'insert': '**niet afgesloten\n'},
      ]);
    });

    test('markers lopen niet over regelgrenzen heen', () {
      expect(_ops(markdownToDelta('**een\ntwee**')), [
        {'insert': '**een\ntwee**\n'},
      ]);
    });

    test('onbekende markup blijft platte tekst', () {
      expect(_ops(markdownToDelta('# Kop\n* geen bullet\n1. genummerd')), [
        {'insert': '# Kop\n* geen bullet\n1. genummerd\n'},
      ]);
    });
  });

  group('deltaToMarkdown', () {
    test('vet', () {
      final delta = Delta()
        ..insert('een ')
        ..insert('vet', {'bold': true})
        ..insert(' woord')
        ..insert('\n');
      expect(deltaToMarkdown(delta), 'een **vet** woord');
    });

    test('cursief', () {
      final delta = Delta()
        ..insert('schuin', {'italic': true})
        ..insert('\n');
      expect(deltaToMarkdown(delta), '*schuin*');
    });

    test('onderstreept', () {
      final delta = Delta()
        ..insert('streep', {'underline': true})
        ..insert('\n');
      expect(deltaToMarkdown(delta), '<u>streep</u>');
    });

    test('bullet', () {
      final delta = Delta()
        ..insert('melk')
        ..insert('\n', {'list': 'bullet'})
        ..insert('brood')
        ..insert('\n', {'list': 'bullet'});
      expect(deltaToMarkdown(delta), '- melk\n- brood');
    });

    test('gecombineerde opmaak in vaste nestvolgorde', () {
      final delta = Delta()
        ..insert('alles', {'bold': true, 'italic': true, 'underline': true})
        ..insert('\n');
      expect(deltaToMarkdown(delta), '<u>***alles***</u>');
    });

    test('opeenvolgende stukken met dezelfde opmaak worden samengevoegd', () {
      final delta = Delta()
        ..insert('een', {'bold': true})
        ..insert('twee', {'bold': true})
        ..insert('\n');
      expect(deltaToMarkdown(delta), '**eentwee**');
    });

    test("Quill's afsluitende newline levert geen extra lege regel op", () {
      final delta = Delta()
        ..insert('regel')
        ..insert('\n');
      expect(deltaToMarkdown(delta), 'regel');
    });
  });

  group('roundtrip', () {
    void roundtrip(String source) {
      expect(deltaToMarkdown(markdownToDelta(source)), source);
    }

    test('opmaak heen en terug', () {
      roundtrip('een **vet** woord');
      roundtrip('*schuin*');
      roundtrip('<u>streep</u>');
      roundtrip('- melk\n- brood');
      roundtrip('<u>***alles***</u>');
    });

    test('gecombineerde opmaak binnen een bullet', () {
      roundtrip('- **vet** en *schuin* en <u>streep</u>');
    });

    test('platte tekst met lege regels en onbekende markup blijft byte-identiek', () {
      const source =
          '# Kop\n'
          '\n'
          'Gewone tekst met een [link](https://example.com) en `code`.\n'
          '\n'
          '\n'
          '  ingesprongen regel\n'
          '1. genummerd\n'
          '* sterretje-bullet\n'
          '| tabel | kolom |\n'
          '\n'
          'laatste regel';
      roundtrip(source);
    });

    test('lege notitie', () {
      roundtrip('');
    });

    test('afsluitende lege regel blijft behouden', () {
      roundtrip('regel\n');
    });
  });
}
