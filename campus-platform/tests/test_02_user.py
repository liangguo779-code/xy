"""用户模块测试 - 3个接口"""
from conftest import BASE_URL, auth_get, auth_put, auth_delete


class TestUser:
    """用户模块 /api/user"""

    def test_01_update_profile(self, base_url, auth_token):
        """PUT /api/user/profile - 修改个人信息"""
        resp = auth_put(f"{base_url}/api/user/profile", auth_token, json={
            "nickname": "修改后的昵称",
            "phone": "13900001111",
            "dormitory": "3号楼502"
        })
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["nickname"] == "修改后的昵称"

    def test_02_change_password_wrong_old(self, base_url, auth_token):
        """PUT /api/user/password - 旧密码错误"""
        resp = auth_put(f"{base_url}/api/user/password", auth_token, json={
            "oldPassword": "wrongold",
            "newPassword": "newpass123"
        })
        data = resp.json()
        assert data["code"] != 200

    def test_03_change_password_success(self, base_url, auth_token):
        """PUT /api/user/password - 修改密码成功"""
        resp = auth_put(f"{base_url}/api/user/password", auth_token, json={
            "oldPassword": "test123456",
            "newPassword": "test123456"
        })
        data = resp.json()
        assert data["code"] == 200
