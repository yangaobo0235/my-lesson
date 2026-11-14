package com.yangaobo.servicedesk.ticket.model.dto.onsite;

import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;

/**
 * 事务内决定预约创建或原键重放，供HTTP保留201与200语义。
 *
 * @author yangaobo
 */
@lombok.Data
public class OnsiteBookingResult implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;
    /** 已提交的预约公开投影。 */
    private OnsiteBookingVO booking;
    /** 仅本次事务新建资源为true。 */
    private boolean created;
    /** 按事务事实构造，不通过HTTP前置查询猜测竞争结果。 */
    public static OnsiteBookingResult of(OnsiteBookingVO booking, boolean created) {
        OnsiteBookingResult result = new OnsiteBookingResult();
        result.setBooking(booking); result.setCreated(created); return result;
    }
}
