<script setup>
import {computed, onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, ElMessageBox} from 'element-plus';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_COURSE_COVER} from '../../const/index.js';
import {readJsonStorage} from '../../util/auth.js';
import {createQrCode, startPaymentPolling} from './pay.js';
import './student.scss';

const router = useRouter();
const loginUser = readJsonStorage('loginUser', {});
const records = ref([]);
const selectedCourseIds = ref([]);
const couponCode = ref('');
const coupon = ref(null);
const pageInfo = reactive({pageNum: 1, pageSize: 20, total: 0});
const payDialog = ref(false);
const qrCode = ref('');
const paySn = ref('');
let payTimer = null;

const selectedItems = computed(() => records.value.filter(item => selectedCourseIds.value.includes(item.fkCourseId)));
const totalAmount = computed(() => selectedItems.value.reduce((sum, item) => sum + Number(item.coursePrice || 0), 0));
const payAmount = computed(() => Math.max(totalAmount.value - Number(coupon.value?.cpPrice || 0), 0));

async function page() {
  const data = getResponseData(await studentApi.carts({pageNum: pageInfo.pageNum, pageSize: pageInfo.pageSize, fkUserId: loginUser.id})) || {};
  records.value = data.records || [];
  pageInfo.total = data.totalRow || data.total || 0;
}

async function remove(id) {
  await ElMessageBox.confirm('确定移出该课程吗？');
  if (getResponseData(await studentApi.removeCart(id))) {
    ElMessage.success('已移出购物车');
    await page();
  }
}

async function clearCart() {
  await ElMessageBox.confirm('确定清空购物车吗？');
  if (getResponseData(await studentApi.clearCart(loginUser.id))) {
    selectedCourseIds.value = [];
    coupon.value = null;
    ElMessage.success('购物车已清空');
    await page();
  }
}

async function useCoupon() {
  const code = couponCode.value.trim();
  if (!code) return;
  const result = getResponseData(await studentApi.couponByCode(code));
  if (!result) return;
  coupon.value = result;
  couponCode.value = '';
  ElMessage.success('优惠券已生效');
}

async function pay() {
  if (!selectedItems.value.length) {
    ElMessage.warning('至少选择一门课程');
    return;
  }
  try {
    paySn.value = getResponseData(await studentApi.prePay({
      fkUserId: loginUser.id,
      courseIds: selectedCourseIds.value,
      totalAmount: totalAmount.value,
      payAmount: payAmount.value,
      fkCouponsId: coupon.value?.id || null
    }));
    if (!paySn.value) return;
    qrCode.value = await createQrCode(paySn.value);
    payDialog.value = true;
    payTimer = startPaymentPolling(paySn.value, () => {
      clearInterval(payTimer);
      payDialog.value = false;
      router.push('/student/orders');
    });
  } catch (error) {
    ElMessage.error(error.message || '创建支付订单失败');
  }
}

async function cancelPay() {
  clearInterval(payTimer);
  if (paySn.value) await studentApi.cancelOrder(paySn.value);
  payDialog.value = false;
}

onMounted(page);
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div><h2>购物车</h2><p>选择课程、使用优惠券并生成支付订单</p></div>
      <el-button type="danger" plain icon="Delete" @click="clearCart">清空购物车</el-button>
    </header>

    <div v-if="records.length" class="cart-layout">
      <section class="panel">
        <el-checkbox-group v-model="selectedCourseIds" class="cart-list">
          <article v-for="item in records" :key="item.id" class="cart-item">
            <el-checkbox :label="item.fkCourseId"/>
            <img :src="MINIO_COURSE_COVER(item.courseCover)" :alt="item.courseTitle">
            <div class="cart-info">
              <h3>{{ item.courseTitle }}</h3>
              <span>{{ item.username }}</span>
            </div>
            <strong class="price">￥{{ item.coursePrice }}</strong>
            <el-button text type="danger" @click="remove(item.id)">移出</el-button>
          </article>
        </el-checkbox-group>
      </section>

      <aside class="panel checkout">
        <h3>结算</h3>
        <el-input v-model="couponCode" clearable placeholder="输入优惠券口令" @keyup.enter="useCoupon">
          <template #append><el-button @click="useCoupon">兑换</el-button></template>
        </el-input>
        <div v-if="coupon" class="coupon-box">已使用：{{ coupon.title || coupon.code }}，优惠 ￥{{ coupon.cpPrice }}</div>
        <div class="amount-row"><span>课程数</span><strong>{{ selectedItems.length }}</strong></div>
        <div class="amount-row"><span>总金额</span><strong>￥{{ totalAmount }}</strong></div>
        <div class="amount-row"><span>应付</span><strong class="price">￥{{ payAmount }}</strong></div>
        <el-button type="primary" size="large" @click="pay">去支付</el-button>
      </aside>
    </div>

    <el-empty v-else class="empty-panel" description="购物车为空">
      <el-button type="primary" @click="router.push('/student/courses')">去选课</el-button>
    </el-empty>

    <el-dialog v-model="payDialog" title="扫码支付" width="360px" @close="cancelPay">
      <div class="qr-box"><img :src="qrCode" alt="支付二维码"><span>订单号：{{ paySn }}</span></div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.cart-layout { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:16px; }
.cart-list { display:grid; gap:12px; }
.cart-item { display:grid; grid-template-columns:auto 120px minmax(0,1fr) auto auto; align-items:center; gap:12px; padding-bottom:12px; border-bottom:1px solid var(--ml-border); }
.cart-item:last-child { border-bottom:0; padding-bottom:0; }
.cart-item img { width:120px; aspect-ratio:16/9; object-fit:cover; border-radius:6px; }
.cart-info h3 { margin:0 0 6px; color:var(--ml-text); font-size:15px; }
.cart-info span { color:var(--ml-muted); }
.checkout { display:grid; gap:14px; align-content:start; }
.checkout h3 { margin:0; color:var(--ml-text); }
.coupon-box { padding:10px; border-radius:6px; background:var(--ml-surface-soft); color:var(--ml-muted); }
.amount-row { display:flex; justify-content:space-between; align-items:center; color:var(--ml-muted); }
.qr-box { display:grid; justify-items:center; gap:10px; }
.qr-box img { width:220px; height:220px; }
@media (max-width:1000px) { .cart-layout { grid-template-columns:1fr; } .cart-item { grid-template-columns:auto 90px 1fr; } .cart-item .price,.cart-item .el-button { grid-column:3; } }
</style>
