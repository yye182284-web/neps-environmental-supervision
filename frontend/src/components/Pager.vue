<template>
  <footer class="pager">
    <button :disabled="page === 1" @click="$emit('change', page - 1)">上一页</button>
    <span>第 {{ page }} / {{ pages }} 页</span>
    <button :disabled="page === pages" @click="$emit('change', page + 1)">下一页</button>
  </footer>
</template>

<script>
export default { name: 'DataPager', props: { page: Number, pages: Number }, emits: ['change'] }
</script>

<style scoped>
.pager{display:flex;justify-content:flex-end;align-items:center;gap:11px;margin:14px -22px -22px;padding:13px 20px;border-top:1px solid #edf1f3;color:#718296;font-size:12px}
button{padding:6px 11px;border:1px solid #dbe3ea;border-radius:7px;background:#fff;color:#326b63;cursor:pointer}
button:disabled{opacity:.4;cursor:not-allowed}
</style>
