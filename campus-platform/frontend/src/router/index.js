import { createRouter, createWebHistory } from 'vue-router'

const routes = [
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
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/user/NotificationList.vue') },
      { path: 'dispatch', name: 'DispatchHall', component: () => import('@/views/delivery/DispatchHall.vue') }
    ]
  },
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
      { path: 'bans', name: 'AdminBans', component: () => import('@/views/admin/BanManage.vue') },
      { path: 'forum', name: 'AdminForum', component: () => import('@/views/admin/ForumManage.vue') },
      { path: 'config', name: 'AdminConfig', component: () => import('@/views/admin/SystemConfig.vue') },
      { path: 'knowledge', name: 'AdminKnowledge', component: () => import('@/views/admin/KnowledgeManage.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
