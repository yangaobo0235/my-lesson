import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.TicketApplication;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteSlotMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteBookingForm;
import com.yangaobo.servicedesk.ticket.service.IOnsiteBookingService;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * 独占MySQL上验证预约容量、原键、取消和履约竞争。
 *
 * @author yangaobo
 */
class VerifyOnsite {
    /** 只接收显式隔离环境，不连接共享业务库。 */
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("DESK_VERIFY_ISOLATED"))) throw new IllegalStateException("仅独占验收环境");
        try (var app = SpringApplication.run(TicketApplication.class,"--server.port=0")) {
            var db=app.getBean(JdbcTemplate.class);
            require("desk_ticket_ci".equals(db.queryForObject("SELECT DATABASE()",String.class)),"独占库");
            var tickets=app.getBean(TicketMapper.class);
            var slots=app.getBean(OnsiteSlotMapper.class);
            var service=app.getBean(IOnsiteBookingService.class);
            var json=app.getBean(ObjectMapper.class);
            String item=id(), user=id(), entitlement=id(), customer=id(), handler=id();
            var slotService=app.getBean(com.yangaobo.servicedesk.ticket.service.IOnsiteSlotService.class);
            var admin=new IdentityDTO();admin.setOwnerId(user);admin.setMustChangePassword(false);admin.setRoles(Set.of("SYSTEM_ADMIN"));
            var starts=tickets.decisionTime().plusDays(2).withHour(1).withMinute(0).withSecond(0).withNano(0);
            var slotForm=new com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotForm();
            slotForm.setServiceAreaCode("VERIFY");slotForm.setCapacity(1);
            slotForm.setStartsAt(starts.atOffset(java.time.ZoneOffset.UTC));
            slotForm.setBookingOpensAt(tickets.decisionTime().plusHours(1).atOffset(java.time.ZoneOffset.UTC));
            slotForm.setBookingClosesAt(starts.minusHours(1).atOffset(java.time.ZoneOffset.UTC));
            var draftSlot=slotService.add(admin,slotForm);
            require(slotService.query(admin,draftSlot.getId()).getStatus().equals("DRAFT"),"草稿单项查询");
            var update=new com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotUpdateForm();
            update.setId(draftSlot.getId());update.setRowVersion(draftSlot.getRowVersion());update.setCapacity(2);
            update.setServiceAreaCode(slotForm.getServiceAreaCode());update.setStartsAt(slotForm.getStartsAt());
            update.setBookingOpensAt(slotForm.getBookingOpensAt());update.setBookingClosesAt(slotForm.getBookingClosesAt());
            var updated=slotService.update(admin,update);require(updated.getCapacity()==2,"原版本修改草稿容量");
            expect(()->slotService.update(admin,update),1004);
            var publish=new com.yangaobo.servicedesk.common.base.IdVersionForm();publish.setId(updated.getId());publish.setRowVersion(updated.getRowVersion());
            var published=slotService.change(admin,publish,false);update.setRowVersion(published.getRowVersion());
            expect(()->slotService.update(admin,update),1004);
            db.update(""" 
                INSERT INTO it_service_item
                (id,service_code,category,service_name,support_scope,onsite_supported,created_by,updated_by,change_reason)
                VALUES (?,?,'TERMINAL','隔离预约项目','Windows办公终端',true,?,?,'隔离实验')
                """,item,"VERIFY_"+item.toUpperCase(java.util.Locale.ROOT),user,user);
            db.update("""
                INSERT INTO it_service_entitlement
                (id,customer_id,service_item_id,starts_on,ends_on,granted_by,updated_by,change_reason)
                VALUES (?,?,?,CURRENT_DATE-INTERVAL 1 DAY,CURRENT_DATE+INTERVAL 1 DAY,?,?,'隔离实验')
                """,entitlement,customer,item,user,user);
            OnsiteSlotEntity future=slot(tickets,slots,user,false);
            TicketEntity a=ticket(tickets,json,item,entitlement,customer,handler);
            TicketEntity b=ticket(tickets,json,item,entitlement,customer,handler);
            IdentityDTO customerA=actor(a,false), customerB=actor(b,false), technician=actor(a,true);
            CountDownLatch start=new CountDownLatch(1);
            var requestA=request(future.getId()); var requestB=request(future.getId());
            try(var executor=Executors.newFixedThreadPool(2)) {
                var first=executor.submit(()->{start.await();return service.book(customerA,a.getId(),requestA);});
                var second=executor.submit(()->{start.await();return service.book(customerB,b.getId(),requestB);});
                start.countDown();
                var x=first.get().getBooking(); var y=second.get().getBooking();
                require(Set.of(x.getStatus(),y.getStatus()).equals(Set.of("CONFIRMED","REJECTED")),"最后名额只确认一人");
                var winning="CONFIRMED".equals(x.getStatus())?a:b;
                var owner=winning==a?customerA:customerB;
                var key=winning==a?requestA:requestB;
                var receipt=winning==a?x:y;
                require(!service.book(owner,winning.getId(),key).isCreated(),"原键重放返回原资源");
                require(slots.selectById(future.getId()).getReservedCount()==1,"重放不重复扣容量");
                require(service.end(owner,winning.getId(),receipt.getId(),null).getStatus().equals("CANCELLED"),"到访前取消");
                service.end(owner,winning.getId(),receipt.getId(),null);
                require(slots.selectById(future.getId()).getReservedCount()==0,"重复取消只回补一次");
                require(service.book(owner,winning.getId(),key).getBooking().getStatus().equals("CANCELLED"),"原键不复活取消预约");
                var changed=request(future.getId()); changed.setClientRequestId(key.getClientRequestId());
                changed.setSlotId(id());
                conflict(()->service.book(owner,winning.getId(),changed));
            }
            OnsiteSlotEntity past=slot(tickets,slots,user,true);
            TicketEntity c=ticket(tickets,json,item,entitlement,customer,handler);
            // 仅用隔离夹具模拟已开始窗口的历史确认；不添加生产测试接口。
            String booking=id();
            db.update("""
                INSERT INTO sd_onsite_booking
                (id,ticket_id,slot_id,service_area_code,visit_address,client_request_id,request_hash,status,confirmed_at,created_at,updated_at)
                VALUES (?,?,?,'VERIFY','隔离地址',?,?,'CONFIRMED',UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)-INTERVAL 1 DAY)
                """,booking,c.getId(),past.getId(),id(),"b".repeat(64));
            db.update("UPDATE sd_onsite_slot SET reserved_count=1 WHERE id=?",past.getId());
            conflict(()->service.end(actor(c,false),c.getId(),booking,null));
            require(service.end(actor(c,true),c.getId(),booking,"VISITED").getFinishResult().equals("VISITED"),"真实履约");
            service.end(actor(c,true),c.getId(),booking,"VISITED");
            conflict(()->service.end(actor(c,true),c.getId(),booking,"NOT_PERFORMED"));
            require(service.end(actor(c,false),c.getId(),booking,null).getStatus().equals("FINISHED"),"取消返回已履约真实状态");
            require(slots.selectById(past.getId()).getReservedCount()==1,"已履约保留容量占用");
            require(db.queryForObject("SELECT COUNT(*) FROM sd_ticket_event WHERE onsite_booking_id=?",Long.class,booking)==1,"履约事件只追加一次");
            IdentityDTO intruder=actor(c,false); intruder.setOwnerId(id());
            conflict(()->service.history(intruder,c.getId(),new BasePageForm()));
            require(service.history(actor(c,false),c.getId(),new BasePageForm()).getTotal()==1,"本人读取历史");
            System.out.println("PASS onsite: capacity race, replay, hash conflict, cancel once, finish replay/conflict, visibility, timeline, notifications");
        }
    }

    /** 两个相隔一天的窗口用于取消与真实履约边界。 */
    private static OnsiteSlotEntity slot(TicketMapper tickets,OnsiteSlotMapper mapper,String actor,boolean past) {
        LocalDateTime now=tickets.decisionTime();
        LocalDateTime start=now.toLocalDate().plusDays(past?-1:1).atTime(1,0);
        var slot=new OnsiteSlotEntity(); slot.setId(id());slot.setServiceAreaCode("VERIFY");
        slot.setStartsAt(start);slot.setEndsAt(start.plusHours(2));
        slot.setBookingOpensAt(start.minusDays(2));slot.setBookingClosesAt(start);
        slot.setCapacity(1);slot.setReservedCount(0);slot.setStatus("PUBLISHED");
        slot.setCreatedBy(actor);slot.setPublishedBy(actor);slot.setPublishedAt(start.minusDays(3));
        slot.setCreatedAt(start.minusDays(3));slot.setUpdatedAt(now);slot.setRowVersion(0L);
        mapper.insert(slot);return slot;
    }

    /** 工单仍沿原受理资格履约，不以设备或未来资格期限替代。 */
    private static TicketEntity ticket(TicketMapper mapper,ObjectMapper json,String item,String entitlement,String customer,String handler) {
        LocalDateTime now=mapper.decisionTime(); var ticket=new TicketEntity();
        ticket.setId(id());ticket.setTicketNo("VERIFY_"+ticket.getId());ticket.setCustomerId(customer);
        ticket.setRequesterId(id());ticket.setClientRequestId(id());ticket.setRequestHash("a".repeat(64));
        ticket.setServiceItemId(item);ticket.setEntitlementId(entitlement);
        ticket.setServiceScopeSnapshot(json.createObjectNode().put("schema_version",1).put("admission_type","COVERED").put("onsite_supported",true));
        ticket.setSupportContext(json.createObjectNode().put("schema_version",1));
        ticket.setTitle("隔离上门验收");ticket.setSymptom("仅验证业务约束");ticket.setStatus("PROCESSING");
        ticket.setPriority("NORMAL");ticket.setOnsiteEligible(true);ticket.setAssigneeId(handler);
        ticket.setServiceAreaCode("VERIFY");ticket.setServiceLocation("隔离地址");
        ticket.setFirstAcceptedAt(now);ticket.setAcceptanceDueAt(now.plusHours(4));
        ticket.setCreatedAt(now);ticket.setUpdatedAt(now);ticket.setRowVersion(0L);
        mapper.insert(ticket);return ticket;
    }

    /** 明确已认证领域身份；网络身份链另由HTTP/mTLS验收。 */
    private static IdentityDTO actor(TicketEntity ticket,boolean technician) {
        var actor=new IdentityDTO();actor.setOwnerId(technician?ticket.getAssigneeId():ticket.getRequesterId());
        actor.setOrganizationId(technician?id():ticket.getCustomerId());actor.setMustChangePassword(false);
        actor.setRoles(Set.of(technician?"TECHNICIAN":"CUSTOMER_USER"));
        actor.setCustomerIds(technician?Set.of(ticket.getCustomerId()):Set.of());return actor;
    }

    /** 请求键每个新动作只生成一次。 */
    private static OnsiteBookingForm request(String slot) {
        var form=new OnsiteBookingForm();form.setSlotId(slot);form.setClientRequestId(id());return form;
    }
    /** 预期业务拒绝必须实际抛出，不能以跳过冒充通过。 */
    private static void conflict(Runnable action) {
        try {action.run();} catch(BusinessException expected) {return;}
        throw new AssertionError("应拒绝冲突或无权动作");
    }
    /** 精确业务码断言避免无关SQL或配置异常冒充版本保护。 */
    private static void expect(Runnable action,int code) {
        try {action.run();} catch(BusinessException expected) {
            require(expected.getErrorCode().code()==code,"错误码不匹配");return;
        }
        throw new AssertionError("应拒绝目标动作");
    }
    /** 不依赖-ea参数。 */
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    /** 夹具标识与真实业务隔离。 */
    private static String id() {return UUID.randomUUID().toString().replace("-","");}
}
