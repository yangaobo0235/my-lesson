import * as echarts from "echarts";

/*
 * 绘制 Echarts 饼图
 *
 * dom: 图表容器，是一个DOM对象，必传。
 * data: 系列数据，必传，格式为 [{name: '男', value: 10}, {name: '女', value: 20}]
 * name: 系列名称，用于 tooltip 展示，默认空，可选
 * radius: 饼图半径，默认60%，可选
 * unit：数据单位，默认空，可选
 * title：图标标题，默认空，可选
 */
export function pie(config) {

    const dom = config['dom'];
    const data = config['data'];
    const name = config['name'] || '';
    const radius = config['radius'] || '60%';
    const unit = config['unit'] || '';
    const title = config['title'] || '';

    // 系列据
    const series = {
        type: 'pie', // 系列类型
        name: name, // 系列名称
        data: data, // 系列数据
        radius: radius, // 饼图半径
        label: {
            show: true, // 显示文字
            formatter: arg => arg.name + ` ${arg.value} ${unit}（${arg.percent}%）` // 格式化
        }
    };

    // 绘制图表
    echarts.init(dom).setOption({
        title: {text: title},
        series: [series],
        tooltip: {}
    });
}

/*
 * 绘制 Echarts 柱图
 *
 *  dom: 图表容器，是一个DOM对象，必传。
 *  xData: X轴数据，必传，格式为 ['男', '女']
 *  yData: Y轴数据，必传，格式为 ['10', '20']
 *  xName: X轴名称，必传
 *  yName: Y轴名称，必传
 *  name: 系列名称，用于 tooltip 展示，默认空，可选
 *  barWidth: 柱图宽度，默认50%，可选
 *  markMax: 是否标记最大值，默认true，可选
 *  markMin: 是否标记最小值，默认true，可选
 *  markAvg: 是否标记平均值，默认true，可选
 *  labelShow: 是否在柱子上显示文字，默认true，可选
 *  labelRotate: 文字旋转角度，默认60，可选
 */
export function bar(config) {

    const dom = config['dom'];
    const xData = config['xData'];
    const yData = config['yData'];
    const xName = config['xName'];
    const yName = config['yName'];
    const name = config['name'] || '';
    const barWidth = config['barWidth'] || '50%';
    const markMax = config['markMax'] || true;
    const markMin = config['markMin'] || true;
    const markAvg = config['markAvg'] || true;
    const labelShow = config['labelShow'] || true;
    const labelRotate = config['labelRotate'] || 60;

    // 系列数据
    const series = {
        type: 'bar', // 系列类型
        name: name, // 系列名称
        data: yData, // 系列数据
        // 标记点
        markPoint: {
            data: [
                markMax ? {type: 'max', name: '最大值'} : {}, // 自动标记最大值
                markMin ? {type: 'min', name: '最小值'} : {}, // 自动标记最小值
            ]
        },
        // 标记线
        markLine: {
            data: [
                markAvg ? {type: 'average', name: '平均值'} : {} // 标记平均值线
            ]
        },
        // 图形文字
        label: {
            show: labelShow,
            rotate: labelRotate
        }
    };

    // 数据X轴
    const xAxis = {
        name: xName, // 名称，可选
        data: xData, // 数据数组
        nameLocation: 'end', // 名称位置，可选
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    };

    // 数据Y轴
    const yAxis = {
        name: yName, // 名称，可选
        nameLocation: 'end', // 名称位置，可选
        min: 0, // 最小值
        scale: true, // 自适应最大最小值
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    };

    // 绘制图表
    echarts.init(dom).setOption({
        series: [series],
        xAxis: xAxis,
        yAxis: yAxis,
        tooltip: {},
        barWidth: barWidth
    });
}

