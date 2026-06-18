"""配送模块测试 - 7个接口"""
import requests
from conftest import BASE_URL, auth_get, auth_post, auth_put


class TestDelivery:
    """配送模块 /api/delivery"""
    _delivery_id = None
    _runner_token = None

    def _ensure_runner(self, base_url):
        if TestDelivery._runner_token:
            return TestDelivery._runner_token
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "runner_test", "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "runner_test", "password": "123456"
        })
        TestDelivery._runner_token = resp.json()["data"]["token"]
        return TestDelivery._runner_token

    def _create_delivery_order(self, base_url, buyer_token, seller_token):
        resp = auth_post(f"{base_url}/api/goods", {
            "title": "Delivery Test Item", "price": 300
        }, seller_token)
        goods_id = resp.json()["data"]["id"]

        auth_post(f"{base_url}/api/address", {
            "contactName": "配送", "phone": "13800000000",
            "building": "5号楼", "detail": "601"
        }, buyer_token)

        resp = auth_post(f"{base_url}/api/orders", {
            "goodsId": goods_id, "dealType": 1
        }, buyer_token)
        order_id = resp.json()["data"]["id"]

        auth_put(f"{base_url}/api/orders/{order_id}/pay-fee", buyer_token)
        return order_id

    def test_01_pending_orders(self, base_url, auth_token):
        """GET /api/delivery/pending - 待接单列表"""
        resp = auth_get(f"{base_url}/api/delivery/pending", auth_token)
        data = resp.json()
        assert data["code"] == 200

    def test_02_accept_order(self, base_url, auth_token):
        """PUT /api/delivery/{id}/accept - 交付员接单"""
        runner_token = self._ensure_runner(base_url)

        # 创建配送订单
        requests.post(f"{base_url}/api/auth/register", json={
            "username": "delivery_buyer", "password": "123456"
        })
        resp = requests.post(f"{base_url}/api/auth/login", json={
            "username": "delivery_buyer", "password": "123456"
        })
        buyer_token = resp.json()["data"]["token"]
        self._create_delivery_order(base_url, buyer_token, auth_token)

        # 获取待接单
        resp = auth_get(f"{base_url}/api/delivery/pending", runner_token)
        pending = resp.json()["data"]
        if pending and len(pending) > 0:
            delivery_id = pending[0]["id"]
            TestDelivery._delivery_id = delivery_id
            resp = auth_put(f"{base_url}/api/delivery/{delivery_id}/accept", runner_token)
            assert resp.json()["code"] == 200

    def test_03_my_deliveries(self, base_url, auth_token):
        """GET /api/delivery/my - 我的工单"""
        runner_token = self._ensure_runner(base_url)
        resp = auth_get(f"{base_url}/api/delivery/my", runner_token)
        assert resp.json()["code"] == 200

    def test_04_pickup_goods(self, base_url, auth_token):
        """PUT /api/delivery/{id}/pickup - 取货"""
        if TestDelivery._delivery_id:
            runner_token = self._ensure_runner(base_url)
            resp = auth_put(f"{base_url}/api/delivery/{TestDelivery._delivery_id}/pickup",
                            runner_token, params={"photoUrl": "http://example.com/pickup.jpg"})
            assert resp.status_code == 200

    def test_05_deliver_goods(self, base_url, auth_token):
        """PUT /api/delivery/{id}/deliver - 送达"""
        if TestDelivery._delivery_id:
            runner_token = self._ensure_runner(base_url)
            resp = auth_put(f"{base_url}/api/delivery/{TestDelivery._delivery_id}/deliver",
                            runner_token, params={"photoUrl": "http://example.com/deliver.jpg"})
            assert resp.status_code == 200

    def test_06_get_tracks(self, base_url, auth_token):
        """GET /api/delivery/{id}/tracks - 物流轨迹"""
        if TestDelivery._delivery_id:
            resp = auth_get(f"{base_url}/api/delivery/{TestDelivery._delivery_id}/tracks", auth_token)
            assert resp.json()["code"] == 200

    def test_07_report_location(self, base_url, auth_token):
        """POST /api/delivery/{id}/location - 上报位置"""
        if TestDelivery._delivery_id:
            runner_token = self._ensure_runner(base_url)
            resp = auth_post(f"{base_url}/api/delivery/{TestDelivery._delivery_id}/location",
                             {"lat": 31.6, "lng": 118.5, "address": "教学楼"}, runner_token)
            assert resp.status_code == 200
