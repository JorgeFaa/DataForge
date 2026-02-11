import 'dart:io';

class ApiConfig {
  static final String baseUrl = _buildBaseUrl();

  static String _buildBaseUrl() {
    final port = Platform.environment['API_PORT'];
    return 'http://localhost:$port';
  }
}
