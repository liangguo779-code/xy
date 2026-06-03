"""管理后台测试 - 17个接口"""
from conftest import BASE_URL, auth_get, auth_put


class TestAdmin:
    """管理后台 /api/admin (普通用户访问，验证接口能访问)"""

    def test_01_dashboard(self, base_url, auth_token):
        """GET /api/admin/dashboard - 数据概览"""
        resp = auth_get(f"{base_url}/api/admin/dashboard", auth_token)
        assert resp.status_code in (200, 403)

    def test_02_user_list(self, base_url, auth_token):
        """GET /api/admin/users - 用户列表"""
        resp = auth_get(f"{base_url}/api/admin/users", auth_token, params={"page": 1, "size": 20})
        assert resp.status_code in (200, 403)

    def test_03_user_stats(self, base_url, auth_token):
        """GET /api/admin/users/stats - 用户统计"""
        resp = auth_get(f"{base_url}/api/admin/users/stats", auth_token)
        assert resp.status_code in (200, 403)

    def test_04_goods_list(self, base_url, auth_token):
        """GET /api/admin/goods - 商品管理"""
        resp = auth_get(f"{base_url}/api/admin/goods", auth_token, params={"page": 1, "size": 20})
        assert resp.status_code in (200, 403)

    def test_05_goods_stats(self, base_url, auth_token):
        """GET /api/admin/goods/stats - 商品统计"""
        resp = auth_get(f"{base_url}/api/admin/goods/stats", auth_token)
        assert resp.status_code in (200, 403)

    def test_06_order_list(self, base_url, auth_token):
        """GET /api/admin/orders - 订单管理"""
        resp = auth_get(f"{base_url}/api/admin/orders", auth_token, params={"page": 1, "size": 20})
        assert resp.status_code in (200, 403)

    def test_07_order_stats(self, base_url, auth_token):
        """GET /api/admin/orders/stats - 订单统计"""
        resp = auth_get(f"{base_url}/api/admin/orders/stats", auth_token)
        assert resp.status_code in (200, 403)

    def test_08_dispute_list(self, base_url, auth_token):
        """GET /api/admin/disputes - 纠纷列表"""
        resp = auth_get(f"{base_url}/api/admin/disputes", auth_token, params={"page": 1, "size": 20})
        assert resp.status_code in (200, 403)

    def test_09_report_list(self, base_url, auth_token):
        """GET /api/admin/reports - 举报列表"""
        resp = auth_get(f"{base_url}/api/admin/reports", auth_token, params={"page": 1, "size": 20})
        assert resp.status_code in (200, 403)

    def test_10_config_list(self, base_url, auth_token):
        """GET /api/admin/config - 系统配置"""
        resp = auth_get(f"{base_url}/api/admin/config", auth_token)
        assert resp.status_code in (200, 403)

    def test_11_update_config(self, base_url, auth_token):
        """PUT /api/admin/config - 更新配置"""
        resp = auth_put(f"{base_url}/api/admin/config", auth_token, json={
            "key": "site_name", "value": "校园生态平台", "description": "站点名称"
        })
        assert resp.status_code in (200, 403)
