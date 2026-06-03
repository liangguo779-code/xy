"""AI 中台测试"""
import requests
from conftest import BASE_URL, AI_URL


class TestAI:
    """AI 中台"""

    def test_01_health(self, ai_url):
        """GET /health - 健康检查"""
        resp = requests.get(f"{ai_url}/health")
        data = resp.json()
        assert data["status"] == "ok"

    def test_02_chat(self, ai_url):
        """POST /chat - RAG问答"""
        resp = requests.post(f"{ai_url}/chat", json={
            "question": "学分怎么认定？"
        })
        data = resp.json()
        assert "answer" in data
        assert len(data["answer"]) > 0

    def test_03_chat_from_backend(self, base_url, auth_token):
        """POST /api/ai/chat - 通过后端调用AI"""
        resp = requests.post(f"{base_url}/api/ai/chat",
                             json={"question": "重修流程是什么？"},
                             headers={"Authorization": f"Bearer {auth_token}"})
        data = resp.json()
        assert data["code"] == 200
        assert len(data["data"]["answer"]) > 0

    def test_04_knowledge_list(self, ai_url):
        """GET /knowledge/list - 知识库文件列表"""
        resp = requests.get(f"{ai_url}/knowledge/list")
        data = resp.json()
        assert "files" in data
        assert len(data["files"]) >= 1
