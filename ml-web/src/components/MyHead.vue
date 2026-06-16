<script setup>
import {ref} from "vue";
import {isNotNull} from "../util";

let parent = defineProps({
  /* 页头项
   *
   * span: 列占位，默认 1，全类型通用
   * offset: 列偏移，默认 0，全类型通用
   * type: 表头项类型，支持 ipt, opt, rdo, btn 4种，默认 btn：
   *     type = ipt: 单行文本框
   *         placeholder: 单行文本框背景字，默认 "请输入.."
   *         iptValue: 单行文本框默认值
   *         callback：点击时的回调函数，回传当前控件的 iptValue 值
   *     type = opt: 下拉菜单
   *         placeholder: 单行文本框背景字，默认 "请选择.."
   *         optValue: 下拉菜单默认值
   *         filterable: 下拉菜单是否开启过滤，默认 true
   *         options: 下拉菜单或单选按钮项，格式  [{ label: '', value: '' }]
   *         callback：选择时的回调函数，回传当前控件的 optValue 值
   *     type = rdo: 单选按钮
   *         options: 下拉菜单或单选按钮项，格式  [{ label: '', value: '' }]
   *         rdoValue: 单选按钮默认值，（type = rdo专属）
   *         callback：选择时的回调函数，回传当前控件的 rdoValue 值
   *     type = btn: 普通按钮
   *         btnTitle: 鼠标经过按钮时的文字提示，默认 "普通按钮"
   *         btnType: 按钮类型，默认 "Info"
   *         btnIcon: 按钮图标，默认 "Apple"
   */
  items: {type: Array, required: true}
});

// 存放父组件传递的全部文本框的值 + 全部单选按钮的值 + 全部下拉菜单的值
let iptValues = ref({}), rdoValues = ref({}), optValues = ref({});

// 分类存储 iptValues, rdoValues 和 optValues 的值
for (let item of parent['items']) {
  // 使用当前 item 的字符串形式作为 key 值，以保证唯一性
  let key = JSON.stringify(item);
  // 将全部单行文本的值存入 iptValues 对象
  if (item['iptValue'] != null) iptValues.value[key] = item['iptValue'];
  // 将全部单选按钮的值存入 rdoValues 对象
  if (item['rdoValue'] != null) rdoValues.value[key] = item['rdoValue'];
  // 将全部下拉菜单的值存入 optValues 对象
  if (item['optValue'] != null) optValues.value[key] = item['optValue'];
}
</script>

<template>
  <el-row class="my-head">
    <el-col v-for="item in items"
            :key="item"
            :span="isNotNull(item['span']) ? item['span'] : 1"
            :offset="isNotNull(item['offset']) ? item['offset'] : 0">

      <!--ipt文本框-->
      <el-input class="ipt"
                v-if="item['type'] === 'ipt'"
                size="small"
                clearable
                v-model="iptValues[JSON.stringify(item)]"
                :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                @keyup.enter="item['callback'](iptValues[JSON.stringify(item)])"
                @clear="item['callback'](iptValues[JSON.stringify(item)])">
        <template #append>
          <el-button class="ipt-btn"
                     size="small"
                     title="根据输入的条件进行模糊搜索"
                     :icon="'Search'"
                     @click="item['callback'](iptValues[JSON.stringify(item)])"/>
        </template>
      </el-input>

      <!--opt下拉菜单-->
      <el-select class="opt"
                 v-else-if="item['type'] === 'opt'" size="small"
                 clearable
                 v-model="optValues[JSON.stringify(item)]"
                 :filterable="isNotNull(item['filterable']) ? item['filterable'] : true"
                 :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请选择..'"
                 @change="item['callback'](optValues[JSON.stringify(item)])">
        <el-option class="opt-item"
                   v-for="option in item['options']"
                   :key="option"
                   :label="option['label']"
                   :value="option['value']"/>
      </el-select>

      <!--rdo单选按钮-->
      <el-radio-group class="rdo"
                      v-else-if="item['type'] === 'rdo'"
                      size="small"
                      v-model="rdoValues[JSON.stringify(item)]"
                      @change="item['callback'](rdoValues[JSON.stringify(item)])">
        <el-radio-button class="rdo-item"
                         v-for="option in item['options']"
                         :key="option"
                         :label="option['value']">
          <span>{{ option['label'] }}</span>
        </el-radio-button>
      </el-radio-group>

      <!--普通按钮-->
      <el-button class="btn"
                 v-else
                 plain
                 size="small"
                 :title="isNotNull(item['btnTitle']) ? item['btnTitle'] : '普通按钮'"
                 :icon="isNotNull(item['btnIcon']) ? item['btnIcon'] : 'Apple'"
                 :type="isNotNull(item['btnType']) ? item['btnType'] : 'info'"
                 @click="item['callback']"/>
    </el-col>
  </el-row>
</template>

<style scoped lang="scss">
:deep(.el-input__inner) {
  padding-left: 5px; // 左内边距
  box-sizing: border-box; // 忽略内边距影响
}

:deep(.el-select__selection) {
  padding-left: 5px; // 左内边距
  box-sizing: border-box; // 忽略内边距影响
}

.my-head {
  display: flex; // flex布局
  align-items: center; // 垂直居中
  margin: 30px auto 10px; // 外边距

  .ipt, .opt {
    width: 95%; // 宽度
  }

  .rdo, .btn {
    float: right; // 右浮动
  }
}
</style>
