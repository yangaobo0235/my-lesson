<script setup>
// 引入西瓜播放器: 核心实例，核心CSS，弹幕插件，弹幕CSS
import Player from 'xgplayer';
import 'xgplayer/dist/index.min.css';
import Barrage from 'xgplayer/es/plugins/danmu';
import 'xgplayer/es/plugins/danmu/index.css';
import {onMounted, watch} from "vue";

// 播放器对象
let player = null;

let parent = defineProps({
  // 视频地址，必传
  url: {type: String, required: true},
  // 视频封面地址，必传
  poster: {type: String, required: true},
  // 流式布局，根据父容器调整宽高，默认 true
  fluid: {type: Boolean, required: false, default: true},
  // 视频切换时的标识变量，如 close 表示重置播放器
  message: {type: String, required: false},
  // 分离控制栏，默认 true
  marginControls: {type: Boolean, required: false, default: true},
  // 自动播放，默认 false
  autoplay: {type: Boolean, required: false, default: false},
  // 静音播放，默认 true
  autoplayMuted: {type: Boolean, required: false, default: true},
  // 语言，默认 zh-cn
  lang: {type: String, required: false, default: 'zh-cn'},
  // 初始音量，默认 0.6
  volume: {type: Number, required: false, default: 0.6},
  // 倍速列表，默认 [0.5, 1, 2, 4]
  playbackRate: {type: Array, required: false, default: [0.5, 1, 2, 4]},
  // 循环播放，默认 false
  loop: {type: Boolean, required: false, default: false},
  // 允许下载，默认 true
  download: {type: Boolean, required: false, default: true},
  // 迷你屏，默认 true
  mini: {type: Boolean, required: false, default: true},
  // 画中画，默认 true
  pip: {type: Boolean, required: false, default: true},
});

// 监听 message 变量，message 发生变化时触发
watch(() => parent['message'], (newValue, oldValue) => {
  if (newValue === 'close') {
    // 重置播放器
    player.resetState();
  }
});

// 页面加载完毕后再进行视频初始化
onMounted(() => {

  // 欢迎弹幕配置
  const welcomeBarrage = {
    duration: 15000, // 弹幕持续显示时间，毫秒，最低为 5000
    id: randomStr(10), // 弹幕ID，必须唯一
    start: 3000, // 弹幕出现时间，毫秒
    prior: true, // 该条弹幕优先显示，默认 false
    color: true, // 该条弹幕为彩色弹幕，默认 false
    txt: '一大波弹幕即将来袭', // 弹幕内容
    style: {color: 'red', fontSize: '20px', padding: '20px'}, // 弹幕样式
    mode: 'scroll' // 显示模式 [top顶部居中，bottom底部居中，scroll滚动]，默认为scroll
  };

  // 实例化西瓜播放器
  player = new Player({
    id: 'player', // 绑定DOM元素的ID值
    url: parent['url'], // 视频地址
    poster: parent['poster'], // 视频封面
    fluid: parent['fluid'], // 根据父容器调整宽高
    marginControls: parent['marginControls'], // 分离控制栏
    lang: parent['lang'], // 语言
    volume: parent['volume'], // 音量
    playbackRate: parent['playbackRate'], // 倍速列表
    autoplay: parent['autoplay'], // 自动播放
    autoplayMuted: parent['autoplayMuted'], // 静音播放
    loop: parent['loop'], // 循环播放
    download: parent['download'], // 允许下载
    mini: parent['mini'], // 迷你屏
    pip: parent['pip'], // 画中画
    plugins: [Barrage], // 添加弹幕插件
    danmu: {comments: [welcomeBarrage]} // 弹幕
  });
});

/*
 * 生成len位随机字符串
 *
 * @param len 随机字符串位数，范围在1 ~ 36之间，默认为10
 * @return 随机字符串
 * */
function randomStr(len = 10) {

  // 随机一个小数，并将其转为36进制字符
  let result = Math.random().toString(36);

  // 消除所有点符号
  result = result.replaceAll('.', '');

  // 截取后len位字符串并返回: len不小于1且不超过36
  len = Math.max(len, 1);
  len = Math.min(len, 36);
  return result.slice(-len);
}
</script>

<template>
  <div id="player"/>
</template>

