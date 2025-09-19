import 'package:flutter/material.dart';
import 'package:eq_adjust/pages/my_eq_page.dart';
import 'package:eq_adjust/services/airoha_eq_control.dart';
import 'package:eq_adjust/services/airoha_connector.dart';
import 'package:eq_adjust/pages/welcome_page.dart';

class EqualizerPage extends StatefulWidget {
  const EqualizerPage({super.key});

  @override
  _EqualizerPageState createState() => _EqualizerPageState();
}

class _EqualizerPageState extends State<EqualizerPage> {
  final AirohaConnector _connector = AirohaConnector();
  bool _isAdaptiveEQon = false;
  String _leftIndex = 'NA';
  String _rightIndex = 'NA';

  @override
  void initState() {
    super.initState();
    _setupDisconnectionListener();
    _fetchAdaptiveEqStatus();
  }

  @override
  void dispose() {
    _connector.unregisterConnectionListener();
    super.dispose();
  }

  // 設定斷線監聽器
  void _setupDisconnectionListener() {
    _connector.registerConnectionListener((int status) {
      if (status == AirohaConnector.DISCONNECTED) {
        _gotoWelcomePage();
      }
    });
  }

  // 返回歡迎頁面
  void _gotoWelcomePage() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (context) => const WelcomePage()),
    );
  }

  Future<void> _fetchAdaptiveEqStatus() async {
    final isOn = await AirohaEqControl.getAdaptiveEqDetectionStatus();
    if (isOn != null) {
      setState(() {
        _isAdaptiveEQon = isOn;
      });
    }

    final eqInfo = await AirohaEqControl.getAdaptiveEqInfo();
    if (eqInfo != null) {
      setState(() {
        _leftIndex = eqInfo['leftIndex'] != -1 ? '${eqInfo['leftIndex']}' : 'NA';
        _rightIndex = eqInfo['rightIndex'] != -1 ? '${eqInfo['rightIndex']}' : 'NA';
      });
    }
  }

  // 斷開連接並返回
  Future<void> _disconnectAndGoBack() async {
    await _connector.disconnect();
    _gotoWelcomePage();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFEFEFEF),
      appBar: AppBar(
        title: const Text('Equalizer'),
        backgroundColor: const Color(0xFFEFEFEF),
        elevation: 0,
        centerTitle: true,
        // 修改：添加斷開連接的返回按鈕
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: _disconnectAndGoBack,
        ),
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(10.0),
          child: Column(
            children: [
              // 新增：連接狀態顯示
              _buildConnectionStatusTile(),
              _buildAdaptiveEqTile(context),
              _buildEqIndexTile(Icons.earbuds, 'Adaptive EQ Index (L)', _leftIndex),
              _buildEqIndexTile(Icons.earbuds_sharp, 'Adaptive EQ Index (R)', _rightIndex),
              const SizedBox(height: 10),
              _buildMyEqTile(context),
            ],
          ),
        ),
      ),
    );
  }

  // 新增：連接狀態顯示元件
  Widget _buildConnectionStatusTile() {
    return Container(
      color: const Color(0xFFE9E9E9),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      margin: const EdgeInsets.only(bottom: 5),
      child: Row(
        children: [
          const Icon(Icons.bluetooth_connected, color: Colors.green),
          const SizedBox(width: 10),
          const Text('裝置已連接', style: TextStyle(fontSize: 16)),
          const Spacer(),
          TextButton(
            onPressed: _disconnectAndGoBack,
            child: const Text('斷開', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }

  Widget _buildAdaptiveEqTile(BuildContext context) {
    return Container(
      color: const Color(0xFFE9E9E9),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          const Icon(Icons.graphic_eq, color: Colors.red),
          const SizedBox(width: 10),
          const Text('Adaptive EQ', style: TextStyle(fontSize: 16)),
          const Spacer(),
          Switch(
            value: _isAdaptiveEQon,
            onChanged: (bool value) async {
              final success = await AirohaEqControl.setAdaptiveEqDetectionStatus(isOn: value);
              if (success) {
                setState(() {
                  _isAdaptiveEQon = value;
                });
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('設定失敗')),
                );
              }
            },
            activeColor: Colors.orange,
          ),
        ],
      ),
    );
  }

  Widget _buildEqIndexTile(IconData icon, String title, String value) {
    return Container(
      color: const Color(0xFFE9E9E9),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          Icon(icon, color: Colors.grey),
          const SizedBox(width: 10),
          Text(title, style: const TextStyle(fontSize: 16)),
          const Spacer(),
          Text(value, style: const TextStyle(fontSize: 16, color: Colors.orange)),
        ],
      ),
    );
  }

  Widget _buildMyEqTile(BuildContext context) {
    return InkWell(
      onTap: () {
        Navigator.of(context).push(
            MaterialPageRoute(builder: (context) => const MyEqPage())
        );
      },
      child: Container(
        color: const Color(0xFFE9E9E9),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        child: const Row(
          children: [
            Icon(Icons.tune, color: Colors.green),
            SizedBox(width: 10),
            Text('My EQ', style: TextStyle(fontSize: 16)),
            Spacer(),
            Icon(Icons.arrow_forward_ios, size: 20, color: Colors.grey),
          ],
        ),
      ),
    );
  }
}