import 'package:dsh_app/phone/mcp_client.dart';
import 'package:dsh_app/rony/rony_channel.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('online updates are off in the fork', () {
    expect(kRonyOnlineUpdatesDisabled, isTrue);
  });

  test('MCP server names follow the engine rule and avoid reserved ones', () {
    expect(RonyMcpServer.problem('android', 'http://127.0.0.1:8765/mcp'), isNull);
    expect(RonyMcpServer.problem('fs', 'npx -y some-mcp'), isNull);
    expect(RonyMcpServer.problem('root', 'http://x'), isNotNull);
    expect(RonyMcpServer.problem('has space', 'http://x'), isNotNull);
    expect(RonyMcpServer.problem('x', 'ftp://x'), isNotNull);
    expect(RonyMcpServer.problem('x', ''), isNotNull);
  });

  test('a saved token is kept unless replaced', () {
    const s = RonyMcpServer(name: 'a', url: 'http://x', hasToken: true);
    expect(s.toWire()['keepToken'], isTrue);
    expect(s.copyWith(token: 'new').toWire()['token'], 'new');
    expect(s.copyWith(token: 'new').toWire()['keepToken'], isFalse);
  });

  test('MCP replies parse as JSON or SSE', () {
    expect(McpTestClient.parseReply('{"jsonrpc":"2.0","id":1,"result":{}}', 1)?['id'], 1);
    expect(McpTestClient.parseReply('event: message\ndata: {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}\n\n', 2)?['result'], isNotNull);
    expect(McpTestClient.parseReply('data: {"jsonrpc":"2.0","id":3}', 4), isNull);
  });
}
