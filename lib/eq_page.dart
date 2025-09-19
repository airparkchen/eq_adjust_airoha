import 'package:flutter/material.dart';
import 'package:eq_adjust/my_eq_page.dart';
import 'package:eq_adjust/eq_control.dart';

class EqualizerPage extends StatefulWidget {
  const EqualizerPage({Key? key}) : super(key: key);

  @override
  _EqualizerPageState createState() => _EqualizerPageState();
}

class _EqualizerPageState extends State<EqualizerPage> {
  bool _isAdaptiveEQon = false;
  String _leftIndex = 'NA';
  String _rightIndex = 'NA';

  @override
  void initState() {
    super.initState();
    _fetchAdaptiveEqStatus();
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFEFEFEF),
      appBar: AppBar(
        title: const Text('Equalizer'),
        backgroundColor: const Color(0xFFEFEFEF),
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(10.0),
          child: Column(
            children: [
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

  Widget _buildAdaptiveEqTile(BuildContext context) {
    return Container(
      color: const Color(0xFFE9E9E9),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          const Icon(Icons.adaptive_audio, color: Colors.red),
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
                // You can add a Snackbar or dialog to show error
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
        Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MyEqPage()));
      },
      child: Container(
        color: const Color(0xFFE9E9E9),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        child: Row(
          children: const [
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