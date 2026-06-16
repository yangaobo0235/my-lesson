<script setup>
import {onMounted, ref} from "vue";
import {genderFormat, orderPayTypeFormat} from "../util/index.js";
import {bar, pie} from "../echarts/index.js";
import {getResponseData} from "../request/index.js";
import {statisticsApi as userStatisticsApi} from "../api/ums/user.js";
import {statisticsApi as orderStatisticsApi} from "../api/oms/order.js";
import {isNotNull} from "../util/index.js";

// 今日用户 + 今年用户 + 用户日增 + 用户年增
let todayUserCount = ref(0), thisYearUserCount = ref(0);
let userDayIncrease = ref(0), userYearIncrease = ref(0);
// 今日订单 + 今年订单 + 订单日增 + 订单年增
let todayOrderCount = ref(0), thisYearOrderCount = ref(0);
let orderDayIncrease = ref(0), orderYearIncrease = ref(0);
// 统计组件数据列表
let statisticCards = ref([
  {value: todayUserCount, title: '今日用户总数（人）', increaseLabel: '对比昨日', increase: userDayIncrease},
  {value: thisYearUserCount, title: '今年用户总数（人）', increaseLabel: '对比去年', increase: userYearIncrease},
  {value: todayOrderCount, title: '今日订单总数（单）', increaseLabel: '对比昨日', increase: orderDayIncrease},
  {value: thisYearOrderCount, title: '今年订单总数（单）', increaseLabel: '对比去年', increase: orderYearIncrease}
]);

/**
 * 获取用户统计组件数据
 *
 * 1. 获取今日用户，今年用户，用户日增，用户年增和用户性别比例数据
 * 2. 处理饼图数据
 * 3. 绘制饼图
 */
async function selectUserStatistics() {
  // 获取用户统计组件数据，包括今日用户，今年用户，用户日增，用户年增和用户性别比例数据
  let userStatisticsData = getResponseData(await userStatisticsApi());
  if (isNotNull(userStatisticsData)) {
    todayUserCount.value = userStatisticsData['todayCount'];
    thisYearUserCount.value = userStatisticsData['thisYearCount'];
    userDayIncrease.value = userStatisticsData['dayIncrease'];
    userYearIncrease.value = userStatisticsData['yearIncrease'];
  }
  // 处理饼图数据
  let pieData = userStatisticsData['genderCount'];
  for (let i in pieData) {
    pieData[i]['name'] = genderFormat(pieData[i]['name']);
  }
  // 绘制饼图
  pie({
    dom: document.querySelector('#genderBoard'),
    data: pieData,
    name: '性别 - 用户数'
  });
}

/**
 * 获取订单统计组件数据
 *
 * 1. 获取今日订单，今年订单，订单日增，订单年增和支付方式统计数据
 * 2. 准备柱图数据
 * 3. 绘制柱图
 */
async function selectOrderStatistics() {
  // 获取订单统计组件数据，包括今日订单，今年订单，订单日增，订单年增和支付方式统计数据
  let orderStatisticsData = getResponseData(await orderStatisticsApi());
  if (isNotNull(orderStatisticsData)) {
    todayOrderCount.value = orderStatisticsData['todayCount'];
    thisYearOrderCount.value = orderStatisticsData['thisYearCount'];
    orderDayIncrease.value = orderStatisticsData['dayIncrease'];
    orderYearIncrease.value = orderStatisticsData['yearIncrease'];
  }
  // 准备柱图数据
  let barData = orderStatisticsData['payTypeCount'];
  let xData = [], yData = [];
  for (let i in barData) {
    xData.push(orderPayTypeFormat(barData[i]['name']));
    yData.push(barData[i]['value']);
  }
  // 绘制柱图
  bar({
    dom: document.querySelector('#payTypeBoard'),
    xData: xData,
    yData: yData,
    name: '支付方式 - 订单数',
    xName: '方式',
    yName: '订单数',
  });
}

/**
 * 加载函数
 *
 * 1. 获取用户统计数据
 * 2. 获取订单统计数据
 */
onMounted(() => {
  selectUserStatistics();
  selectOrderStatistics();
});
</script>

<template>
  <section class="board-head">
    <el-row :gutter="16">
      <el-col :span="6" v-for="statisticCard in statisticCards">
        <div class="statistic-card">
          <el-statistic class="statistic-body" :value="statisticCard['value']">
            <template #title>
              <el-link icon="InfoFilled" :underline="false">
                &nbsp;{{ statisticCard['title'] }}
              </el-link>
            </template>
          </el-statistic>
          <div class="statistic-footer">
            <div class="footer-item">
              {{ statisticCard['increaseLabel'] }}
              <span :class="statisticCard['increase'] > 0 ? 'green': statisticCard['increase'] < 0 ? 'red' : ''">
                {{ statisticCard['increase'] === '0' ? '持平' : statisticCard['increase'] }}
                <el-icon>
                  <component :is="statisticCard['increase'] > 0 ? 'CaretTop': statisticCard['increase'] < 0 ? 'CaretBottom' : ''"/>
                </el-icon>
              </span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </section>
  <section class="board-body">
    <el-row :gutter="16">
      <el-col class="chart-board" :span="12">
        <el-divider>
          <el-button link icon="PieChart">性别 - 用户数 · 图例</el-button>
        </el-divider>
        <div id="genderBoard" class="board"/>
      </el-col>
      <el-col class="chart-board" :span="12">
        <el-divider>
          <el-button link icon="Histogram">支付方式 - 订单数 · 图例</el-button>
        </el-divider>
        <div id="payTypeBoard" class="board"/>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped lang="scss">
.board-head {
  margin-bottom: 40px; // 下外边距

  .el-statistic {
    --el-statistic-content-font-size: 30px; // value字号

    .el-icon {
      margin-left: 4px; // 左外边距
    }
  }

  .statistic-card {
    height: 100%; // 高度
    border-radius: 4px; // 圆角
    border: 1px solid #1D1E1F; // 边框
    background-color: var(--el-bg-color-overlay); // 背景色
    padding: 20px 20px 0; // 上内边距，左右内边距，下内边距
    text-align: center; // 内容居中
  }

  .statistic-footer {
    display: flex; // flex布局
    justify-content: center; // 左右居中
    flex-wrap: wrap; // flex环绕
    font-size: 12px; // 字号
    color: var(--el-text-color-regular); // 前景色
    margin-top: 16px; // 上外边距
  }

  .statistic-footer .footer-item span:last-child {
    display: inline-flex; // flex布局
    align-items: center; // 居中
    margin-left: 4px; // 左外边距
  }

  .green {
    color: var(--el-color-success); // 绿色
  }

  .red {
    color: var(--el-color-error); // 红色
  }
}

.board-body {
  height: 500px; // 高度

  .board {
    width: 100%; // 宽度
    height: 447px; // 高度
    border: 1px solid #5470C6; // 边框
  }

  .foot-divider-tip {
    padding-bottom: 50px; // 下外边距
  }
}
</style>
