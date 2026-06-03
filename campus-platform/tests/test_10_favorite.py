"""收藏模块测试 - 4个接口"""
from conftest import BASE_URL, auth_get, auth_post, auth_delete


class TestFavorite:
    """收藏模块 /api/favorites"""
    _goods_id = None

    def test_01_add_favorite(self, base_url, auth_token):
        """POST /api/favorites/{goodsId} - 收藏商品"""
        # 创建一个商品
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Favorite Test Item",
            "price": 100
        }, auth_token)
        TestFavorite._goods_id = resp.json()["data"]["id"]

        resp = auth_post(f"{base_url}/api/favorites/{TestFavorite._goods_id}", {}, auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_02_check_favorite(self, base_url, auth_token):
        """GET /api/favorites/check/{goodsId} - 检查收藏"""
        resp = auth_get(f"{base_url}/api/favorites/check/{TestFavorite._goods_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"] is True

    def test_03_list_favorites(self, base_url, auth_token):
        """GET /api/favorites - 收藏列表"""
        resp = auth_get(f"{base_url}/api/favorites", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]) >= 1

    def test_04_remove_favorite(self, base_url, auth_token):
        """DELETE /api/favorites/{goodsId} - 取消收藏"""
        resp = auth_delete(f"{base_url}/api/favorites/{TestFavorite._goods_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
