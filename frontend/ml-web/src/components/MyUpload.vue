<script setup>
import {shallowRef} from "vue";
import {ElMessage} from "element-plus";
import {getResponseData} from "../request";
import {hasNull, isNotNull} from "../util";

// 上传控件对象
let uploader = shallowRef();

// 当前token值
let currentToken = sessionStorage.getItem('token');

let parent = defineProps({
  // 控件名称：对应后台API接口中的文件参数名，如 avatarFile 等
  name: {type: String, required: true},
  // 上传地址：对应后台API接口地址
  url: {type: String, required: true},
  // 回调函数：上传成功后的回调函数
  callback: {type: Function, required: false},
  // 单次文件上传的最大数量限制，默认 1
  limit: {type: Number, required: false, default: 1},
  // 是否自动上传，默认 false
  autoUpload: {type: Boolean, required: false, default: false},
  // 上传提交按钮主题颜色，默认 "warning"
  btnType: {type: String, required: false, default: 'warning'},
  // 支持上传的MIME格式数组 默认 ['image/jpeg', 'image/png']
  allowTypes: {type: Array, required: false, default: ['image/jpeg', 'image/png']},
  // 支持上传的文件最大限制，单位MB，默认10M
  maxSize: {type: Number, required: false, default: 10},
});

/**
 * 文件上传前的校验过程
 *
 * @param file 文件对象，必传
 * @return true 校验成功，false 校验失败
 */
function beforeUpload(file) {
  let allowTypes = parent['allowTypes'];
  let maxSize = parent['maxSize'];
  // 空值保护
  if(hasNull(file, allowTypes)) return false;
  // 校验文件MIME类型
  if (!allowTypes.includes(file.type)) {
    ElMessage.warning(`仅支持 ${allowTypes.toString()} 格式！`);
    return false;
  }
  // 校验文件大小
  if (file.size / 1024 / 1024 > maxSize) {
    ElMessage.error(`文件大小不能超过 ${maxSize} MB`)
    return false;
  }
  return true;
}

/**
 * 上传文件成功后执行的函数
 *
 * @param res 响应对象
 */
async function onSuccess(res) {
  let data = getResponseData(res);
  if (isNotNull(data)) {
    // 清空上传控件的文件列表
    uploader.value.clearFiles();
    // 存在回调函数时，异步调用回调函数，并将响应数据作为参数传入
    if (parent['callback']) await parent['callback'](data);
  }
}

</script>

<template>
  <el-upload class="my-uploader"
             ref="uploader"
             list-type="picture"
             :name="name"
             :action="url"
             :headers="{'token': currentToken}"
             :drag="true"
             :limit="limit"
             :show-file-list="false"
             :auto-upload="autoUpload"
             :before-upload="beforeUpload"
             :on-success="onSuccess">
    <el-link class="el-icon--upload"
             icon="UploadFilled"
             :underline="false"/>
    <div class="el-upload__text">拖拽文件 或 <em>点击上传</em></div>
    <template #tip>
      <div class="el-upload__tip">
        仅支持 {{ allowTypes.toString().replaceAll('image/', '').replaceAll(',', ', ') }}
        格式文件，且单个文件不超过{{ maxSize }}MB
      </div>
    </template>
  </el-upload>

  <!--上传按钮-->
  <el-button class="upload-btn"
             v-if="!autoUpload"
             :type="btnType"
             @click="uploader.submit()">
    确认上传
  </el-button>
</template>

<style scoped lang="scss">
.upload-btn {
  margin-top: 10px; // 上外边距
  width: 100%; // 宽度
}
</style>
