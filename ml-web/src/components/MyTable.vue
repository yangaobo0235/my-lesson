<script setup>
import {ref} from "vue";
import {ElMessageBox} from "element-plus";
import {myDelete, myDeleteBatch} from "../request";
import {isNotNull, datetimeFormat} from "../util";
import router from "../router";

let parent = defineProps({
  // 模块名称，必填，用于删除和批量删除业务
  module: {type: String, required: true},
  // 表格行数据，必填
  records: {type: Array, required: true},
  /*
   * 表格列数据，必填：
   *
   * label: 列名数组，全类型通用
   * prop: 属性名，全类型通用
   * width: 列宽，全类型通用
   * sortable: 是否支持排序，默认 true，全类型通用
   * tooltip: 是否支持溢出内容的鼠标经过提示，默认 false，全类型通用
   * type: 表格列类型，支持 tag, star, card, img, icon, text 6种，默认 text：
   *     type = tag: 标签
   *         prefix: 前缀文字
   *         suffix: 后缀文字
   *         format: 前置格式化函数
   *         tagType: 标签颜色，默认 "warning"
   *         tagTypeFn: 标签颜色函数
   *     type = card: 卡片
   *         prefix: 前缀文字
   *         suffix: 后缀文字
   *         format: 前置格式化函数
   *     type = img: 图片
   *         minio: MINIO地址前缀函数
   *         imgWidth: 图片宽度，默认 100px
   *         imgHeight: 图片高度，默认 100px
   *     type = icon: 图标
   *         iconType: 图标颜色，默认 "primary"
   *         iconSize: 图标尺寸，默认 "small"
   *     type = text: 文本
   *         prefix: 前缀文字
   *         suffix: 后缀文字
   *         format: 前置格式化函数
   */
  columns: {type: Array, required: true},
  /* 分页信息
   *
   * pageNum: 当前第几页，默认 1
   * pageSize: 每页多少条，默认 5
   * total: 一共多少条
   * callback: 分页回调函数
   */
  pageInfo: {type: Object, required: true},
  /* 表格尾按钮：
   *
   * label: 按钮文案，默认 "普通按钮"
   * type: 按钮颜色，默认 "success"
   * icon: 按钮图标，默认 "InfoFilled"
   * callback: 当点击按钮时触发的回调函数
   */
  buttons: {type: Array, required: false},
  // 当点击添加按钮时跳入的路由组件名称
  insertPage: {type: String, required: false},
  // 跳入添加页面之前，附加的参数，JSON格式，默认存储到 sessionStorage 中
  insertPageParam: {type: Object, required: false},
  // 当点击修改按钮时跳入的路由组件名称
  updatePage: {type: String, required: false},
  // 跳入修改页面之前，附加的参数，JSON格式，默认存储到 sessionStorage 中
  updatePageParam: {type: Object, required: false},
  // 单条删除API方法
  deleteApi: {type: Function, required: false},
  // 打印报表API方法
  excelApi: {type: Function, required: false},
  // 批量删除API方法
  deleteBatchApi: {type: Function, required: false},
  // 单条删除和批量删除结束后的回调方法
  deleteCallback: {type: Function, required: false},
  // 每页多少条可选数量，默认 [1, 5, 10, 18]
  pageSizes: {type: Array, required: false, default: [1, 5, 10, 18]},
});

/* ==================== 处理表格多级标题 ==================== */

// 表格列数据对象
let columnsObj = {};

/*
 * 原格式:
 *
 * [
 *   {label: ['a', 'b'], prop: 'id01'},
 *   {label: ['a', 'c'], prop: 'id02'},
 *   {label: ['b', 'c'], prop: 'id03'},
 * ]
 *
 * 新格式:
 *
 * {
 *   'a': [{label: 'b', prop: 'id01'}, {label: 'c', prop: 'id02'}],
 *   'b': [{label: 'c', prop: 'id03'}],
 * }
 *
 */
