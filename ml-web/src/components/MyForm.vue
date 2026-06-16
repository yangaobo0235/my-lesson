<script setup>
import {shallowRef} from "vue";
import {myInsert, myUpdate} from "../request";
import {isNotNull} from "../util";

let parent = defineProps({
  // 表单类型：支持 insert 和 update 两种
  type: {type: String, required: false, default: 'insert'},
  /* 表单项
   *
   * label: 文案，默认表单项属性名，全类型通用
   * prop: 属性名，全类型通用
   * required: 是否必填，默认 false，全类型通用
   * span: 每个表单项的宽度占 24 分之多少，默认 24，表示独占一行，全类型通用
   * hidden: 是否隐藏，默认 false，需要占位时改为 true，全类型通用
   * placeholder: 背景字，默认 "请输入.."，icon 和 cascader 类型除外
   * disabled: 是否禁用，默认 false，icon 和 cascader 类型除外
   * readonly: 是否只读，默认 false，icon 和 cascader 类型除外
   * type: 表单项类型，支持 number, password, textarea, select, datetime, date, icon, cascader, text 9种，默认 text：
   *     type = number: 数字框
   *         min: 最小值，默认 0
   *         max: 最大值
   *         precision: 精度，默认 0
   *     type = textarea: 文本域
   *         rows: 行数，默认 8
   *         cols: 列数
   *     type = select: 下拉框
   *         options: 下拉菜单项，格式 [{ label: '', value: '' }]
   *         multiple: 是否支持多选，默认 false
   *     type = icon: ICON单选框
   *         iconSize: 按钮大小，默认 14
   *     type = cascader: 级联框
   *         options: 级联菜单项，格式 [{ label: '', value: '', children: [] }]
   *         cascaderChange: 当级联菜单发生改变时触发的函数（不带小括号）
   */
  items: {type: Array, required: true},
  // 表单规则
  rules: {type: Object, required: true},
  // API函数：表单提交时调用的API函数（不带小括号）
  api: {type: Function, required: true},
  // API参数：API请求函数参数，作为请求参数
  params: {type: Object, required: true},
  // API参数：API请求函数参数，不作为请求参数，而是有一些其它作用
  args: {type: Object, required: false, default: null},
  // 回调函数：表单提交后调用的函数（不带小括号）
  callback: {type: Function, required: false},
  // 表单宽度
  width: {type: String, required: false, default: '100%'},
});

// 表单对象
let form = shallowRef();

// 添加数据: 发送请求 + 重置表单
async function insert() {
  await myInsert({
    form: form,
    api: parent['api'],
    params: parent['params'],
    args: parent['args'],
    callback: parent['callback'],
  });
  form.value.resetFields;
}

// 修改数据: 发送请求 + 重置表单
async function update() {
  await myUpdate({
    form: form,
    api: parent['api'],
    params: parent['params'],
    args: parent['args'],
    callback: parent['callback'],
  });
  form.value.resetFields;
}