/*
 * 绘制 Echarts 线图
 *
 *  dom: 图表容器，是一个DOM对象，必传。
 *  xData: X轴数据，必传，格式为 ['男', '女']
 *  yData: Y轴数据，必传，格式为 ['10', '20']
 *  xName: X轴名称，必传
 *  yName: Y轴名称，必传
 *  name: 系列名称，用于 tooltip 展示，默认空，可选
 *  markMax: 是否标记最大值，默认true，可选
 *  markMin: 是否标记最小值，默认true，可选
 *  markAvg: 是否标记平均值，默认true，可选
 */
export function line(config) {

    const dom = config['dom'];
    const xData = config['xData'];
    const yData = config['yData'];
    const xName = config['xName'];
    const yName = config['yName'];
    const name = config['name'] || '';
    const markMax = config['markMax'] || true;
    const markMin = config['markMin'] || true;
    const markAvg = config['markAvg'] || true;

    // 系列数据
    const series = {
        type: 'line', // 系列类型
        name: name, // 系列名称
        data: yData, // 系列数据
        // 标记点
        markPoint: {
            data: [
                markMax ? {type: 'max', name: '最大值'} : {}, // 自动标记最大值
                markMin ? {type: 'min', name: '最小值'} : {}, // 自动标记最小值
            ]
        },
        // 标记线
        markLine: {
            data: [
                markAvg ? {type: 'average', name: '平均值'} : {} // 标记平均值线
            ]
        }
    }

    // 数据X轴
    const xAxis = {
        name: xName, // 名称，可选
        type: 'category', // 类型
        data: xData, // 数据数组
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    }

    // 数据Y轴
    const yAxis = {
        name: yName, // 名称，可选
        nameLocation: 'end', // 名称位置，可选
        min: 0, // 最小值
        scale: true, // 自适应最大最小值
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    };

    // 绘制图表
    echarts.init(dom).setOption({
        xAxis: xAxis,
        yAxis: yAxis,
        series: [series],
        tooltip: {},
    });
}

/*
 * 绘制 Echarts 点图
 *
 *  dom: 图表容器，是一个DOM对象，必传。
 *  xData: X轴数据，必传，格式为 ['男', '女']
 *  yData: Y轴数据，必传，格式为 ['10', '20']
 *  xName: X轴名称，必传
 *  yName: Y轴名称，必传
 *  name: 系列名称，用于 tooltip 展示，默认空，可选
 *  markMax: 是否标记最大值，默认true，可选
 *  markMin: 是否标记最小值，默认true，可选
 *  markAvg: 是否标记平均值，默认true，可选
 */
export function point(config) {

    const dom = config['dom'];
    const xData = config['xData'];
    const yData = config['yData'];
    const xName = config['xName'];
    const yName = config['yName'];
    const name = config['name'] || '';
    const markMax = config['markMax'] || true;
    const markMin = config['markMin'] || true;
    const markAvg = config['markAvg'] || true;

    // 系列数据
    const series = {
        type: 'scatter', // 系列类型
        name: name, // 系列名称
        data: yData, // 系列数据
        // 标记点
        markPoint: {
            data: [
                markMax ? {type: 'max', name: '最大值'} : {}, // 自动标记最大值
                markMin ? {type: 'min', name: '最小值'} : {}, // 自动标记最小值
            ]
        },
        // 标记线
        markLine: {
            data: [
                markAvg ? {type: 'average', name: '平均值'} : {} // 标记平均值线
            ]
        }
    }

    // 数据X轴
    const xAxis = {
        name: xName, // 名称，可选
        type: 'category', // 类型
        data: xData, // 数据数组
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    }

    // 数据Y轴
    const yAxis = {
        name: yName, // 名称，可选
        nameLocation: 'end', // 名称位置，可选
        min: 0, // 最小值
        scale: true, // 自适应最大最小值
        axisLine: {show: true, lineStyle: {color: '#48b', width: 2}}, // 轴线
        axisLabel: {show: true} // 文本
    };

    // 绘制图表
    echarts.init(dom).setOption({
        xAxis: xAxis,
        yAxis: yAxis,
        series: [series],
        tooltip: {},
    });
}