for (let column of parent['columns']) {
  // '主键' 或 ['主键'] -> ['基本信息', '主键']
  if (typeof column['label'] === 'string' || column['label'].length === 1) {
    column['label'] = ['基本信息', column['label']]
  }
  // 获取一级标题
  let parentName = column['label'][0];
  column['label'] = column['label'][1];
  // 若 columnsObj 中已经存在该一级标题对应的数组，则取出该数组，并加入该一级标题
  if (columnsObj.hasOwnProperty(parentName)) {
    columnsObj[parentName].push(column);
  }
  // 若 columnsObj 中不经存在该一级标题对应的数组，则创建新数组，再加入该一级标题
  else {
    columnsObj[parentName] = [column];
  }
}

/* ==================== 处理表格数据显示 ==================== */

/** 处理表格数据显示: row['user.username'] -> row['user']['username'] */
function toMustache(row, prop) {
  let propArray = prop.split('.');
  for (let i = 0, j = propArray.length; i < j; i++) {
    // i=0: row = row['user']
    // i=1: row = row['user']['username']
    if (row) row = row[propArray[i]];
  }
  return row;
}

/* ==================== 查看card内容详情 ==================== */

/**
 * 查看card内容详情
 *
 * @param row 当前行数据
 * @param prop 属性
 */
function cardDetail(row, prop) {
  ElMessageBox.alert(row[prop], '记录详情', {
    confirmButtonText: '了解',
    callback: () => {
    },
  });
}

/* ==================== 查看详情 ==================== */

// 菜单详情抽屉 + 菜单详情列表数据
let detailDrawer = ref(), detailItems = ref([]);

/** 查看记录详情 */
function detail(record) {

  // 每次清空，防止追加
  detailItems.value = [];
  // 填充表格数据到详情列表中
  for (let i in parent['columns']) {
    let item = parent['columns'][i];
    item['value'] = toMustache(record, item['prop']);
    detailItems.value.push(item);
  }
  // 填充 创建时间 和 修改时间 信息
  detailItems.value.push({label: '创建时间', value: record['created'], format: datetimeFormat});
  detailItems.value.push({label: '修改时间', value: record['updated'], format: datetimeFormat});
  // 打开抽屉
  detailDrawer.value = true;
}

/* ==================== 单条删除 ==================== */

/**
 * 调用 myDelete() 工具删除该行数据
 *
 * @param row 当前行数据
 */
function remove(row) {
  myDelete({
    id: row['id'],
    api: parent['deleteApi'],
    args: {module: parent['module']},
    callback: parent['deleteCallback']
  });
}

/* ==================== 批量删除 ==================== */

// 批量删除主键数组
let ids = [];

// 当表格首列的多选框被选中或取消选中时触发
function selectionChange(rows) {
  // 记录当前被选中的行
  ids = rows.map(e => e['id']);
}

/** 调用 myDeleteBatch() 工具批量删除数据*/
function removeBatch() {
  myDeleteBatch({
    ids: ids,
    api: parent['deleteBatchApi'],
    args: {module: parent['module']},
    callback: parent['deleteCallback']
  });
}

/* ==================== 单条添加 ==================== */

/** 路由到指定的添加页面 */
function insert() {
  if (isNotNull(parent['insertPage'])) {
    if (isNotNull(parent['insertPageParam'])) {
      sessionStorage.setItem('insertPageParam', JSON.stringify(parent['insertPageParam']));
    }
    router.push(parent['insertPage']);
  } else {
    ElMessageBox.alert('功能还未开发，敬请期待！', '提示', {type: 'warning'});
  }
}

/* ==================== 单条修改 ==================== */

/**
 * 存储当前行数据，并路由到指定的修改页面。
 *
 * @param row 当前行数据
 */