const ICONS = [
  'Plus', 'Minus', 'CirclePlus', 'Search', 'Female', 'Male', 'Aim', 'House', 'FullScreen', 'Loading',
  'Link', 'Service', 'Pointer', 'Star', 'Notification', 'Connection', 'ChatDotRound', 'Setting',
  'Clock', 'Position', 'Discount', 'Odometer', 'ChatSquare', 'ChatRound', 'ChatLineRound', 'ChatLineSquare',
  'ChatDotSquare', 'View', 'Hide', 'Unlock', 'Lock', 'RefreshRight', 'RefreshLeft', 'Refresh', 'Bell',
  'MuteNotification', 'User', 'Check', 'CircleCheck', 'Warning', 'CircleClose', 'Close', 'PieChart', 'More',
  'Compass', 'Filter', 'Switch', 'Select', 'SemiSelect', 'CloseBold', 'EditPen', 'Edit', 'Message',
  'MessageBox', 'TurnOff', 'Finished', 'Delete', 'Crop', 'SwitchButton', 'Operation', 'Open', 'Remove',
  'ZoomOut', 'ZoomIn', 'InfoFilled', 'CircleCheckFilled', 'SuccessFilled', 'WarningFilled', 'CircleCloseFilled',
  'QuestionFilled', 'WarnTriangleFilled', 'UserFilled', 'MoreFilled', 'Tools', 'HomeFilled', 'Menu',
  'UploadFilled', 'Avatar', 'HelpFilled', 'Share', 'StarFilled', 'Comment', 'Histogram', 'Grid', 'Promotion',
  'DeleteFilled', 'RemoveFilled', 'CirclePlusFilled', 'ArrowLeft', 'ArrowUp', 'ArrowRight', 'ArrowDown',
  'ArrowLeftBold', 'ArrowUpBold', 'ArrowRightBold', 'ArrowDownBold', 'DArrowRight', 'DArrowLeft', 'Download',
  'Upload', 'Top', 'Bottom', 'Back', 'Right', 'TopRight', 'TopLeft', 'BottomRight', 'BottomLeft', 'Sort',
  'SortUp', 'SortDown', 'Rank', 'CaretLeft', 'CaretTop', 'CaretRight', 'CaretBottom', 'DCaret', 'Expand',
  'Fold', 'DocumentAdd', 'Document', 'Notebook', 'Tickets', 'Memo', 'Collection', 'Postcard', 'ScaleToOriginal',
  'SetUp', 'DocumentDelete', 'DocumentChecked', 'DataBoard', 'DataAnalysis', 'CopyDocument', 'FolderChecked',
  'Files', 'Folder', 'FolderDelete', 'FolderRemove', 'FolderOpened', 'DocumentCopy', 'DocumentRemove',
  'FolderAdd', 'FirstAidKit', 'Reading', 'DataLine', 'Management', 'Checked', 'Ticket', 'Failed', 'TrendCharts',
  'List', 'Microphone', 'Mute', 'Mic', 'VideoPause', 'VideoCamera', 'VideoPlay', 'Headset', 'Monitor', 'Film',
  'Camera', 'Picture', 'PictureRounded', 'Iphone', 'Cellphone', 'VideoCameraFilled', 'PictureFilled',
  'Platform', 'CameraFilled', 'BellFilled', 'Location', 'LocationInformation', 'DeleteLocation', 'Coordinate',
  'Bicycle', 'OfficeBuilding', 'School', 'Guide', 'AddLocation', 'MapLocation', 'Place', 'LocationFilled',
  'Van', 'Watermelon', 'Pear', 'NoSmoking', 'Smoking', 'Mug', 'GobletSquareFull', 'GobletFull', 'KnifeFork',
  'Sugar', 'Bowl', 'MilkTea', 'Lollipop', 'Coffee', 'Chicken', 'Dish', 'IceTea', 'ColdDrink', 'CoffeeCup',
  'DishDot', 'IceDrink', 'IceCream', 'Dessert', 'IceCreamSquare', 'ForkSpoon', 'IceCreamRound', 'Food',
  'HotWater', 'Grape', 'Fries', 'Apple', 'Burger', 'Goblet', 'GobletSquare', 'Orange', 'Cherry', 'Printer',
  'Calendar', 'CreditCard', 'Box', 'Money', 'Refrigerator', 'Cpu', 'Football', 'Brush', 'Suitcase',
  'SuitcaseLine', 'Umbrella', 'AlarmClock', 'Medal', 'GoldMedal', 'Present', 'Mouse', 'Watch', 'QuartzWatch',
  'Magnet', 'Help', 'Soccer', 'ToiletPaper', 'ReadingLamp', 'Paperclip', 'MagicStick', 'Basketball',
  'Baseball', 'Coin', 'Goods', 'Sell', 'SoldOut', 'Key', 'ShoppingCart', 'ShoppingCartFull', 'ShoppingTrolley',
  'Phone', 'Scissor', 'Handbag', 'ShoppingBag', 'Trophy', 'TrophyBase', 'Stopwatch', 'Timer', 'CollectionTag',
  'TakeawayBox', 'PriceTag', 'Wallet', 'Opportunity', 'PhoneFilled', 'WalletFilled', 'GoodsFilled', 'Flag',
  'BrushFilled', 'Briefcase', 'Stamp', 'Sunrise', 'Sunny', 'Ship', 'MostlyCloudy', 'PartlyCloudy', 'Sunset',
  'Drizzling', 'Pouring', 'Cloudy', 'Moon', 'MoonNight', 'Lightning', 'ChromeFilled', 'Eleme', 'ElemeFilled',
  'ElementPlus', 'Shop', 'SwitchFilled', 'WindPower'
];
</script>

