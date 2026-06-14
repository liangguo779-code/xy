<template>
  <div class="skeleton-card" :style="{ width, height }">
    <div v-if="type === 'goods'" class="skeleton-goods">
      <div class="skeleton-image"></div>
      <div class="skeleton-body">
        <div class="skeleton-line skeleton-line-80"></div>
        <div class="skeleton-line skeleton-line-60"></div>
        <div class="skeleton-line skeleton-line-40"></div>
      </div>
    </div>
    <div v-else-if="type === 'post'" class="skeleton-post">
      <div class="skeleton-line skeleton-line-60"></div>
      <div class="skeleton-line skeleton-line-100"></div>
      <div class="skeleton-line skeleton-line-80"></div>
      <div class="skeleton-line skeleton-line-40"></div>
    </div>
    <div v-else-if="type === 'comment'" class="skeleton-comment">
      <div class="skeleton-avatar"></div>
      <div class="skeleton-body">
        <div class="skeleton-line skeleton-line-30"></div>
        <div class="skeleton-line skeleton-line-80"></div>
      </div>
    </div>
    <div v-else class="skeleton-generic">
      <div class="skeleton-line skeleton-line-100"></div>
      <div class="skeleton-line skeleton-line-80"></div>
      <div class="skeleton-line skeleton-line-60"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'generic' },
  width: { type: String, default: '100%' },
  height: { type: String, default: 'auto' }
})
</script>

<style scoped>
.skeleton-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.skeleton-goods .skeleton-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  background: linear-gradient(90deg, #F2F3F5 25%, #E5E6EB 37%, #F2F3F5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

.skeleton-goods .skeleton-body {
  padding: 12px;
}

.skeleton-post {
  padding: 16px;
}

.skeleton-comment {
  display: flex;
  gap: 10px;
  padding: 12px;
}

.skeleton-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(90deg, #F2F3F5 25%, #E5E6EB 37%, #F2F3F5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
  flex-shrink: 0;
}

.skeleton-body {
  flex: 1;
}

.skeleton-generic {
  padding: 16px;
}

.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, #F2F3F5 25%, #E5E6EB 37%, #F2F3F5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
  margin-bottom: 10px;
}

.skeleton-line-30 { width: 30%; }
.skeleton-line-40 { width: 40%; }
.skeleton-line-60 { width: 60%; }
.skeleton-line-80 { width: 80%; }
.skeleton-line-100 { width: 100%; }

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
</style>
