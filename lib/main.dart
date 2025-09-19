import 'package:flutter/material.dart';
import 'package:eq_adjust/pages/welcome_page.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Airoha EQ Demo',
      theme: ThemeData(
        primarySwatch: Colors.orange,
        primaryColor: Colors.orange,
      ),
      // 修改：改為從 WelcomePage 開始，而不是 EqualizerPage
      home: const WelcomePage(),
    );
  }
}