"""通知模块测试 - 4个接口"""
from conftest import BASE_URL, auth_get, auth_put


class TestNotification:
    """通知模块 /api/notifications"""

    def test_01_list_notifications(self, base_url, auth_token):
        """GET /api/notifications - 通知列表"""
        resp = auth_get(f"{base_url}/api/notifications", auth_token, params={"page": 1, "size": 20})
        data = resp.json()
        assert data["code"] == 200

    def test_02_unread_count(self, base_url, auth_token):
        """GET /api/notifications/unread-count - 未读数"""
        resp = auth_get(f"{base_url}/api/notifications/unread-count", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert "count" in data["data"]

    def test_03_mark_all_read(self, base_url, auth_token):
        """PUT /api/notifications/read-all - 全部已读"""
        resp = auth_put(f"{base_url}/api/notifications/read-all", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_04_mark_read(self, base_url, auth_token):
        """PUT /api/notifications/{id}/read - 标记已读"""
        # 获取一条通知
        resp = auth_get(f"{base_url}/api/notifications", auth_token, params={"page": 1, "size": 1})
        records = resp.json()["data"]["records"]
        if records:
            nid = records[0]["id"]
            resp = auth_put(f"{base_url}/api/notifications/{nid}/read", auth_token)
            data = resp.json()
            assert data["code"] == 200
