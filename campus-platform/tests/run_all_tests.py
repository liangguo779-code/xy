"""独立测试脚本 - 不依赖pytest，直接运行验证全部接口"""
import requests
import time
import json

BASE_URL = "http://localhost:8080"
AI_URL = "http://localhost:8000"

# 测试结果统计
passed = 0
failed = 0
errors = []


def test(name, func):
    global passed, failed, errors
    try:
        result = func()
        if result:
            print(f"  PASS  {name}")
            passed += 1
        else:
            print(f"  FAIL  {name}")
            failed += 1
            errors.append(name)
    except Exception as e:
        print(f"  ERROR {name}: {e}")
        failed += 1
        errors.append(f"{name}: {e}")


def auth(token):
    return {"Authorization": f"Bearer {token}"} if token else {}


# ========== 初始化 ==========
print("\n=== 初始化 ===")
username = f"test_{int(time.time())}"
password = "test123456"

r = requests.post(f"{BASE_URL}/api/auth/register", json={"username": username, "password": password})
assert r.json()["code"] == 200, f"注册失败: {r.json()}"

r = requests.post(f"{BASE_URL}/api/auth/login", json={"username": username, "password": password})
assert r.json()["code"] == 200, f"登录失败: {r.json()}"
TOKEN = r.json()["data"]["token"]
USER_ID = r.json()["data"]["user"]["id"]
print(f"  用户: {username}, Token: {TOKEN[:20]}...")

# 创建第二个用户
username2 = f"test2_{int(time.time())}"
requests.post(f"{BASE_URL}/api/auth/register", json={"username": username2, "password": password})
r = requests.post(f"{BASE_URL}/api/auth/login", json={"username": username2, "password": password})
TOKEN2 = r.json()["data"]["token"]
USER2_ID = r.json()["data"]["user"]["id"]

# 获取管理员token
r = requests.post(f"{BASE_URL}/api/auth/login", json={"username": "admin", "password": "admin123"})
ADMIN_TOKEN = r.json()["data"]["token"] if r.json()["code"] == 200 else TOKEN

# ========== 1. 认证模块 ==========
print("\n=== 1. 认证模块 /api/auth ===")
dup_user = f"dup_{int(time.time())}"


def t01(): return requests.post(f"{BASE_URL}/api/auth/register", json={"username": dup_user, "password": "123456"}).json()["code"] == 200
def t02(): return requests.post(f"{BASE_URL}/api/auth/register", json={"username": dup_user, "password": "123456"}).json()["code"] != 200
def t03(): return requests.post(f"{BASE_URL}/api/auth/login", json={"username": username, "password": password}).json()["code"] == 200
def t04(): return requests.post(f"{BASE_URL}/api/auth/login", json={"username": username, "password": "wrong"}).json()["code"] == 401
def t05(): return requests.get(f"{BASE_URL}/api/auth/me", headers=auth(TOKEN)).json()["code"] == 200
def t06(): return requests.get(f"{BASE_URL}/api/auth/me").status_code == 401
def t07(): return requests.post(f"{BASE_URL}/api/auth/send-code", params={"phone": "13800000000"}).status_code in (200, 500)
def t08(): return requests.post(f"{BASE_URL}/api/auth/reset-password", json={"phone": "13800000000", "code": "000000", "newPassword": "new"}).json()["code"] != 200


test("注册", t01)
test("重复注册失败", t02)
test("登录成功", t03)
test("密码错误", t04)
test("获取用户信息", t05)
test("未登录401", t06)
test("发送验证码", t07)
test("验证码错误", t08)

# ========== 2. 用户模块 ==========
print("\n=== 2. 用户模块 /api/user ===")


def t10(): return requests.put(f"{BASE_URL}/api/user/profile", headers=auth(TOKEN), json={"nickname": "New Name", "phone": "13900001111"}).json()["code"] == 200
def t11(): return requests.put(f"{BASE_URL}/api/user/password", headers=auth(TOKEN), json={"oldPassword": "wrong", "newPassword": "new"}).json()["code"] != 200


