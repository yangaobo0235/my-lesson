package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;

/**
 * 客户可选资产的业务边界。
 *
 * @author yangaobo
 */
public interface IDeviceService extends IService<DeviceEntity> {
    /** 在当前角色和客户范围内分页查询。 */
    PageVO<DeviceVO> listResources(IdentityDTO actor, SupportQueryForm form);

    /** 读取当前有权详情，历史退役记录保留。 */
    DeviceVO query(IdentityDTO actor, String id);

    /** 核验新关联资格后登记。 */
    DeviceVO create(IdentityDTO actor, DeviceCreateForm form);

    /** 带版本维护允许修改字段。 */
    DeviceVO updateResource(IdentityDTO actor, DeviceUpdateForm form);

    /** 停止新增关联，不物理删除历史。 */
    DeviceVO retire(IdentityDTO actor, IdVersionForm form);
}
