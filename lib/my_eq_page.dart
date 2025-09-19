import 'package:flutter/material.dart';
import 'package:eq_adjust/eq_control.dart';

class MyEqPage extends StatefulWidget {
  const MyEqPage({Key? key}) : super(key: key);

  @override
  _MyEqPageState createState() => _MyEqPageState();
}

class _MyEqPageState extends State<MyEqPage> {
  final List<double> _gains = List.filled(10, 0.0);
  final List<double> _freqs = [200, 280, 400, 550, 770, 1000, 2000, 4000, 8000, 16000];
  bool _isSupportLDAC = false;

  @override
  void initState() {
    super.initState();
    _fetchEqSettings();
  }

  Future<void> _fetchEqSettings() async {
    final settings = await AirohaEqControl.getAllEqSettings();
    if (settings != null) {
      final List<dynamic>? eqSettingsList = settings['eqSettingsList'];
      if (eqSettingsList != null) {
        for (var setting in eqSettingsList) {
          if (setting['categoryId'] == 101) {
            final payload = setting['eqPayload'];
            if (payload != null && payload['iirParams'] != null) {
              final List<dynamic> iirParams = payload['iirParams'];
              final List<int> sampleRates = List<int>.from(payload['allSampleRates']);

              setState(() {
                for (int i = 0; i < 10 && i < iirParams.length; i++) {
                  _gains[i] = (iirParams[i]['gainValue'] as num).toDouble().clamp(-12.0, 12.0);
                  _freqs[i] = (iirParams[i]['frequency'] as num).toDouble();
                }
                _isSupportLDAC = sampleRates.contains(88200) && sampleRates.contains(96000);
              });
            }
            break;
          }
        }
      }
    }
  }

  void _setEqSettings({required bool save}) async {
    List<Map<String, dynamic>> iirParams = [];
    for (int i = 0; i < 10; i++) {
      iirParams.add({
        'bandType': 2, // BAND_PASS
        'frequency': _freqs[i],
        'gainValue': _gains[i],
        'qValue': 2.0,
      });
    }

    final List<int> allSampleRates = _isSupportLDAC ? [44100, 48000, 88200, 96000] : [44100, 48000];

    await AirohaEqControl.setEqSetting(
      categoryId: 101,
      iirParams: iirParams,
      allSampleRates: allSampleRates,
      saveOrNot: save,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFEFEFEF),
      appBar: AppBar(
        title: const Text('My EQ'),
        backgroundColor: const Color(0xFFEFEFEF),
        elevation: 0,
        centerTitle: true,
        actions: [
          TextButton(
            onPressed: () => _setEqSettings(save: true),
            child: const Text('Save', style: TextStyle(color: Colors.orange)),
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.all(20),
              child: Text(
                'Equalizer Warning',
                style: TextStyle(fontSize: 14),
              ),
            ),
            _buildLdacSwitch(),
            const SizedBox(height: 10),
            ...List.generate(10, (index) => _buildEqSlider(index)),
          ],
        ),
      ),
    );
  }

  Widget _buildLdacSwitch() {
    return Container(
      color: const Color(0xFFE9E9E9),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          const Icon(Icons.adaptive_audio, color: Colors.red),
          const SizedBox(width: 10),
          const Text('LDAC', style: TextStyle(fontSize: 16)),
          const Spacer(),
          Switch(
            value: _isSupportLDAC,
            onChanged: (bool value) {
              setState(() {
                _isSupportLDAC = value;
              });
              _setEqSettings(save: false);
            },
            activeColor: Colors.orange,
          ),
        ],
      ),
    );
  }

  Widget _buildEqSlider(int index) {
    String freqText;
    if (_freqs[index] >= 1000) {
      freqText = '${(_freqs[index] / 1000).toInt()}k Hz';
    } else {
      freqText = '${_freqs[index].toInt()} Hz';
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      child: Row(
        children: [
          SizedBox(
            width: 60,
            child: Text(freqText, style: const TextStyle(fontSize: 14)),
          ),
          SizedBox(
            width: 30,
            child: Text('${_gains[index].toInt()}', style: const TextStyle(fontSize: 14)),
          ),
          Expanded(
            child: SliderTheme(
              data: SliderThemeData(
                thumbColor: Colors.white,
                activeTrackColor: Colors.orange,
                inactiveTrackColor: Colors.grey,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10.0),
                trackHeight: 5.0,
              ),
              child: Slider(
                value: _gains[index],
                min: -12, // Corresponds to GAIN_MIN
                max: 12,  // Corresponds to GAIN_MAX
                divisions: 24,
                onChanged: (double value) {
                  setState(() {
                    _gains[index] = value;
                  });
                },
                onChangeEnd: (double value) {
                  _setEqSettings(save: false);
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}