test("修改个人信息", t10)
test("旧密码错误", t11)

# ========== 3. 地址模块 ==========
print("\n=== 3. 地址模块 /api/address ===")
ADDR_ID = None


def t20():
    global ADDR_ID
    r = requests.post(f"{BASE_URL}/api/address", headers=auth(TOKEN), json={
        "contactName": "Zhang", "phone": "13800001111", "building": "B1", "detail": "305", "isDefault": 1
    })
    ADDR_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t21(): return requests.get(f"{BASE_URL}/api/address", headers=auth(TOKEN)).json()["code"] == 200
def t22(): return requests.get(f"{BASE_URL}/api/address/" + str(ADDR_ID), headers=auth(TOKEN)).json()["code"] == 200
def t23(): return requests.get(f"{BASE_URL}/api/address/default", headers=auth(TOKEN)).json()["code"] == 200
def t24(): return requests.put(f"{BASE_URL}/api/address", headers=auth(TOKEN), json={"id": ADDR_ID, "contactName": "Li", "phone": "13900002222", "building": "B2", "detail": "401"}).json()["code"] == 200
def t25(): return requests.delete(f"{BASE_URL}/api/address/" + str(ADDR_ID), headers=auth(TOKEN)).json()["code"] == 200


test("新增地址", t20)
test("地址列表", t21)
test("地址详情", t22)
test("默认地址", t23)
test("修改地址", t24)
test("删除地址", t25)

# ========== 4. 商品模块 ==========
print("\n=== 4. 商品模块 /api/goods ===")
GOODS_ID = None


def t30():
    global GOODS_ID
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={
        "title": "Test Laptop", "description": "Good condition", "price": 2500, "condition": "Good"
    })
    GOODS_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t31(): return requests.get(f"{BASE_URL}/api/goods", headers=auth(TOKEN), params={"page": 1, "size": 10}).json()["code"] == 200
def t32(): return requests.get(f"{BASE_URL}/api/goods", headers=auth(TOKEN), params={"keyword": "Laptop", "page": 1, "size": 10}).json()["code"] == 200
def t33(): return requests.get(f"{BASE_URL}/api/goods", headers=auth(TOKEN), params={"minPrice": 100, "maxPrice": 5000, "page": 1, "size": 10}).json()["code"] == 200
def t34(): return requests.get(f"{BASE_URL}/api/goods", headers=auth(TOKEN), params={"sortBy": "price_asc", "page": 1, "size": 10}).json()["code"] == 200
def t35(): return requests.get(f"{BASE_URL}/api/goods/recommend", headers=auth(TOKEN), params={"page": 1, "size": 10}).json()["code"] == 200
def t36(): return requests.get(f"{BASE_URL}/api/goods/" + str(GOODS_ID), headers=auth(TOKEN)).json()["code"] == 200
def t37(): return requests.put(f"{BASE_URL}/api/goods/" + str(GOODS_ID), headers=auth(TOKEN), json={"title": "Updated"}).json()["code"] == 200
def t38():
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={"title": "Sold Item", "price": 100})
    gid = r.json()["data"]["id"]
    return requests.put(f"{BASE_URL}/api/goods/" + str(gid) + "/sold", headers=auth(TOKEN)).json()["code"] == 200
def t39(): return requests.delete(f"{BASE_URL}/api/goods/" + str(GOODS_ID), headers=auth(TOKEN)).json()["code"] == 200


test("发布商品", t30)
test("商品列表", t31)
test("关键词搜索", t32)
test("价格筛选", t33)
test("排序", t34)
test("推荐商品", t35)
test("商品详情", t36)
test("编辑商品", t37)
test("标记已售", t38)
test("删除商品", t39)

# ========== 5. 分类模块 ==========
print("\n=== 5. 分类模块 /api/categories ===")


def t40(): return requests.get(f"{BASE_URL}/api/categories", headers=auth(TOKEN)).json()["code"] == 200


test("分类列表", t40)

