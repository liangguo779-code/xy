"""关注模块测试 - 4个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post, auth_delete


class TestFollow:
    """关注模块 /api/follow"""
    _target_user_id = None

    def test_01_follow_user(self, base_url, auth_token):
        """POST /api/follow/{userId} - 关注用户"""
        # 注册一个目标用户
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "follow_target",
            "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "follow_target",
            "password": "123456"
        })
        TestFollow._target_user_id = resp.json()["data"]["user"]["id"]

        resp = auth_post(f"{base_url}/api/follow/{TestFollow._target_user_id}", {}, auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_02_check_follow(self, base_url, auth_token):
        """GET /api/follow/check/{userId} - 检查关注"""
        resp = auth_get(f"{base_url}/api/follow/check/{TestFollow._target_user_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"] is True

    def test_03_follow_count(self, base_url, auth_token):
        """GET /api/follow/count/{userId} - 关注/粉丝数"""
        resp = auth_get(f"{base_url}/api/follow/count/{TestFollow._target_user_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert "following" in data["data"]
        assert "followers" in data["data"]

    def test_04_unfollow_user(self, base_url, auth_token):
        """DELETE /api/follow/{userId} - 取消关注"""
        resp = auth_delete(f"{base_url}/api/follow/{TestFollow._target_user_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
