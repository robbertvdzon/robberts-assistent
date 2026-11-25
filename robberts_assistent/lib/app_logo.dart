import 'package:flutter/widgets.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Het gedeelde beeldmerk van Robbert's assistent.
class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.size = 30});

  final double size;

  @override
  Widget build(BuildContext context) => SvgPicture.asset(
    'assets/icon/logo.svg',
    width: size,
    height: size,
    semanticsLabel: "Logo van Robbert's assistent",
  );
}

/// Compacte merkregel voor de vaste app-header.
class AppHeaderTitle extends StatelessWidget {
  const AppHeaderTitle({super.key});

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      const Padding(padding: EdgeInsets.only(right: 8), child: AppLogo()),
      Flexible(
        child: Text(
          "Robbert's assistent",
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: DefaultTextStyle.of(context).style.copyWith(fontSize: 13),
        ),
      ),
    ],
  );
}
