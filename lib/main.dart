import 'package:flutter/material.dart';
import 'package:eq_adjust/eq_page.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Airoha EQ Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const EqualizerPage(),
    );
  }
}