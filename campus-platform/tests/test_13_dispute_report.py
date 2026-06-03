"""纠纷仲裁 & 举报投诉测试"""
from conftest import BASE_URL, auth_get, auth_post


class TestDispute:
    """纠纷模块 /api/disputes"""

    def test_01_create_dispute(self, base_url, auth_token):
        """POST /api/disputes - 提交纠纷"""
        resp = auth_post(f"{base_url}/api/disputes", {
            "orderId": 1,
            "reason": "Item not as described",
            "evidenceImages": '["http://example.com/evidence.jpg"]'
        }, auth_token)
        assert resp.json()["code"] == 200

    def test_02_my_disputes(self, base_url, auth_token):
        """GET /api/disputes/my - 我的纠纷"""
        resp = auth_get(f"{base_url}/api/disputes/my", auth_token)
        assert resp.json()["code"] == 200


class TestReport:
    """举报模块 /api/reports"""

    def test_01_create_report(self, base_url, auth_token):
        """POST /api/reports - 提交举报"""
        resp = auth_post(f"{base_url}/api/reports", {
            "targetType": "goods",
            "targetId": 1,
            "reason": "Suspected fraud item"
        }, auth_token)
        assert resp.json()["code"] == 200
