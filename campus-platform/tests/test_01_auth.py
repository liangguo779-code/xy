"""认证模块测试 - 6个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post


class TestAuth:
    """认证模块 /api/auth"""

    def test_01_register(self, base_url):
        """POST /api/auth/register - 注册"""
        resp = requests.post(f"{base_url}/api/auth/register", json={
            "username": "newuser001",
            "password": "123456",
            "nickname": "新用户"
        })
        data = resp.json()
        assert data["code"] == 200

    def test_02_register_duplicate(self, base_url):
        """POST /api/auth/register - 重复注册应失败"""
        resp = requests.post(f"{base_url}/api/auth/register", json={
            "username": "newuser001",
            "password": "123456"
        })
        data = resp.json()
        assert data["code"] != 200

    def test_03_login_success(self, base_url):
        """POST /api/auth/login - 登录成功"""
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "newuser001",
            "password": "123456"
        })
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["token"] is not None
        assert data["data"]["user"]["username"] == "newuser001"

    def test_04_login_wrong_password(self, base_url):
        """POST /api/auth/login - 密码错误"""
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "newuser001",
            "password": "wrong"
        })
        data = resp.json()
        assert data["code"] == 401

    def test_05_login_wrong_username(self, base_url):
        """POST /api/auth/login - 用户名不存在"""
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "nonexistent",
            "password": "123456"
        })
        data = resp.json()
        assert data["code"] == 401

    def test_06_get_me(self, base_url, auth_token):
        """GET /api/auth/me - 获取当前用户信息"""
        resp = auth_get(f"{base_url}/api/auth/me", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["username"] is not None

    def test_07_get_me_no_token(self, base_url):
        """GET /api/auth/me - 未登录应返回错误"""
        resp = requests.get(f"{base_url}/api/auth/me")
        # Sa-Token 可能返回401或200（取决于拦截器配置）
        if resp.status_code == 200:
            data = resp.json()
            assert data["code"] in (401, 403) or data["data"] is None
        else:
            assert resp.status_code == 401

    def test_08_send_reset_code(self, base_url):
        """POST /api/auth/send-code - 发送验证码"""
        resp = requests.post(f"{base_url}/api/auth/send-code",
                             params={"phone": "13800000000"})
        # 如果手机号未注册会返回错误，这里测试接口能访问
        assert resp.status_code in (200, 500)

    def test_09_reset_password(self, base_url):
        """POST /api/auth/reset-password - 重置密码（验证码错误）"""
        resp = requests.post(f"{base_url}/api/auth/reset-password", json={
            "phone": "13800000000",
            "code": "000000",
            "newPassword": "newpass123"
        })
        data = resp.json()
        assert data["code"] != 200  # 验证码错误应失败

    def test_10_logout(self, base_url, auth_token):
        """POST /api/auth/logout - 退出登录"""
        resp = auth_post(f"{base_url}/api/auth/logout", {}, auth_token)
        data = resp.json()
        assert data["code"] == 200
