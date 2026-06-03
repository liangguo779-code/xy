"""公告模块测试 - 5个接口"""
from conftest import BASE_URL, auth_get, auth_post, auth_put, auth_delete


class TestAnnouncement:
    """公告模块 /api/announcements"""
    _announcement_id = None

    def test_01_list_announcements(self, base_url, auth_token):
        """GET /api/announcements - 公告列表（用户端）"""
        resp = auth_get(f"{base_url}/api/announcements", auth_token, params={"page": 1, "size": 10})
        assert resp.json()["code"] == 200

    def test_02_create_announcement(self, base_url, auth_token):
        """POST /api/announcements - 创建公告"""
        resp = auth_post(f"{base_url}/api/announcements", {
            "title": "System Maintenance Notice",
            "content": "System maintenance Saturday 2-4am",
            "type": "normal"
        }, auth_token)
        data = resp.json()
        assert data["code"] == 200
        TestAnnouncement._announcement_id = data["data"]["id"]

    def test_03_update_announcement(self, base_url, auth_token):
        """PUT /api/announcements/{id} - 更新公告"""
        resp = auth_put(f"{base_url}/api/announcements/{TestAnnouncement._announcement_id}",
                        auth_token, json={"title": "Updated Notice", "content": "Maintenance moved to Sunday"})
        assert resp.json()["code"] == 200

    def test_04_list_all_announcements(self, base_url, auth_token):
        """GET /api/announcements/all - 全部公告（管理端）"""
        resp = auth_get(f"{base_url}/api/announcements/all", auth_token, params={"page": 1, "size": 10})
        assert resp.json()["code"] == 200

    def test_05_delete_announcement(self, base_url, auth_token):
        """DELETE /api/announcements/{id} - 删除公告"""
        resp = auth_delete(f"{base_url}/api/announcements/{TestAnnouncement._announcement_id}", auth_token)
        assert resp.json()["code"] == 200
