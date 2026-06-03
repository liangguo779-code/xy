import pytest
import requests
import time

BASE_URL = "http://localhost:8080"
AI_URL = "http://localhost:8000"

# 全局token，避免fixture问题
_TOKEN = None


def get_token():
    global _TOKEN
    if _TOKEN:
        return _TOKEN

    username = f"pytest_{int(time.time())}"
    password = "test123456"

    # 注册
    requests.post(f"{BASE_URL}/api/auth/register", json={
        "username": username,
        "password": password,
        "nickname": "pytest"
    })

    # 登录
    resp = requests.post(f"{BASE_URL}/api/auth/login", json={
        "username": username,
        "password": password
    })
    data = resp.json()
    if data["code"] == 200:
        _TOKEN = data["data"]["token"]
    return _TOKEN


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def ai_url():
    return AI_URL


@pytest.fixture(scope="session")
def auth_token(base_url):
    """登录获取 token，整个测试会话共享"""
    token = get_token()
    assert token is not None, "获取token失败"
    return token


@pytest.fixture(scope="session")
def headers(auth_token):
    return {"Authorization": f"Bearer {auth_token}"}


def auth_post(url, json, token):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return requests.post(url, json=json, headers=headers)


def auth_get(url, token, params=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return requests.get(url, headers=headers, params=params)


def auth_put(url, token, json=None, params=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return requests.put(url, headers=headers, json=json, params=params)


def auth_delete(url, token):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return requests.delete(url, headers=headers)
