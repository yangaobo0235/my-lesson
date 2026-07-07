<script setup>
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, ElMessageBox} from 'element-plus';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {orderStateFormat} from '../../util/index.js';
import {readJsonStorage} from '../../util/auth.js';
import {createQrCode, startPaymentPolling} from './pay.js';
import './student.scss';

const router = useRouter();
const loginUser = readJsonStorage('loginUser', {});
const records = ref([]);
const pageInfo = reactive({pageNum: 1, pageSize: 10, total: 0});
const payDialog = ref(false);
const qrCode = ref('');
const paySn = ref('');
let payTimer = null;

async function page(pageNum = pageInfo.pageNum) {
  const data = getResponseData(await studentApi.orders({pageNum, pageSize: pageInfo.pageSize, fkUserId: loginUser.id})) || {};
  records.value = data.records || [];
  pageInfo.pageNum = data.pageNumber || data.pageNum || pageNum;
  pageInfo.total = data.totalRow || data.total || 0;
}

async function deleteOrder(id) {
  await ElMessageBox.confirm('确定删除该订单吗？');
  if (getResponseData(await studentApi.deleteOrder(id))) {
    ElMessage.success('订单已删除');
    await page();
  }
}

async function payOrder(order) {
  try {
    paySn.value = order.sn;
    qrCode.value = await createQrCode(order.sn);
    payDialog.value = true;
    payTimer = startPaymentPolling(order.sn, async () => {
      clearInterval(payTimer);
      payDialog.value = false;
      await page();
    });
  } catch (error) {
    ElMessage.error(error.message || '获取支付二维码失败');
  }
}

async function cancelPay() {
  clearInterval(payTimer);
  if (paySn.value) await studentApi.cancelOrder(paySn.value);
  payDialog.value = false;
  await page();
}

onMounted(() => page(1));
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div><h2>我的订单</h2><p>查看购买记录，未支付订单可继续支付，已支付课程可进入学习</p></div>
    </header>

    <section class="panel">
      <el-table :data="records">
        <el-table-column prop="sn" label="订单编号" min-width="190"/>
        <el-table-column prop="totalAmount" label="总金额" width="110"/>
        <el-table-column prop="payAmount" label="实付" width="110"/>
        <el-table-column label="状态" width="110">
          <template #default="{row}"><el-tag>{{ orderStateFormat(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="课程" min-width="240">
          <template #default="{row}">
            <div class="detail-list">
              <span v-for="detail in row.orderDetails" :key="detail.id">{{ detail.courseTitle }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status === 0" size="small" type="primary" @click="payOrder(row)">去支付</el-button>
            <el-button v-if="row.status === 1" size="small" type="success" @click="router.push('/student/learning')">去学习</el-button>
            <el-button size="small" text type="danger" @click="deleteOrder(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :current-page="pageInfo.pageNum" :page-size="pageInfo.pageSize"
                     :total="pageInfo.total" @current-change="page"/>
    </section>

    <el-dialog v-model="payDialog" title="扫码支付" width="360px" @close="cancelPay">
      <div class="qr-box"><img :src="qrCode" alt="支付二维码"><span>订单号：{{ paySn }}</span></div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.detail-list { display:flex; flex-wrap:wrap; gap:6px; }
.detail-list span { padding:4px 7px; border-radius:6px; background:var(--ml-surface-soft); color:var(--ml-text); }
.qr-box { display:grid; justify-items:center; gap:10px; }
.qr-box img { width:220px; height:220px; }
</style>