# ========== 6. 聊天模块 ==========
print("\n=== 6. 聊天模块 /api/chat ===")
SESSION_ID = None


def t50():
    global SESSION_ID
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={"title": "Chat Item", "price": 100})
    gid = r.json()["data"]["id"]
    r = requests.post(f"{BASE_URL}/api/chat/session?goodsId=" + str(gid), headers=auth(TOKEN2), json={})
    SESSION_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t51(): return requests.get(f"{BASE_URL}/api/chat/sessions", headers=auth(TOKEN)).json()["code"] == 200
def t52(): return requests.post(f"{BASE_URL}/api/chat/messages", headers=auth(TOKEN), json={"sessionId": SESSION_ID, "content": "Hello?", "msgType": 0}).json()["code"] == 200
def t53(): return requests.get(f"{BASE_URL}/api/chat/messages/" + str(SESSION_ID), headers=auth(TOKEN), params={"page": 1, "size": 50}).json()["code"] == 200


test("创建会话", t50)
test("会话列表", t51)
test("发送消息", t52)
test("历史消息", t53)

# ========== 7. 订单模块 ==========
print("\n=== 7. 订单模块 /api/orders ===")
ORDER_ID = None


def t60():
    global ORDER_ID
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={"title": "Order Item", "price": 500})
    gid = r.json()["data"]["id"]
    r = requests.post(f"{BASE_URL}/api/orders", headers=auth(TOKEN2), json={"goodsId": gid, "dealType": 0, "pickupLocation": "Library"})
    ORDER_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t61(): return requests.get(f"{BASE_URL}/api/orders/my", headers=auth(TOKEN)).json()["code"] == 200
def t62(): return requests.get(f"{BASE_URL}/api/orders/" + str(ORDER_ID), headers=auth(TOKEN)).json()["code"] == 200


def t63():
    r = requests.put(f"{BASE_URL}/api/orders/" + str(ORDER_ID) + "/confirm", headers=auth(TOKEN))
    if r.json()["code"] != 200: return False
    detail = requests.get(f"{BASE_URL}/api/orders/" + str(ORDER_ID), headers=auth(TOKEN)).json()
    code = detail["data"]["verifyCode"]
    r = requests.put(f"{BASE_URL}/api/orders/" + str(ORDER_ID) + "/complete", headers=auth(TOKEN), params={"verifyCode": code})
    return r.json()["code"] == 200


def t64():
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={"title": "Cancel Item", "price": 100})
    gid = r.json()["data"]["id"]
    r = requests.post(f"{BASE_URL}/api/orders", headers=auth(TOKEN2), json={"goodsId": gid, "dealType": 0})
    oid = r.json()["data"]["id"]
    return requests.put(f"{BASE_URL}/api/orders/" + str(oid) + "/cancel", headers=auth(TOKEN2)).json()["code"] == 200


test("创建自提订单", t60)
test("我的订单", t61)
test("订单详情", t62)
test("确认+核销完成", t63)
test("取消订单", t64)

# ========== 8. 配送模块 ==========
print("\n=== 8. 配送模块 /api/delivery ===")


def t70(): return requests.get(f"{BASE_URL}/api/delivery/pending", headers=auth(TOKEN)).json()["code"] == 200


test("待接单列表", t70)

# ========== 9. 评价模块 ==========
print("\n=== 9. 评价模块 /api/reviews ===")


def t80(): return requests.get(f"{BASE_URL}/api/reviews/me", headers=auth(TOKEN)).json()["code"] == 200
def t81(): return requests.get(f"{BASE_URL}/api/reviews/me/rating", headers=auth(TOKEN)).json()["code"] == 200


test("我的评价", t80)
test("我的评分", t81)

# ========== 10. 收藏模块 ==========
print("\n=== 10. 收藏模块 /api/favorites ===")
FAV_ID = None


def t90():
    global FAV_ID
    r = requests.post(f"{BASE_URL}/api/goods", headers=auth(TOKEN), json={"title": "Fav Item", "price": 100})
    FAV_ID = r.json()["data"]["id"]
    return requests.post(f"{BASE_URL}/api/favorites/" + str(FAV_ID), headers=auth(TOKEN2), json={}).json()["code"] == 200


