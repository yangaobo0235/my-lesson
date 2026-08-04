import {ElMessage} from 'element-plus';
import {getResponseData} from '../../request/index.js';
import {studentApi} from '../../api/student.js';

export async function blobToDataUrl(blob) {
    return await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
    });
}

export async function readBlobText(blob) {
    return await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsText(blob, 'utf-8');
    });
}

export async function createQrCode(sn) {
    const response = await studentApi.qrCode({sn});
    const contentType = response.headers?.['content-type'] || '';
    if (contentType.includes('application/json')) {
        const text = await readBlobText(response.data);
        try {
            const result = JSON.parse(text);
            throw new Error(result.message || result.data?.message || '获取支付二维码失败');
        } catch (error) {
            if (error instanceof SyntaxError) throw new Error('获取支付二维码失败');
            throw error;
        }
    }
    return await blobToDataUrl(response.data);
}

export function startPaymentPolling(sn, onPaid) {
    return setInterval(async () => {
        const paid = getResponseData(await studentApi.checkOrder(sn));
        if (paid === true) {
            ElMessage.success('支付成功');
            onPaid?.();
        }
    }, 2000);
}
