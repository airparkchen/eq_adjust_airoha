import 'package:flutter/material.dart';
import 'package:eq_adjust/services/airoha_connector.dart';
import 'package:eq_adjust/pages/eq_page.dart';
import 'package:eq_adjust/pages/welcome_page.dart';

class DeviceSearchPage extends StatefulWidget {
  const DeviceSearchPage({super.key});

  @override
  _DeviceSearchPageState createState() => _DeviceSearchPageState();
}

class _DeviceSearchPageState extends State<DeviceSearchPage> {
  final AirohaConnector _connector = AirohaConnector();
  bool _isConnecting = false;
  bool _isConnectedWrongRole = false;

  @override
  void initState() {
    super.initState();
    _setupConnectionListener();
    _startConnection();
  }

  @override
  void dispose() {
    _connector.unregisterConnectionListener();
    super.dispose();
  }

  // 設定連接監聽器（對應demo app的registerConnectionListener）
  void _setupConnectionListener() {
    _connector.registerConnectionListener((int status) {
      switch (status) {
        case AirohaConnector.CONNECTED:
          _gotoEqualizerPage();
          break;
        case AirohaConnector.CONNECTED_WRONG_ROLE:
          setState(() {
            _isConnectedWrongRole = true;
          });
          break;
        case AirohaConnector.DISCONNECTED:
          if (!_isConnectedWrongRole) {
            _gotoWelcomePage();
          }
          setState(() {
            _isConnectedWrongRole = false;
          });
          break;
      }
    });
  }

  // 開始連接（對應demo app的connectBoundDevice）
  Future<void> _startConnection() async {
    setState(() {
      _isConnecting = true;
    });

    try {
      await _connector.connectBoundDevice();
    } catch (e) {
      _showErrorDialog('連接失敗: $e');
    }
  }

  // 跳轉到EQ頁面（對應demo app的gotoMenu）
  void _gotoEqualizerPage() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (context) => const EqualizerPage()),
    );
  }

  // 返回歡迎頁面（對應demo app的gotoBeginPage）
  void _gotoWelcomePage() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (context) => const WelcomePage()),
    );
  }

  // 顯示錯誤對話框
  void _showErrorDialog(String message) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('連接錯誤'),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                _gotoWelcomePage();
              },
              child: const Text('確定'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFEFEFEF),
      appBar: AppBar(
        title: const Text('搜尋裝置'),
        backgroundColor: const Color(0xFFEFEFEF),
        elevation: 0,
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            Navigator.of(context).pop();
          },
        ),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 搜尋圖示（對應demo app的searchImage）
            Icon(
              Icons.search,
              size: 80,
              color: Colors.orange,
            ),

            const SizedBox(height: 30),

            // 圓形進度條（對應demo app的progressBarHA）
            SizedBox(
              width: 80,
              height: 80,
              child: CircularProgressIndicator(
                strokeWidth: 6,
                valueColor: AlwaysStoppedAnimation<Color>(Colors.orange),
              ),
            ),

            const SizedBox(height: 30),

            // 描述文字（對應demo app的descriptionText）
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 40),
              child: Column(
                children: [
                  Text(
                    '正在搜尋耳機裝置...',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Colors.orange,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    '請確保您的耳機已開機並在配對模式',
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.grey,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),

            const SizedBox(height: 50),

            // 取消按鈕
            OutlinedButton(
              onPressed: () {
                Navigator.of(context).pop();
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.grey, width: 1),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(20),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 12),
              ),
              child: const Text(
                '取消',
                style: TextStyle(
                  fontSize: 16,
                  color: Colors.grey,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}