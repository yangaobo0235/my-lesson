import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.TicketApplication;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContentForm;
import com.yangaobo.servicedesk.ticket.service.ITicketDeadlineService;
import com.yangaobo.servicedesk.ticket.service.ITicketService;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * 在专用MySQL上验证真实期限服务，不增加测试专用生产接口。
 *
 * @author yangaobo
 */
class VerifyTicketDeadline {
    /**
     * 仅在显式隔离环境中启动独立应用上下文并执行竞争验收。
     */
    public static void main(String[] arguments) throws Exception {
        if (!"true".equals(System.getenv("DESK_VERIFY_ISOLATED"))) {
            throw new IllegalStateException("期限验收只允许独占环境");
        }
        try (var application = SpringApplication.run(TicketApplication.class, "--server.port=0")) {
            JdbcTemplate database = application.getBean(JdbcTemplate.class);
            require(
                    "desk_ticket_ci"
                            .equals(database.queryForObject("SELECT DATABASE()", String.class)),
                    "独占库名");
            TicketMapper mapper = application.getBean(TicketMapper.class);
            ITicketDeadlineService deadlines = application.getBean(ITicketDeadlineService.class);
            ITicketService tickets = application.getBean(ITicketService.class);
            ObjectMapper json = application.getBean(ObjectMapper.class);
            // 仅为期限实验直接写入历史时刻；身份链在HTTP/RPC验收中单独验证。
            TicketEntity overdue = fixture(mapper, json, false);
            require(deadlines.scan(200) >= 1, "停机后补扫");
            long version = mapper.selectById(overdue.getId()).getRowVersion();
            deadlines.scan(200);
            require(mapper.selectById(overdue.getId()).getRowVersion() == version, "重复扫描不改版本");
            require(count(database, overdue.getId(), "ACCEPTANCE_OVERDUE") == 1, "超期只记一次");

            TicketEntity lateAccept = fixture(mapper, json, false);
            IdentityDTO technician = actor(lateAccept, true);
            TicketActionForm accept = new TicketActionForm();
            accept.setClientActionId(id());
            accept.setRowVersion(0L);
            tickets.accept(technician, lateAccept.getId(), accept);
            require(count(database, lateAccept.getId(), "ACCEPTANCE_OVERDUE") == 1, "接单补记超期");
            require(count(database, lateAccept.getId(), "ACCEPTED") == 1, "接单与超期同事务");

            TicketEntity resolved = fixture(mapper, json, true);
            deadlines.scan(200);
            deadlines.scan(200);
            TicketEntity closed = mapper.selectById(resolved.getId());
            require("CLOSED".equals(closed.getStatus()), "到期关闭");
            require("CONFIRMATION_TIMEOUT".equals(closed.getClosureReason()), "超时不冒充客户确认");
            require("REFERRED".equals(closed.getResolutionType()), "保留人工结论");
            require(count(database, closed.getId(), "CLOSED") == 1, "关闭事件唯一");
            require(
                    database.queryForObject(
                            "SELECT COUNT(*) FROM sd_notification WHERE ticket_id = ?",
                            Long.class,
                            closed.getId())
                            == 2,
                    "通知与关闭同事务");

            for (int round = 0; round < 10; round++) {
                TicketEntity candidate = fixture(mapper, json, true);
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var scan =
                            executor.submit(
                                    () -> {
                                        start.await();
                                        return deadlines.scan(200);
                                    });
                    var returned =
                            executor.submit(
                                    () -> {
                                        start.await();
                                        TicketContentForm form = new TicketContentForm();
                                        form.setClientActionId(id());
                                        form.setRowVersion(0L);
                                        form.setContent("客户反馈问题仍存在");
                                        try {
                                            tickets.unresolved(
                                                    actor(candidate, false),
                                                    candidate.getId(),
                                                    form);
                                            return true;
                                        } catch (BusinessException conflict) {
                                            require(
                                                    conflict.getErrorCode().code() == 1004,
                                                    "竞争只允许版本冲突");
                                            return false;
                                        }
                                    });
                    start.countDown();
                    scan.get();
                    boolean returnedFirst = returned.get();
                    TicketEntity result = mapper.selectById(candidate.getId());
                    require(result.getRowVersion() == 1, "竞争只提交一个状态变化");
                    require(
                            (returnedFirst ? "PROCESSING" : "CLOSED").equals(result.getStatus()),
                            "退回和关闭互斥");
                    require(
                            count(database, candidate.getId(), "CLOSED")
                                    + count(database, candidate.getId(), "UNRESOLVED")
                                    == 1,
                            "竞争事件唯一");
                    if (returnedFirst)
                        require(
                                result.getConfirmationDueAt() == null
                                        && result.getResolutionType() == null,
                                "退回清空旧结论期限");
                }
            }
            System.out.println("PASS 期限补扫、接单补记、重复执行、通知原子性及10轮关闭退回竞争");
        }
    }

    /**
     * 构造只属于此次隔离数据库的时间边界工单，不改共享数据。
     */
    private static TicketEntity fixture(TicketMapper mapper, ObjectMapper json, boolean resolved) {
        LocalDateTime now = mapper.decisionTime();
        TicketEntity ticket = new TicketEntity();
        ticket.setId(id());
        ticket.setTicketNo("VERIFY-" + ticket.getId());
        ticket.setCustomerId(id());
        ticket.setRequesterId(id());
        ticket.setClientRequestId(id());
        ticket.setRequestHash("a".repeat(64));
        ticket.setTitle("期限隔离夹具");
        ticket.setSymptom("仅验证状态与期限");
        ticket.setServiceScopeSnapshot(
                json.createObjectNode().put("schema_version", 1).put("admission_type", "TRIAGE"));
        ticket.setSupportContext(json.createObjectNode().put("schema_version", 1));
        ticket.setPriority("NORMAL");
        ticket.setStatus(resolved ? "RESOLVED" : "SUBMITTED");
        ticket.setOnsiteEligible(false);
        ticket.setRowVersion(0L);
        ticket.setCreatedAt(now.minusDays(5));
        ticket.setUpdatedAt(now.minusDays(5));
        ticket.setAcceptanceDueAt(now.minusDays(5).plusHours(4));
        if (resolved) {
            ticket.setAssigneeId(id());
            ticket.setFirstAcceptedAt(now.minusDays(5).plusHours(1));
            ticket.setResolvedAt(now.minusDays(4));
            ticket.setConfirmationDueAt(now.minusDays(1));
            ticket.setResolutionType("REFERRED");
        }
        mapper.insert(ticket);
        return ticket;
    }

    /**
     * 身份已验证的领域输入，仅用于期限服务竞争。
     */
    private static IdentityDTO actor(TicketEntity ticket, boolean technician) {
        IdentityDTO actor = new IdentityDTO();
        actor.setMustChangePassword(false);
        actor.setOwnerId(technician ? id() : ticket.getRequesterId());
        actor.setOrganizationId(technician ? id() : ticket.getCustomerId());
        actor.setOrganizationType(technician ? "SERVICE_PROVIDER" : "CUSTOMER");
        actor.setRoles(Set.of(technician ? "TECHNICIAN" : "CUSTOMER_USER"));
        actor.setCustomerIds(technician ? Set.of(ticket.getCustomerId()) : Set.of());
        return actor;
    }

    /**
     * 从数据库验证已提交事实，不用进程返回值代替事件检查。
     */
    private static long count(JdbcTemplate database, String ticket, String event) {
        return database.queryForObject(
                "SELECT COUNT(*) FROM sd_ticket_event WHERE ticket_id = ? AND event_type = ?",
                Long.class,
                ticket,
                event);
    }

    /**
     * 不依赖-ea启动参数，任何失败均令验收进程失败。
     */
    private static void require(boolean condition, String contract) {
        if (!condition) throw new AssertionError(contract);
    }

    /**
     * 此验收创建的所有标识均使用独立随机值。
     */
    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
