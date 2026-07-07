// 环境IP地址
const HOST = 'http://localhost';
export const GATEWAY_HOST = `${HOST}:24101`;
export const USER_EXCEL_HOST = `${GATEWAY_HOST}/user-server/api/v1/user/excel`;
export const EPISODE_EXCEL_HOST = `${GATEWAY_HOST}/course-server/api/v1/episode/excel`;
export const ORDER_EXCEL_HOST = `${GATEWAY_HOST}/order-server/api/v1/order/excel`;

// Minio函数
export const MINIO_HOST = import.meta.env.VITE_MINIO_PUBLIC_URL
    || 'http://192.168.23.66:9001/mylesson';
export const MINIO_AVATAR = url => MINIO_HOST + '/avatar/' + url;
export const MINIO_BANNER = url => MINIO_HOST + '/banner/' + url;
export const MINIO_COURSE_COVER = url => MINIO_HOST + '/course-cover/' + url;
export const MINIO_COURSE_SUMMARY = url => MINIO_HOST + '/course-summary/' + url;
export const MINIO_EPISODE_VIDEO = url => MINIO_HOST + '/episode-video/' + url;
export const MINIO_EPISODE_VIDEO_COVER = url => MINIO_HOST + '/episode-video-cover/' + url;

// 表单规则
export const RULE = {
    TITLE: [{pattern: /^.{1,42}$/, message: '标题长度必须在1~42之间'}],
    AUTHOR: [{pattern: /^.{1,42}$/, message: '作者名称长度必须在1~42之间'}],
    INFO: [{pattern: /^.{1,170}$/, message: '描述长度必须在1~170之间'}],
    CONTENT: [{pattern: /^.{1,170}$/, message: '内容长度必须在1~170之间'}],
    VCODE: [{pattern: /^\d{4}$/, message: '验证码必须为4位数字'}],
    MENU_URL: [{pattern: /^\/[a-zA-Z]{0,256}$/, message: '跳转地址必须以 / 开头，后续内容仅支持0~256个英文字母'}],
    MENU_ICON: [{pattern: /^[a-zA-Z]{1,256}$/, message: '图标仅支持1~256个英文字母'}],
    USERNAME: [{pattern: /^[a-zA-Z0-9]{4,20}$/, message: '账号必须由4到20个英文字母或数字组成'}],
    PASSWORD: [{pattern: /^[a-zA-Z0-9]{4,20}$/, message: '密码必须由4到20个英文字母或数字组成'}],
    REALNAME: [{pattern: /^[\u4e00-\u9fa5]{2,6}$/, message: '真实姓名必须由2到6个中文组成'}],
    NICKNAME: [{pattern: /^[\u4e00-\u9fa5|_a-zA-Z0-9]{2,10}$/, message: '昵称必须由2到10个中文、英文或数字组成'}],
    IDCARD: [{pattern: /^[1-9]\d{5}(19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$/, message: '身份证号格式不正确'}],
    PHONE: [{pattern: /^1(3[0-9]|4[01456879]|5[0-35-9]|6[2567]|7[0-8]|8[0-9]|9[0-35-9])\d{8}$/, message: '手机号码格式不正确'}],
    EMAIL: [{pattern: /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/, message: '电子邮箱格式不正确'}],
    PROVINCE: [{pattern: /^[\u4e00-\u9fa5]{2,20}$/, message: '省份必须由2到20个中文组成'}],
    CODE: [{pattern: /^.{1,42}$/, message: '兑换口令长度必须在1~42之间'}],
    SN: [{pattern: /^.{1,42}$/, message: '订单编号长度必须在1~42之间'}],
}

/* ==================== 项目相关信息 ==================== */

// 项目环境信息
export const PROJECT_INFO = {
    title: 'MyLesson 课程平台',
    author: '杨奥博',
    version: 'v1.0.0',
    gatewayHost: `${HOST}:24101`,
    userHost: `${HOST}:24102`,
    courseHost: `${HOST}:24103`,
    saleHost: `${HOST}:24104`,
    orderHost: `${HOST}:24105`,
    searchHost: `${HOST}:24106`,
    socketHost: `${HOST}:24106`,
    webHost: `${HOST}:24108`,
    put: 22, post: 25, get: 63, delete: 41,
    info: 'MyLesson 是一个面向课程内容管理、用户学习服务、订单营销和 AI 学习助手的一体化课程平台。系统通过 Vue3 Web 前端统一承载业务操作入口，后端采用微服务架构拆分用户、课程、营销、订单和 AI 能力，由网关统一鉴权和转发请求。平台以角色、菜单和业务接口权限为基础，支持课程内容维护、学习计划生成、AI 问答、受控工具调用、订单与营销数据管理等核心流程。',
};

// 项目技术栈信息
export const PROJECT_SKILLS = [
    {label: '底层操作系统', value: 'Windows', version: '11'},
    {label: '语言开发环境', value: 'JDK', version: '17.0.9'},
    {label: '集成开发工具', value: 'IntelliJ IDEA', version: '2023.3.3.win Ultimate Edition'},
    {label: '项目管理工具', value: 'Maven', version: '3.9.9'},
    {label: '版本控制工具', value: 'Git', version: '2.28.0.windows.1'},
    {label: '代码托管中心', value: 'GitHub', version: 'latest'},
    {label: '前端服务容器', value: 'Node', version: '22.12+'},
    {label: '前端测试软件', value: 'Edge', version: '120.0.2210.77'},
    {label: '压力测试工具', value: 'JMeter', version: '5.4.1'},
    {label: '虚拟管理工具', value: 'VmWare', version: '17.5.1 build-23298084'},
    {label: '虚拟操作系统', value: 'OpenEuler', version: '24.03-LTS'},
    {label: '容器管理引擎', value: 'Docker', version: '18.09.0'},
    {label: '数据存储仓库', value: 'MySQL', version: '8.0.27'},
    {label: '对象存储仓库', value: 'MinIO', version: 'RELEASE.2023-08-31T15-31-16Z'},
    {label: '数据缓存仓库', value: 'Redis', version: '7.0.5'},
    {label: '反向代理组件', value: 'Nginx', version: '1.25.2'},
    {label: '搜索引擎组件', value: 'ElasticSearch', version: '8.4.0'},
    {label: '搜索引擎界面', value: 'Kibana', version: '8.4.0'},
    {label: '日志收集组件', value: 'Logstash', version: '8.4.0'},
    {label: '单元测试', value: 'junit', version: '4.13.2'},
    {label: '代码简化', value: 'lombok', version: '1.18.24'},
    {label: '通用工具', value: 'hutool', version: '5.8.25'},
    {label: '数据库驱动', value: 'mysql-connector-j', version: '8.2.0'},
    {label: '持久层框架', value: 'mybatis-flex-spring-boot3-starter', version: '1.10.2'},
    {label: '控制层框架', value: 'spring-boot-starter-web', version: '3.1.5'},
    {label: '切面编程', value: 'spring-boot-starter-aop', version: '3.1.5'},
    {label: '搜索引擎', value: 'spring-boot-starter-data-elasticsearch', version: '3.1.5'},
    {label: '缓存容器', value: 'spring-boot-starter-data-redis', version: '3.1.5'},
    {label: '缓存工具', value: 'spring-boot-starter-cache', version: '3.1.5'},
    {label: '登录校验', value: 'jjwt', version: '0.9.1'},
    {label: '参数校验', value: 'hibernate-validator', version: '8.0.1.Final'},
    {label: '报表打印', value: 'easyexcel', version: '3.3.4 '},
    {label: '对象存储', value: 'minio', version: '3.0.10'},
    {label: '文档工具', value: 'knife4j-openapi3-jakarta-spring-boot-starter', version: '4.4.0'},
    {label: '注册中心', value: 'spring-cloud-starter-alibaba-nacos-discovery', version: '2022.0.0.0'},
    {label: '配置中心', value: 'spring-cloud-starter-alibaba-nacos-config', version: '2022.0.0.0 '},
    {label: '服务容错', value: 'spring-cloud-starter-alibaba-sentinel', version: '2022.0.0.0'},
    {label: '分布式事务', value: 'spring-cloud-starter-alibaba-seata', version: '2022.0.0.0'},
    {label: '分布式调度', value: 'xxl-job', version: '2.4.2'},
    {label: '远程调用', value: 'spring-cloud-starter-openfeign', version: '4.0.4'},
    {label: '链路追踪', value: 'micrometer-tracing', version: '1.11.5'},
    {label: '消息队列', value: 'rocketmq-spring-boot-starter', version: '2.2.2'},
    {label: '页面布局', value: 'HTML', version: '5'},
    {label: '页面美化', value: 'CSS', version: '3'},
    {label: '脚本功能', value: 'ECMAScript', version: ''},
    {label: '前端服务器', value: 'node', version: '22.12+'},
    {label: 'Vue脚手架', value: 'vite', version: '8.0.16'},
    {label: 'Vue路由', value: 'vue-router', version: '4.0.3'},
    {label: 'Vue样式预处理器', value: 'sass-embedded', version: '1.77.8'},
    {label: 'Vue状态管理', value: 'vuex', version: '4.0.0'},
    {label: 'AJAX产品', value: 'axios', version: '1.17.0'},
    {label: 'WEB框架', value: 'element-plus', version: '2.14.2'},
    {label: 'WEB框架图标库', value: 'icons-vue', version: '2.3.1'},
    {label: 'WEB框架暗黑库', value: '@vueuse/core', version: '10.7.2'},
    {label: '视频播放器', value: 'xgplayer', version: '3.0.11'},
    {label: '图表库', value: 'ApacheEcharts', version: '5.4.3'},
    {label: 'Web 前端', value: 'Vue3 + Element Plus', version: 'Vite'}
];

/* ==================== 下拉菜单预设选项 ==================== */

// 下拉菜单选项 - 性别
export const GENDER_OPTIONS = [
    {label: '女孩', value: 0},
    {label: '男孩', value: 1},
    {label: '保密', value: 2},
];

// 下拉菜单选项 - 星座
export const ZODIAC_OPTIONS = [
    {label: '白羊座（Aries）', value: '白羊座'},
    {label: '金牛座（Taurus）', value: '金牛座'},
    {label: '双子座（Gemini）', value: '双子座'},
    {label: '巨蟹座（Cancer）', value: '巨蟹座'},
    {label: '狮子座（Leo）', value: '狮子座'},
    {label: '处女座（Virgo）', value: '处女座'},
    {label: '天秤座（Libra）', value: '天秤座'},
    {label: '天蝎座（Scorpio）', value: '天蝎座'},
    {label: '射手座（Sagittarius）', value: '射手座'},
    {label: '摩羯座（Capricorn）', value: '摩羯座'},
    {label: '水瓶座（Aquarius）', value: '水瓶座'},
    {label: '双鱼座（Pisces）', value: '双鱼座'},
];

// 下拉菜单选项 - 省份
export const PROVINCE_OPTIONS = [
    {label: '北京', value: '北京'},
    {label: '上海', value: '上海'},
    {label: '天津', value: '天津'},
    {label: '重庆', value: '重庆'},
    {label: '河北', value: '河北'},
    {label: '山西', value: '山西'},
    {label: '辽宁', value: '辽宁'},
    {label: '吉林', value: '吉林'},
    {label: '黑龙江', value: '黑龙江'},
    {label: '江苏', value: '江苏'},
    {label: '浙江', value: '浙江'},
    {label: '安徽', value: '安徽'},
    {label: '福建', value: '福建'},
    {label: '江西', value: '江西'},
    {label: '山东', value: '山东'},
    {label: '河南', value: '河南'},
    {label: '湖北', value: '湖北'},
    {label: '湖南', value: '湖南'},
    {label: '广东', value: '广东'},
    {label: '广西', value: '广西'},
    {label: '海南', value: '海南'},
    {label: '四川', value: '四川'},
    {label: '贵州', value: '贵州'},
    {label: '云南', value: '云南'},
    {label: '西藏', value: '西藏'},
    {label: '陕西', value: '陕西'},
    {label: '甘肃', value: '甘肃'},
    {label: '青海', value: '青海'},
    {label: '宁夏', value: '宁夏'},
    {label: '新疆', value: '新疆'},
    {label: '香港', value: '香港'},
    {label: '澳门', value: '澳门'},
    {label: '台湾', value: '台湾'},
    {label: '其他', value: '其他'},
];

// 下拉菜单选项 - 秒杀活动状态
export const SECKILL_STATUS_OPTIONS = [
    {label: '未开始', value: 0},
    {label: '已开始', value: 1},
    {label: '已结束', value: 2},
];

// 下拉菜单选项 - 订单状态
export const ORDER_STATE_OPTIONS = [
    {label: '未付款', value: 0},
    {label: '已付款', value: 1},
    {label: '已取消', value: 2},
    {label: '其它', value: 3}
];

// 下拉菜单选项 - 订单支付方式
export const ORDER_PAY_TYPE_OPTIONS = [
    {label: '未支付', value: 0},
    {label: '微信', value: 1},
    {label: '支付宝', value: 2},
    {label: '其它', value: 3},
];
