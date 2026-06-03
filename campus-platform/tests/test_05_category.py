"""分类模块测试 - 1个接口"""
from conftest import BASE_URL, auth_get


class TestCategory:
    """分类模块 /api/categories"""

    def test_01_list_categories(self, base_url, auth_token):
        """GET /api/categories - 分类列表"""
        resp = auth_get(f"{base_url}/api/categories", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]) >= 1