function update(row) {
  if (isNotNull(parent['updatePage'])) {
    if (isNotNull(parent['updatePageParam'])) {
      sessionStorage.setItem('updatePageParam', JSON.stringify(parent['updatePageParam']));
    }
    sessionStorage.setItem('row', JSON.stringify(row));
    router.push(parent['updatePage']);
  } else {
    ElMessageBox.alert('功能还未开发，敬请期待！', '提示', {type: 'warning'});
  }
}

/* ==================== 打印报表 ==================== */

/** 调用对应API，打印报表 */
function excel() {
  if (isNotNull(parent['excelApi'])) {
    parent['excelApi']();
  } else {
    ElMessageBox.alert('功能还未开发，敬请期待！', '提示', {type: 'warning'});
  }
}

/* ==================== 处理表格属性 ==================== */

/** 是否使用 tooltip 属性 */
function useTooltip(item) {
  // 配置优先
  if (isNotNull(item['tooltip'])) return item['tooltip'];
  // 评分，图片，图标和卡片都不使用 tooltip 属性，其余情况使用 tooltip 属性
  return item['type'] !== 'star' &&
      item['type'] !== 'img' &&
      item['type'] !== 'icon' &&
      item['type'] !== 'card';
}

/** 是否使用 sortable 属性 */
function useSortable(item) {
  // 配置优先
  if (isNotNull(item['sortable'])) return item['sortable'];
  // 评分，图片，图标和卡片都不使用 sortable 属性，其余情况使用 sortable 属性
  return item['type'] !== 'star' &&
      item['type'] !== 'img' &&
      item['type'] !== 'icon' &&
      item['card'] !== 'icon';
}

/** 是否使用 width 属性 */
function useWidth(item) {
  // 配置优先
  if (isNotNull(item['width'])) return item['width'];
  // 卡片默认宽度 300px
  if (item['type'] === 'card') return 300;
  // 标签默认宽度 110px
  if (item['type'] === 'tag') return 110;
  // 图片默认宽度 80px
  if (item['type'] === 'img') return 80;
  // 其他默认宽度 200px
  return 200;
}

</script>

