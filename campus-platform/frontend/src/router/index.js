import { createRouter, createWebHistory } from 'vue-router'

// 路由表：免登录页、主站布局、管理后台三大块，页面组件均懒加载
const routes = [
  // 免登录页面（meta.public 为 true 时不受登录守卫限制）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/user/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/user/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/user/ForgotPassword.vue'),
    meta: { public: true }
  },
  // 主站页面（Layout 侧边导航），默认落地商品列表
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/goods',
    children: [
      { path: 'goods', name: 'Goods', component: () => import('@/views/trade/GoodsList.vue') },
      { path: 'goods/:id', name: 'GoodsDetail', component: () => import('@/views/trade/GoodsDetail.vue') },
      { path: 'seller/:userId', name: 'SellerProfile', component: () => import('@/views/trade/SellerProfile.vue') },
      { path: 'my-goods', name: 'MyGoods', component: () => import('@/views/trade/MyGoods.vue') },
      { path: 'orders', name: 'Orders', component: () => import('@/views/trade/OrderList.vue') },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('@/views/trade/OrderDetail.vue') },
      { path: 'crazy-thursday', name: 'CrazyThursday', component: () => import('@/views/trade/CrazyThursday.vue') },
      { path: 'chat', name: 'ChatList', component: () => import('@/views/chat/ChatList.vue') },
      { path: 'chat/:sessionId', name: 'ChatRoom', component: () => import('@/views/chat/ChatRoom.vue') },
      { path: 'forum', name: 'Forum', component: () => import('@/views/forum/ForumList.vue') },
      { path: 'forum/:id', name: 'PostDetail', component: () => import('@/views/forum/PostDetail.vue') },
      { path: 'ai', name: 'AiChat', component: () => import('@/views/ai/AiChat.vue') },
      { path: 'address', name: 'Address', component: () => import('@/views/user/AddressList.vue') },
      { path: 'profile', name: 'ProfileEdit', component: () => import('@/views/user/ProfileEdit.vue') },
      { path: 'favorites', name: 'Favorites', component: () => import('@/views/user/Favorites.vue') },
      { path: 'browse-history', name: 'BrowseHistory', component: () => import('@/views/user/BrowseHistory.vue') },
      { path: 'my-reports', name: 'MyReports', component: () => import('@/views/user/MyReports.vue') },
      { path: 'my-disputes', name: 'MyDisputes', component: () => import('@/views/user/MyDisputes.vue') },
      { path: 'my-reviews', name: 'MyReviews', component: () => import('@/views/user/MyReviews.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/user/NotificationList.vue') },
      { path: 'dispatch', name: 'DispatchHall', component: () => import('@/views/delivery/DispatchHall.vue') }
    ]
  },
  // 管理后台页面（AdminLayout，角色权限在页面与后端二次校验）
  {
    path: '/admin',
    component: () => import('@/components/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/UserManage.vue') },
      { path: 'goods', name: 'AdminGoods', component: () => import('@/views/admin/GoodsAudit.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/OrderMonitor.vue') },
      { path: 'disputes', name: 'AdminDisputes', component: () => import('@/views/admin/DisputeManage.vue') },
      { path: 'reports', name: 'AdminReports', component: () => import('@/views/admin/ReportManage.vue') },
      { path: 'reviews', name: 'AdminReviews', component: () => import('@/views/admin/ReviewManage.vue') },
      { path: 'bans', name: 'AdminBans', component: () => import('@/views/admin/BanManage.vue') },
      { path: 'forum', name: 'AdminForum', component: () => import('@/views/admin/ForumManage.vue') },
      { path: 'config', name: 'AdminConfig', component: () => import('@/views/admin/SystemConfig.vue') },
      { path: 'knowledge', name: 'AdminKnowledge', component: () => import('@/views/admin/KnowledgeManage.vue') },
      { path: 'moderation', name: 'AdminModeration', component: () => import('@/views/admin/Moderation.vue') }
    ]
  }
]

// history 模式路由：部署时需后端将非静态路径回退到 index.html，否则刷新会 404
const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局守卫：未登录且目标页非公开时跳登录页。
// 这里只判断本地是否存有 token，不校验有效性；令牌失效由 axios 拦截器统一跳转处理
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