def t91(): return requests.get(f"{BASE_URL}/api/favorites/check/" + str(FAV_ID), headers=auth(TOKEN2)).json()["code"] == 200
def t92(): return requests.get(f"{BASE_URL}/api/favorites", headers=auth(TOKEN2)).json()["code"] == 200
def t93(): return requests.delete(f"{BASE_URL}/api/favorites/" + str(FAV_ID), headers=auth(TOKEN2)).json()["code"] == 200


test("收藏商品", t90)
test("检查收藏", t91)
test("收藏列表", t92)
test("取消收藏", t93)

# ========== 11. 关注模块 ==========
print("\n=== 11. 关注模块 /api/follow ===")


def t100(): return requests.post(f"{BASE_URL}/api/follow/" + str(USER_ID), headers=auth(TOKEN2), json={}).json()["code"] == 200
def t101(): return requests.get(f"{BASE_URL}/api/follow/check/" + str(USER_ID), headers=auth(TOKEN2)).json()["code"] == 200
def t102(): return requests.get(f"{BASE_URL}/api/follow/count/" + str(USER_ID), headers=auth(TOKEN2)).json()["code"] == 200
def t103(): return requests.delete(f"{BASE_URL}/api/follow/" + str(USER_ID), headers=auth(TOKEN2)).json()["code"] == 200


test("关注用户", t100)
test("检查关注", t101)
test("关注计数", t102)
test("取消关注", t103)

# ========== 12. 论坛模块 ==========
print("\n=== 12. 论坛模块 /api/forum ===")
POST_ID = None


def t110():
    global POST_ID
    r = requests.post(f"{BASE_URL}/api/forum/posts", headers=auth(TOKEN), json={
        "title": "Test Post", "content": "Hello everyone", "category": "General"
    })
    POST_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t111(): return requests.get(f"{BASE_URL}/api/forum/posts", headers=auth(TOKEN), params={"page": 1, "size": 10}).json()["code"] == 200
def t112(): return requests.get(f"{BASE_URL}/api/forum/posts/" + str(POST_ID), headers=auth(TOKEN)).json()["code"] == 200
def t113(): return requests.post(f"{BASE_URL}/api/forum/posts/" + str(POST_ID) + "/like", headers=auth(TOKEN), json={}).json()["code"] == 200
def t114(): return requests.post(f"{BASE_URL}/api/forum/posts/" + str(POST_ID) + "/comments", headers=auth(TOKEN), json={"content": "Nice post"}).json()["code"] == 200
def t115(): return requests.get(f"{BASE_URL}/api/forum/posts/" + str(POST_ID) + "/comments", headers=auth(TOKEN), params={"page": 1, "size": 50}).json()["code"] == 200


test("发帖", t110)
test("帖子列表", t111)
test("帖子详情", t112)
test("点赞帖子", t113)
test("发表评论", t114)
test("评论列表", t115)

# ========== 13. 纠纷/举报 ==========
print("\n=== 13. 纠纷/举报 ===")


def t120(): return requests.post(f"{BASE_URL}/api/disputes", headers=auth(TOKEN), json={"orderId": 1, "reason": "Not as described"}).json()["code"] == 200
def t121(): return requests.get(f"{BASE_URL}/api/disputes/my", headers=auth(TOKEN)).json()["code"] == 200
def t122(): return requests.post(f"{BASE_URL}/api/reports", headers=auth(TOKEN), json={"targetType": "goods", "targetId": 1, "reason": "Fraud"}).json()["code"] == 200


test("提交纠纷", t120)
test("我的纠纷", t121)
test("提交举报", t122)

# ========== 14. 通知模块 ==========
print("\n=== 14. 通知模块 /api/notifications ===")


def t130(): return requests.get(f"{BASE_URL}/api/notifications", headers=auth(TOKEN), params={"page": 1, "size": 20}).json()["code"] == 200
def t131(): return requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=auth(TOKEN)).json()["code"] == 200
def t132(): return requests.put(f"{BASE_URL}/api/notifications/read-all", headers=auth(TOKEN)).json()["code"] == 200


