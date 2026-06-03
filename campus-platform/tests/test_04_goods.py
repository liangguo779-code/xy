"""商品模块测试 - 7个接口"""
from conftest import BASE_URL, auth_get, auth_post, auth_put, auth_delete


class TestGoods:
    """商品模块 /api/goods"""
    _goods_id = None

    def test_01_create_goods(self, base_url, auth_token):
        """POST /api/goods - 发布商品"""
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Test Laptop 90% New",
            "description": "Used for 6 months, no damage",
            "price": 2500.00,
            "originalPrice": 5000.00,
            "category": "Digital",
            "condition": "Good",
            "type": 0,
            "images": '["http://example.com/img1.jpg"]'
        }, auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] is not None
        TestGoods._goods_id = data["data"]["id"]

    def test_02_list_goods(self, base_url, auth_token):
        """GET /api/goods - 商品列表"""
        resp = auth_get(f"{base_url}/api/goods", auth_token, params={"page": 1, "size": 10})
        data = resp.json()
        assert data["code"] == 200

    def test_03_list_goods_with_keyword(self, base_url, auth_token):
        """GET /api/goods - 关键词搜索"""
        resp = auth_get(f"{base_url}/api/goods", auth_token, params={"keyword": "Laptop", "page": 1, "size": 10})
        data = resp.json()
        assert data["code"] == 200

    def test_04_list_goods_with_category(self, base_url, auth_token):
        """GET /api/goods - 分类筛选"""
        resp = auth_get(f"{base_url}/api/goods", auth_token, params={"categoryId": 1, "page": 1, "size": 10})
        data = resp.json()
        assert data["code"] == 200

    def test_05_list_goods_with_price_range(self, base_url, auth_token):
        """GET /api/goods - 价格区间筛选"""
        resp = auth_get(f"{base_url}/api/goods", auth_token, params={
            "minPrice": 100, "maxPrice": 5000, "page": 1, "size": 10
        })
        data = resp.json()
        assert data["code"] == 200

    def test_06_list_goods_with_sort(self, base_url, auth_token):
        """GET /api/goods - 排序"""
        for sort in ["price_asc", "price_desc", "newest", "hottest"]:
            resp = auth_get(f"{base_url}/api/goods", auth_token, params={"sortBy": sort, "page": 1, "size": 10})
            data = resp.json()
            assert data["code"] == 200

    def test_07_recommend_goods(self, base_url, auth_token):
        """GET /api/goods/recommend - 推荐商品"""
        resp = auth_get(f"{base_url}/api/goods/recommend", auth_token, params={"page": 1, "size": 10})
        data = resp.json()
        assert data["code"] == 200

    def test_08_goods_detail(self, base_url, auth_token):
        """GET /api/goods/{id} - 商品详情"""
        resp = auth_get(f"{base_url}/api/goods/{TestGoods._goods_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["title"] == "Test Laptop 90% New"

    def test_09_update_goods(self, base_url, auth_token):
        """PUT /api/goods/{id} - 编辑商品"""
        resp = auth_put(f"{base_url}/api/goods/{TestGoods._goods_id}", auth_token, json={
            "title": "Updated Laptop Title",
            "price": 2200.00
        })
        data = resp.json()
        assert data["code"] == 200

    def test_10_mark_as_sold(self, base_url, auth_token):
        """PUT /api/goods/{id}/sold - 标记已售出"""
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Sold Item", "price": 100, "condition": "New"
        }, auth_token)
        gid = resp.json()["data"]["id"]
        resp = auth_put(f"{base_url}/api/goods/{gid}/sold", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_11_delete_goods(self, base_url, auth_token):
        """DELETE /api/goods/{id} - 删除商品（下架）"""
        resp = auth_delete(f"{base_url}/api/goods/{TestGoods._goods_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