<template>
  <el-table class="my-table"
            tooltip-effect="light"
            size="small"
            stripe
            highlight-current-row
            :data="records"
            @selection-change="selectionChange">
    <el-table-column class="head-col"
                     label="序号"
                     align="center"
                     fixed="left">
      <el-table-column type="selection" width="40"/>
      <el-table-column type="index" width="40"/>
    </el-table-column>
    <el-table-column class="body-col"
                     align="center"
                     v-for="(childrenArray, parentLabel) in columnsObj"
                     :key="childrenArray"
                     :label="parentLabel">
      <el-table-column class="body-col-inner"
                       v-for="item in childrenArray"
                       :key="item"
                       :label="item['label']"
                       :property="item['prop']"
                       :sortable="useSortable(item)"
                       :show-overflow-tooltip="useTooltip(item)"
                       :width="useWidth(item)">
        <template #default="scope">
          <el-tag v-if="item['type'] === 'tag' && isNotNull(toMustache(scope.row, item['prop']))"
                  :type="isNotNull(item['tagTypeFn']) ? item['tagTypeFn'](scope.row[item['prop']]) :
                         isNotNull(item['tagType']) ? item['tagType'] : 'warning'">
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](toMustache(scope.row, item['prop'])) }}</span>
            <span v-else>{{ toMustache(scope.row, item['prop']) }}</span>
            <span>{{ item['suffix'] }}</span>
          </el-tag>
          <el-rate v-else-if="item['type'] === 'star' && isNotNull(scope.row[item['prop']])"
                   disabled show-score
                   v-model="scope.row[item['prop']]"
                   text-color="#ff9900"
                   score-template="{value}"/>
          <el-card v-else-if="item['type'] === 'card' && isNotNull(toMustache(scope.row, item['prop']))"
                   @click="cardDetail(scope.row, item['prop'])">
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](toMustache(scope.row, item['prop'])) }}</span>
            <span v-else>{{ toMustache(scope.row, item['prop']) }}</span>
            <span>{{ item['suffix'] }}</span>
          </el-card>
          <el-image v-else-if="item['type'] === 'img' && isNotNull(toMustache(scope.row, item['prop']))"
                    fit="fill"
                    preview-teleported
                    :src="item['minio'](toMustache(scope.row, item['prop']))"
                    :preview-src-list="[item['minio'](toMustache(scope.row, item['prop']))]"
                    :style="{width: item['imgWidth'], height: item['imgHeight']}"/>
          <el-button v-else-if="item['type'] === 'icon' && isNotNull(toMustache(scope.row, item['prop']))"
                     plain
                     :type="isNotNull(item['iconType']) ? item['iconType'] : 'primary'"
                     :size="isNotNull(item['iconSize']) ? item['iconSize'] : 'small'"
                     :icon="toMustache(scope.row, item['prop'])"/>
          <div class="text" v-else>
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](toMustache(scope.row, item['prop'])) }}</span>
            <span v-else>{{ toMustache(scope.row, item['prop']) }}</span>
            <span>{{ item['suffix'] }}</span>
          </div>
        </template>
      </el-table-column>
    </el-table-column>
    <el-table-column class="time-col"
                     label="时间信息"
                     align="center">
      <el-table-column class="created-col"
                       label="记录首次创建时间"
                       property="created"
                       sortable
                       :width="150"
                       :formatter="row => datetimeFormat(row['created'])"/>
      <el-table-column class="updated-col"
                       label="记录最后修改时间"
                       property="updated"
                       sortable
                       :width="150"
                       :formatter="row => datetimeFormat(row['updated'])"/>
    </el-table-column>
    <el-table-column class="foot-col"
                     align="center"
                     fixed="right"
                     :width="170">
      <template #header>
        <el-button class="insert-btn"
                   plain
                   size="small"
                   icon="CirclePlusFilled"
                   type="primary"
                   title="单增一条记录"
                   @click="insert"/>
        <el-button class="delete-batch-btn"
                   plain
                   size="small"
                   icon="DeleteFilled"
                   type="danger"
                   title="批量删除记录"
                   @click="removeBatch"/>
        <el-button class="excel-btn"
                   plain
                   size="small"
                   icon="Grid"
                   type="warning"
                   title="打印数据报表"
                   @click="excel"/>
      </template>
      <template #default="scope">
        <el-row :gutter="0">
          <el-col class="info-btn-col" :span="12">
            <el-button class="info-btn"
                       link
                       size="small"
                       type="info"
                       icon="InfoFilled"
                       @click="detail(scope.row)">
              查看详情
            </el-button>
          </el-col>
          <el-col class="delete-btn-col" :span="12">
            <el-button class="delete-btn"
                       link
                       size="small"
                       type="danger"
                       icon="Delete"
                       @click="remove(scope.row)">
              删除记录
            </el-button>
          </el-col>
          <el-col class="update-btn-col" :span="12">
            <el-button class="update-btn"
                       link
                       size="small"
                       type="warning"
                       icon="Edit"
                       @click="update(scope.row)">
              修改记录
            </el-button>
          </el-col>
          <el-col class="custom-btn-col"
                  :span="12"
                  v-for="button in buttons"
                  :key="button">
            <el-button class="custom-btn"
                       link
                       size="small"
                       :type="isNotNull(button['type']) ? button['type'] : 'success'"
                       :icon="isNotNull(button['icon']) ? button['icon'] : 'InfoFilled'"
                       @click="button['callback'](scope.row)">
              {{ isNotNull(button['label']) ? button['label'] : '普通按钮' }}
            </el-button>
          </el-col>
        </el-row>
      </template>
    </el-table-column>
    <!--用于调控表格宽度，不设置宽度，则宽度自撑开到100%-->
    <el-table-column/>
  </el-table>
  <el-drawer class="detail-drawer"
             v-model="detailDrawer"
             title="记录详情"
             size="30%">
    <el-descriptions class="my-list"
                     border
                     label-align="center"
                     :column="1">
      <el-descriptions-item class="list-item"
                            v-for="item in detailItems"
                            :key="item"
                            :label="item['label']"
                            :span="isNotNull(item['colspan']) ? item['colspan'] : 1">
        <template #default>
          <el-tag v-if="item['type'] === 'tag' && isNotNull(item['value'])"
                  :type="isNotNull(item['tagTypeFn']) ? item['tagTypeFn'](item['value']) :
                       isNotNull(item['tagType']) ? item['tagType'] : 'warning'">
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](item['value']) }}</span>
            <span v-else>{{ item['value'] }}</span>
            <span>{{ item['suffix'] }}</span>
          </el-tag>
          <el-rate v-else-if="item['type'] === 'star' && isNotNull(item['value'])"
                   disabled
                   show-score
                   v-model="item['value']"
                   text-color="#ff9900"
                   score-template="{value}"/>
          <el-card v-else-if="item['type'] === 'card' && isNotNull(item['value'])"
                   :style="{height: item['height'] + 'px'}">
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](item['value']) }}</span>
            <span v-else>{{ item['value'] }}</span>
            <span>{{ item['suffix'] }}</span>
          </el-card>
          <el-image v-else-if="item['type'] === 'img' && isNotNull(item['value'])"
                    fit="fill"
                    preview-teleported
                    :src="item['minio'](item['value'])"
                    :preview-src-list="[item['minio'](item['value'])]"
                    :style="{width: item['imgWidth'] + 'px', height: item['imgHeight'] + 'px'}"/>
          <el-button v-else-if="item['type'] === 'icon' && isNotNull(item['value'])"
                     plain
                     :type="isNotNull(item['iconType']) ? item['iconType'] : 'primary'"
                     :size="isNotNull(item['iconSize']) ? item['iconSize'] : 'small'"
                     :icon="item['value']">
          </el-button>
          <div class="text" v-else>
            <span>{{ item['prefix'] }}</span>
            <span v-if="isNotNull(item['format'])">{{ item['format'](item['value']) }}</span>
            <span v-else>{{ item['value'] }}</span>
            <span>{{ item['suffix'] }}</span>
          </div>
        </template>
      </el-descriptions-item>
    </el-descriptions>
  </el-drawer>
  <el-pagination class="my-pager"
                 layout="total, sizes, prev, pager, next, jumper"
                 :background="true"
                 :page-sizes="pageSizes"
                 :current-page="isNotNull(pageInfo['pageNum']) ? pageInfo['pageNum'] : 1"
                 :page-size="isNotNull(pageInfo['pageSize']) ? pageInfo['pageSize'] : 5"
                 :total="pageInfo['total']"
                 @size-change="pageSize => pageInfo['callback'](pageInfo['pageNum'], pageSize)"
                 @current-change="pageNum => pageInfo['callback'](pageNum, pageInfo['pageSize'])"/>
</template>

<style scoped lang="scss">
.my-table {
  height: 480px; // 高度

  .el-image {
    width: 100%; // 宽度
    height: 50px; // 高度
    margin-bottom: -10px; // 下边距
  }

  .el-card {
    height: 62px; // 高度（大概2行文字）
    --el-card-padding: 10px !important; // 内边距
    white-space: normal !important; // 自动换行
    overflow-y: scroll !important; // 垂直溢出滚动
  }
}

.foot-col-inner {
  text-align: left; // 居左
}

:deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) {
  min-width: 107px !important; // 最小列宽107
  max-width: 137px !important; // 最大列宽137
}

.my-list {
  .el-image {
    width: 100px; // 宽度
    height: 100px; // 高度
  }

  .el-card {
    max-height: 420px; // 最大高度（不超过15行）
    --el-card-padding: 10px !important; // 内边距
    white-space: normal !important; // 自动换行
    overflow-y: scroll !important; // 垂直溢出滚动
  }
}

.my-pager {
  margin: 20px; // 外边距
  justify-content: center; // 自居中
}
</style>
