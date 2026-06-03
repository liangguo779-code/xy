"""地址模块测试 - 6个接口"""
from conftest import BASE_URL, auth_get, auth_post, auth_put, auth_delete


class TestAddress:
    """地址模块 /api/address"""
    _address_id = None

    def test_01_create_address(self, base_url, auth_token):
        """POST /api/address - 新增地址"""
        resp = auth_post(f"{base_url}/api/address", {
            "contactName": "张三",
            "phone": "13800001111",
            "building": "1号宿舍楼",
            "detail": "305室",
            "isDefault": 1
        }, auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] is not None
        TestAddress._address_id = data["data"]["id"]

    def test_02_list_addresses(self, base_url, auth_token):
        """GET /api/address - 地址列表"""
        resp = auth_get(f"{base_url}/api/address", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]) >= 1

    def test_03_get_address_detail(self, base_url, auth_token):
        """GET /api/address/{id} - 地址详情"""
        resp = auth_get(f"{base_url}/api/address/{TestAddress._address_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["contactName"] == "张三"

    def test_04_get_default_address(self, base_url, auth_token):
        """GET /api/address/default - 默认地址"""
        resp = auth_get(f"{base_url}/api/address/default", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_05_update_address(self, base_url, auth_token):
        """PUT /api/address - 修改地址"""
        resp = auth_put(f"{base_url}/api/address", auth_token, json={
            "id": TestAddress._address_id,
            "contactName": "李四",
            "phone": "13900002222",
            "building": "2号宿舍楼",
            "detail": "401室",
            "isDefault": 1
        })
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["contactName"] == "李四"

    def test_06_delete_address(self, base_url, auth_token):
        """DELETE /api/address/{id} - 删除地址"""
        resp = auth_delete(f"{base_url}/api/address/{TestAddress._address_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