test("通知列表", t130)
test("未读数", t131)
test("全部已读", t132)

# ========== 15. 公告模块 ==========
print("\n=== 15. 公告模块 /api/announcements ===")
ANN_ID = None


def t140():
    global ANN_ID
    r = requests.post(f"{BASE_URL}/api/announcements", headers=auth(ADMIN_TOKEN), json={"title": "Notice", "content": "Hello", "type": "normal"})
    if r.json()["code"] == 200:
        ANN_ID = r.json()["data"]["id"]
    return r.json()["code"] == 200


def t141(): return requests.get(f"{BASE_URL}/api/announcements", headers=auth(TOKEN), params={"page": 1, "size": 10}).json()["code"] == 200
def t142(): return requests.put(f"{BASE_URL}/api/announcements/" + str(ANN_ID) if ANN_ID else "0", headers=auth(ADMIN_TOKEN), json={"title": "Updated"}).json()["code"] == 200
def t143(): return requests.delete(f"{BASE_URL}/api/announcements/" + str(ANN_ID) if ANN_ID else "0", headers=auth(ADMIN_TOKEN)).json()["code"] == 200


test("创建公告", t140)
test("公告列表", t141)
test("更新公告", t142)
test("删除公告", t143)

# ========== 16. 文件上传 ==========
print("\n=== 16. 文件上传 /api/upload ===")


def t150():
    import io
    fake = io.BytesIO(b'\x89PNG\r\n\x1a\n' + b'\x00' * 100)
    r = requests.post(f"{BASE_URL}/api/upload/file", headers=auth(TOKEN),
                      files={"file": ("test.txt", io.BytesIO(b"hello"), "text/plain")},
                      params={"directory": "test"})
    return r.json()["code"] == 200


test("文件上传", t150)

# ========== 17. AI 中台 ==========
print("\n=== 17. AI 中台 ===")


def t160(): return requests.get(f"{AI_URL}/health").json()["status"] == "ok"
def t161(): return "answer" in requests.post(f"{AI_URL}/chat", json={"question": "学分怎么认定?"}).json()
def t162(): return requests.get(f"{AI_URL}/knowledge/list").json()["files"] is not None


test("AI健康检查", t160)
test("AI问答", t161)
test("知识库列表", t162)

# ========== 18. 管理后台 ==========
print("\n=== 18. 管理后台 /api/admin ===")


def t170(): return requests.get(f"{BASE_URL}/api/admin/dashboard", headers=auth(TOKEN)).status_code in (200, 403)
def t171(): return requests.get(f"{BASE_URL}/api/admin/users", headers=auth(TOKEN), params={"page": 1, "size": 20}).status_code in (200, 403)
def t172(): return requests.get(f"{BASE_URL}/api/admin/goods", headers=auth(TOKEN), params={"page": 1, "size": 20}).status_code in (200, 403)
def t173(): return requests.get(f"{BASE_URL}/api/admin/orders", headers=auth(TOKEN), params={"page": 1, "size": 20}).status_code in (200, 403)
def t174(): return requests.get(f"{BASE_URL}/api/admin/config", headers=auth(TOKEN)).status_code in (200, 403)


test("Dashboard", t170)
test("用户管理", t171)
test("商品管理", t172)
test("订单管理", t173)
test("系统配置", t174)

# ========== 19. 退出登录（最后执行） ==========
print("\n=== 19. 退出登录 ===")
def t180(): return requests.post(f"{BASE_URL}/api/auth/logout", headers=auth(TOKEN)).json()["code"] == 200
test("退出登录", t180)

# ========== 汇总 ==========
print(f"\n{'='*50}")
print(f"总计: {passed + failed}  通过: {passed}  失败: {failed}")
if errors:
    print(f"\n失败用例:")
    for e in errors:
        print(f"  - {e}")
print(f"{'='*50}")
