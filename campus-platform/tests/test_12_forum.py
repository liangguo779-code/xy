"""论坛模块测试 - 6个接口"""
from conftest import BASE_URL, auth_get, auth_post


class TestForum:
    """论坛模块 /api/forum"""
    _post_id = None

    def test_01_create_post(self, base_url, auth_token):
        """POST /api/forum/posts - 发帖"""
        resp = auth_post(f"{base_url}/api/forum/posts", {
            "title": "Test Post About Course Selection",
            "content": "When does sophomore course selection start?",
            "category": "学习"
        }, auth_token)
        data = resp.json()
        assert data["code"] == 200
        TestForum._post_id = data["data"]["id"]

    def test_02_list_posts(self, base_url, auth_token):
        """GET /api/forum/posts - 帖子列表"""
        resp = auth_get(f"{base_url}/api/forum/posts", auth_token, params={"page": 1, "size": 10})
        assert resp.json()["code"] == 200

    def test_03_list_posts_with_category(self, base_url, auth_token):
        """GET /api/forum/posts - 分类筛选"""
        resp = auth_get(f"{base_url}/api/forum/posts", auth_token,
                        params={"category": "学习", "page": 1, "size": 10})
        assert resp.json()["code"] == 200

    def test_04_list_posts_with_keyword(self, base_url, auth_token):
        """GET /api/forum/posts - 关键词搜索"""
        resp = auth_get(f"{base_url}/api/forum/posts", auth_token,
                        params={"keyword": "选课", "page": 1, "size": 10})
        assert resp.json()["code"] == 200

    def test_05_post_detail(self, base_url, auth_token):
        """GET /api/forum/posts/{id} - 帖子详情"""
        resp = auth_get(f"{base_url}/api/forum/posts/{TestForum._post_id}", auth_token)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["title"] == "Test Post About Course Selection"

    def test_06_like_post(self, base_url, auth_token):
        """POST /api/forum/posts/{id}/like - 点赞"""
        resp = auth_post(f"{base_url}/api/forum/posts/{TestForum._post_id}/like", {}, auth_token)
        assert resp.json()["code"] == 200

    def test_07_create_comment(self, base_url, auth_token):
        """POST /api/forum/posts/{id}/comments - 评论"""
        resp = auth_post(f"{base_url}/api/forum/posts/{TestForum._post_id}/comments", {
            "content": "Course selection opens in the academic system"
        }, auth_token)
        assert resp.json()["code"] == 200

    def test_08_get_comments(self, base_url, auth_token):
        """GET /api/forum/posts/{id}/comments - 评论列表"""
        resp = auth_get(f"{base_url}/api/forum/posts/{TestForum._post_id}/comments",
                        auth_token, params={"page": 1, "size": 50})
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]["records"]) >= 1
