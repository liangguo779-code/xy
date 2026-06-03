"""聊天模块测试 - 4个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post


class TestChat:
    """聊天模块 /api/chat"""
    _session_id = None
    _token_b = None

    def _ensure_other_user(self, base_url):
        if TestChat._token_b:
            return TestChat._token_b
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "chat_user_b", "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "chat_user_b", "password": "123456"
        })
        TestChat._token_b = resp.json()["data"]["token"]
        return TestChat._token_b

    def test_01_create_session(self, base_url, auth_token):
        """POST /api/chat/session - 创建聊天会话"""
        token_b = self._ensure_other_user(base_url)

        # 创建商品
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Chat Test Item", "price": 100
        }, auth_token)
        goods_id = resp.json()["data"]["id"]

        # 用户B发起聊天
        resp = auth_post(f"{base_url}/api/chat/session?goodsId={goods_id}", {}, token_b)
        data = resp.json()
        assert data["code"] == 200
        TestChat._session_id = data["data"]["id"]

    def test_02_list_sessions(self, base_url, auth_token):
        """GET /api/chat/sessions - 会话列表"""
        resp = auth_get(f"{base_url}/api/chat/sessions", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_03_send_message(self, base_url, auth_token):
        """POST /api/chat/messages - 发送消息"""
        resp = auth_post(f"{base_url}/api/chat/messages", {
            "sessionId": TestChat._session_id,
            "content": "Hello, is this still available?",
            "msgType": 0
        }, auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["content"] == "Hello, is this still available?"

    def test_04_get_messages(self, base_url, auth_token):
        """GET /api/chat/messages/{sessionId} - 历史消息"""
        resp = auth_get(f"{base_url}/api/chat/messages/{TestChat._session_id}",
                        auth_token, params={"page": 1, "size": 50})
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]) >= 1