<template>
  <el-form class="my-form"
           ref="form"
           status-icon
           label-width="auto"
           size="small"
           :model="params"
           :rules="rules"
           :style="{'width': width}">
    <el-row :gutter="20">
      <el-col class="layout-col"
              v-for="item in items"
              :key="item"
              :span="isNotNull(item['span']) ? item['span'] : 24">
        <el-form-item class="form-item"
                      :label="isNotNull(item['label']) ? item['label'] : item['prop']"
                      :prop="item['prop']"
                      :required="item['required']"
                      v-show="!item['hidden']">
          <!--数字框-->
          <el-input-number v-if="item['type'] === 'number'"
                           clearable
                           v-model="params[item['prop']]"
                           :min="isNotNull(item['min']) ? item['min'] : 0"
                           :max="item['max']"
                           :precision="isNotNull(item['precision']) ? item['precision'] : 0"
                           :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                           :disabled="item['disabled']"
                           :readonly="item['readonly']"/>
          <!--密码框-->
          <el-input v-else-if="item['type'] === 'password'"
                    clearable
                    show-password
                    v-model="params[item['prop']]"
                    :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                    :disabled="item['disabled']"
                    :readonly="item['readonly']"/>
          <!--文本域-->
          <el-input v-else-if="item['type'] === 'textarea'"
                    type="textarea"
                    clearable
                    resize="none"
                    v-model="params[item['prop']]"
                    :rows="isNotNull(item['rows']) ? item['rows'] : 8"
                    :cols="item['cols']"
                    :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                    :disabled="item['disabled']"
                    :readonly="item['readonly']"/>
          <!--下拉框-->
          <el-select v-else-if="item['type'] === 'select'"
                     clearable
                     filterable
                     :multiple="item['multiple']"
                     v-model="params[item['prop']]"
                     :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                     :disabled="item['disabled']"
                     :readonly="item['readonly']">
            <el-option class="select-item"
                       v-for="option in item['options']"
                       :key="option"
                       :label="option['label']"
                       :value="option['value']"/>
          </el-select>
          <!--时间框-->
          <el-date-picker v-else-if="item['type'] === 'datetime'"
                          type="datetime"
                          clearable
                          v-model="params[item['prop']]"
                          format="YYYY-MM-DD HH:mm"
                          value-format="YYYY-MM-DDTHH:mm:ss"
                          :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                          :disabled="item['disabled']"
                          :readonly="item['readonly']"/>
          <!--日期框-->
          <el-date-picker v-else-if="item['type'] === 'date'"
                          type="date"
                          clearable
                          v-model="params[item['prop']]"
                          format="YYYY-MM-DD"
                          value-format="YYYY-MM-DD"
                          :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                          :disabled="item['disabled']"
                          :readonly="item['readonly']"/>
          <!--ICON单选框-->
          <el-radio-group v-else-if="item['type'] === 'icon'"
                          class="icon-list"
                          size="small"
                          v-model="params[item['prop']]">
            <el-radio-button class="icon-item"
                             v-for="icon in ICONS"
                             :key="icon"
                             :label="icon">
              <template #default>
                <el-icon :size="isNotNull(item['iconSize']) ? item['iconSize'] : 14">
                  <component :is="icon"/>
                </el-icon>
              </template>
            </el-radio-button>
          </el-radio-group>
          <!--级联框-->
          <el-cascader v-else-if="item['type'] === 'cascader'"
                       v-model="params[item['prop']]"
                       :options="item['options']"
                       :props="{expandTrigger: 'hover'}"
                       @change="params[item['cascaderChange']]"
                       style="width: 100%;"/>
          <!--文本框-->
          <el-input v-else
                    clearable
                    v-model="params[item['prop']]"
                    :placeholder="isNotNull(item['placeholder']) ? item['placeholder'] : '请输入..'"
                    :disabled="item['disabled']"
                    :readonly="item['readonly']"/>
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item class="btn-item">
      <el-button class="insert-btn"
                 v-if="type === 'insert'"
                 type="primary"
                 @click="insert">
        确认添加
      </el-button>
      <el-button class="update-btn"
                 v-else-if="type === 'update'"
                 type="primary"
                 @click="update">
        确认修改
      </el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.my-form {
  .el-input-number, .el-button {
    width: 100%; // 宽度
  }
}

:deep(.el-date-editor) {
  --el-date-editor-width: 100%; // 宽度
}

.icon-list {
  margin-left: -2px; // 左外边距
  max-height: 160px; // 最大高度
  overflow-y: scroll; // Y滚动

  .el-radio-button {
    margin-bottom: 5px; // 下外边距
  }
}

/* 取消首尾按钮的圆角 */
:deep(.el-radio-button:first-child .el-radio-button__inner),
:deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0; // 圆角
}

/* 修改单选按钮样式 */
:deep(.el-radio-button--small .el-radio-button__inner) {
  border-left: 1px solid #7e7e7e; // 左边框
  margin: 0 2px; // 左右外边距
}
</style>
