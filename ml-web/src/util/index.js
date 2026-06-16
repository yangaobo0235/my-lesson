import {ElMessage} from "element-plus";
import router from "../router/index.js";

/**
 * 判断非空值
 *
 * @param value 被判断的值
 * @return boolean 返回 true 表示不为 null 也不为 undefined
 */
export function isNotNull(value) {
    return value !== null && value !== undefined;
}

/**
 * 判断空值
 *
 * @param value 被判断的值
 * @return boolean 返回 true 表示为 null 或 undefined
 */
export function isNull(value) {
    return !isNotNull(value);
}

/**
 * 判断是否存在空值
 *
 * @param values 被判断的值，不定长列表
 * @return boolean 返回 true 表示包含 null 或 undefined
 */
export function hasNull(...values) {
    for (let value in values) {
        if (isNull(value)) {
            return true;
        }
    }
    return false;
}

/**
 * 判断空字符串
 *
 * @param value 被判断的值
 * @return boolean 返回 true 表示不为 null 或 undefined 或空字符串
 */
export function isNotEmpty(value) {
    return value !== null && value !== undefined && value !== '';
}

/**
 * 判断非空字符串
 *
 * @param value 被判断的值
 * @return boolean 返回 true 表示为 null 或 undefined 或空字符串
 */
export function isEmpty(value) {
    return !isNotEmpty(value);
}

/**
 * 判断是否存在空字符串
 *
 * @param values 被判断的值，不定长列表
 * @return boolean 返回 true 表示包含 null 或 undefined 或空字符串
 */
export function hasEmpty(...values) {
    for (let value in values) {
        if (isEmpty(value)) {
            return true;
        }
    }
    return false;
}

// 用于将六大元素处理为两位数格式（年除外）
function toDouble(e) {
    return e < 10 ? '0' + e : e;
}

/**
 * 日期字符串处理：1999-01-02T12:12:12 -> 1999年01月02日 12:12
 *
 * @param dateStr 日期字符串
 * @return string 返回格式化后的日期字符串
 */
export function datetimeFormat(dateStr) {
    if (isNull(dateStr)) return '';
    // 将日期字符串转为日期格式
    let date = new Date(dateStr);
    // 获取日期中的元素: 年，月，日，时，分
    let yy = toDouble(date.getFullYear());
    let mm = toDouble(date.getMonth() + 1);
    let dd = toDouble(date.getDate());
    let hh = toDouble(date.getHours());
    let mi = toDouble(date.getMinutes());
    // 返回美化后的日期字符串
    return `${yy}年${mm}月${dd}日 ${hh}:${mi}`;
}

/**
 * 日期字符串处理：1999-01-02T12:12:12 -> 1999年01月02日
 *
 * @param dateStr 日期字符串
 * @return string 返回格式化后的日期字符串
 */
export function dateFormat(dateStr) {
    if (isNull(dateStr)) return '';
    // 将日期字符串转为日期格式
    let date = new Date(dateStr);
    // 获取日期中的元素: 年，月，日，时，分
    let yy = toDouble(date.getFullYear());
    let mm = toDouble(date.getMonth() + 1);
    let dd = toDouble(date.getDate());
    // 返回美化后的日期字符串
    return `${yy}年${mm}月${dd}日`;
}

/**
 * 性别代码处理：0->'女'，1->'男'，2->'保密'
 *
 * @param genderCode 性别代码
 * @return string 对应的性别字符串
 */
export function genderFormat(genderCode) {
    if (genderCode === '0' || genderCode === 0) return '女孩';
    if (genderCode === '1' || genderCode === 1) return '男孩';
    if (genderCode === '2' || genderCode === 2) return '保密';
    return '性别代码异常';
}

/**
 * 秒杀活动状态代码处理：0->'未开始'，1->'已开始'，2->'已结束'
 *
 * @param status 秒杀活动状态代码
 * @return string 对应的秒杀活动状态字符串
 */
export function seckillStatusFormat(status) {
    if (status === '0' || status === 0) return '未开始';
    if (status === '1' || status === 1) return '已开始';
    if (status === '2' || status === 2) return '已结束';
    return '秒杀活动状态代码异常';
}

/**
 * 订单状态代码处理：0->'未付款'，1->'已付款'，2->'已取消'，3->'其他'
 *
 * @param stateCode 订单状态代码
 * @return string 对应的订单状态代码字符串
 */
export function orderStateFormat(stateCode) {
    if (stateCode === '0' || stateCode === 0) return '未付款';
    if (stateCode === '1' || stateCode === 1) return '已付款';
    if (stateCode === '2' || stateCode === 2) return '已取消';
    if (stateCode === '3' || stateCode === 3) return '其他';
    return '订单状态代码异常';
}

/**
 * 订单支付方式代码处理：0->'未支付'，1->'微信'，2->'支付宝'，3->'其他'
 *
 * @param typeCode 订单支付方式代码
 * @return string 对应的订单支付方式代码字符串
 */
export function orderPayTypeFormat(typeCode) {
    if (typeCode === '0' || typeCode === 0) return '未支付';
    if (typeCode === '1' || typeCode === 1) return '微信';
    if (typeCode === '2' || typeCode === 2) return '支付宝';
    if (typeCode === '3' || typeCode === 3) return '其他';
    return '订单支付方式代码异常';
}

/**
 * DML 操作后处理函数：添加，修改，删除或上传操作成功后，在指定延迟时间后，跳转到指定页面
 *
 * @param path 跳转路径
 * @param seconds 延迟时间，单位毫秒，默认 1000 毫秒
 * @return void
 */
function successTo(path, seconds = 1000) {
    ElMessage('操作成功！');
    setTimeout(() => router.push(path), seconds);
}

