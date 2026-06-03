"""评价模块测试 - 5个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post, auth_put


class TestReview:
    """评价模块 /api/reviews"""
    _order_id = None
    _token_b = None

    def _ensure_other_user(self, base_url):
        if TestReview._token_b:
            return TestReview._token_b
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "review_user_b", "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "review_user_b", "password": "123456"
        })
        TestReview._token_b = resp.json()["data"]["token"]
        return TestReview._token_b

    def _create_completed_order(self, base_url, auth_token):
        token_b = self._ensure_other_user(base_url)

        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Review Test Item", "price": 200
        }, auth_token)
        goods_id = resp.json()["data"]["id"]

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 0
        }, token_b)
        order_id = resp.json()["data"]["id"]

        auth_put(f"{base_url}/api/orders/{order_id}/confirm", auth_token)
        detail = auth_get(f"{base_url}/api/orders/{order_id}", auth_token)
        verify_code = detail.json()["data"]["verifyCode"]
        auth_put(f"{base_url}/api/orders/{order_id}/complete", auth_token,
                 params={"verifyCode": verify_code})

        return order_id, token_b

    def test_01_create_review(self, base_url, auth_token):
        """POST /api/reviews - 创建评价"""
        order_id, token_b = self._create_completed_order(base_url, auth_token)
        TestReview._order_id = order_id

        resp = auth_post(f"{base_url}/api/reviews", {
            "orderId": order_id, "rating": 5,
            "content": "Good seller", "tags": '["态度好","描述准确"]'
        }, token_b)
        assert resp.json()["code"] == 200

    def test_02_create_review_duplicate(self, base_url, auth_token):
        """POST /api/reviews - 重复评价应失败"""
        token_b = self._ensure_other_user(base_url)
        resp = auth_post(f"{base_url}/api/reviews", {
            "orderId": TestReview._order_id, "rating": 4, "content": "Review again"
        }, token_b)
        assert resp.json()["code"] != 200

    def test_03_get_user_reviews(self, base_url, auth_token):
        """GET /api/reviews/user/{userId} - 某用户评价"""
        resp = auth_get(f"{base_url}/api/reviews/user/1", auth_token)
        assert resp.json()["code"] == 200

    def test_04_get_my_reviews(self, base_url, auth_token):
        """GET /api/reviews/me - 我的评价"""
        resp = auth_get(f"{base_url}/api/reviews/me", auth_token)
        assert resp.json()["code"] == 200

    def test_05_get_my_rating(self, base_url, auth_token):
        """GET /api/reviews/me/rating - 我的评分"""
        resp = auth_get(f"{base_url}/api/reviews/me/rating", auth_token)
        assert resp.json()["code"] == 200

    def test_06_get_order_reviews(self, base_url, auth_token):
        """GET /api/reviews/order/{orderId} - 订单评价"""
        resp = auth_get(f"{base_url}/api/reviews/order/{TestReview._order_id}", auth_token)
        assert resp.json()["code"] == 200
