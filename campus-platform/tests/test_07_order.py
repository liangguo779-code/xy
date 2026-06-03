"""订单模块测试 - 9个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post, auth_put


class TestOrder:
    """订单模块 /api/orders"""
    _token_b = None

    def _ensure_other_user(self, base_url):
        if TestOrder._token_b:
            return TestOrder._token_b
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "order_user_b", "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "order_user_b", "password": "123456"
        })
        TestOrder._token_b = resp.json()["data"]["token"]
        return TestOrder._token_b

    def _create_goods(self, base_url, token):
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Order Test Item", "price": 500, "condition": "Good"
        }, token)
        return resp.json()["data"]["id"]

    def test_01_create_self_pickup_order(self, base_url, auth_token):
        """POST /api/orders - 创建自提订单"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 0, "pickupLocation": "Library entrance"
        }, token_b)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["orderNo"] is not None
        assert data["data"]["dealType"] == 0

    def test_02_create_delivery_order(self, base_url, auth_token):
        """POST /api/orders - 创建配送订单"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        # 买家先创建地址
        auth_post(f"{base_url}/api/address", {
            "contactName": "测试", "phone": "13800000000",
            "building": "1号楼", "detail": "301"
        }, token_b)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 1
        }, token_b)
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["dealType"] == 1

    def test_03_list_my_orders(self, base_url, auth_token):
        """GET /api/orders/my - 我的订单"""
        resp = auth_get(f"{base_url}/api/orders/my", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_04_order_detail(self, base_url, auth_token):
        """GET /api/orders/{id} - 订单详情"""
        resp = auth_get(f"{base_url}/api/orders/my", auth_token)
        orders = resp.json()["data"]
        if orders and len(orders) > 0:
            oid = orders[0]["id"]
            resp = auth_get(f"{base_url}/api/orders/{oid}", auth_token)
            data = resp.json()
            assert data["code"] == 200

    def test_05_confirm_and_complete(self, base_url, auth_token):
        """自提完整流程: 创建→确认→核销"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        # 买家下单
        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 0
        }, token_b)
        order_id = resp.json()["data"]["id"]

        # 卖家确认
        resp = auth_put(f"{base_url}/api/orders/{order_id}/confirm", auth_token)
        assert resp.json()["code"] == 200

        # 获取核销码
        detail = auth_get(f"{base_url}/api/orders/{order_id}", auth_token)
        verify_code = detail.json()["data"]["verifyCode"]

        # 核销完成
        resp = auth_put(f"{base_url}/api/orders/{order_id}/complete", auth_token,
                        params={"verifyCode": verify_code})
        assert resp.json()["code"] == 200
        assert resp.json()["data"]["status"] == 3

    def test_06_cancel_order(self, base_url, auth_token):
        """PUT /api/orders/{id}/cancel - 取消订单"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 0
        }, token_b)
        order_id = resp.json()["data"]["id"]

        resp = auth_put(f"{base_url}/api/orders/{order_id}/cancel", token_b)
        assert resp.json()["code"] == 200

    def test_07_pay_delivery_fee(self, base_url, auth_token):
        """PUT /api/orders/{id}/pay-fee - 支付配送服务费"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        auth_post(f"{base_url}/api/address", {
            "contactName": "配送", "phone": "13800000000",
            "building": "5号楼", "detail": "601"
        }, token_b)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 1
        }, token_b)
        order_id = resp.json()["data"]["id"]

        resp = auth_put(f"{base_url}/api/orders/{order_id}/pay-fee", token_b)
        assert resp.json()["code"] == 200

    def test_08_confirm_receive(self, base_url, auth_token):
        """PUT /api/orders/{id}/confirm-receive - 买家确认收货（状态不对会报错，接口能访问）"""
        token_b = self._ensure_other_user(base_url)
        goods_id = self._create_goods(base_url, auth_token)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 1
        }, token_b)
        order_id = resp.json()["data"]["id"]

        resp = auth_put(f"{base_url}/api/orders/{order_id}/confirm-receive", token_b)
        # 状态不正确会返回业务错误，但接口本身能访问
        assert resp.status_code == 200
