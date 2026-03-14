package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelIdentityService;
import cn.lgs.orbisops.application.channel.ChannelManagementApplicationService;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelCommandMapper;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelProviderReadinessService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/channels")
public class OpsChannelAdminController {

    private final ChannelManagementApplicationService channels;
    private final ChannelQueryService queries;
    private final ChannelIdentityService identities;
    private final ChannelChatProcessManager chat;
    private final OpsChannelCommandMapper commandMapper;
    private final OpsChannelProviderReadinessService readiness;

    public OpsChannelAdminController(ChannelManagementApplicationService channels,
                                     ChannelQueryService queries,
                                     ChannelIdentityService identities,
                                     ChannelChatProcessManager chat,
                                     OpsChannelCommandMapper commandMapper,
                                     OpsChannelProviderReadinessService readiness) {
        this.channels = channels;
        this.queries = queries;
        this.identities = identities;
        this.chat = chat;
        this.commandMapper = commandMapper;
        this.readiness = readiness;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list(@RequestParam("projectId") String projectId) {
        return success(queries.list(projectId));
    }

    @GetMapping("/types")
    public Response<List<ChannelProtocolDescriptor>> types() {
        return success(queries.supportedTypes());
    }

    @GetMapping("/{channelId}/readiness")
    public Response<ChannelReadinessSnapshot> readiness(@PathVariable("channelId") String channelId,
                                                        @RequestParam("projectId") String projectId) {
        return success(readiness.readiness(projectId, channelId));
    }

    @GetMapping("/{channelId}/messages")
    public Response<List<Map<String, Object>>> messages(@PathVariable("channelId") String channelId,
                                                         @RequestParam("projectId") String projectId,
                                                         @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return success(queries.messages(projectId, channelId, limit));
    }

    @PostMapping("/{channelId}/messages/{externalMessageId}/recovery/requeue")
    public Response<Map<String, Object>> requeueInboundRecovery(@PathVariable("channelId") String channelId,
                                                                  @PathVariable("externalMessageId") String externalMessageId,
                                                                  @RequestParam("projectId") String projectId,
                                                                  @RequestBody Map<String, Object> request,
                                                                  HttpServletRequest servletRequest) {
        return success(channels.requeue(new ChannelModels.Recovery(projectId, channelId, externalMessageId,
                Boolean.TRUE.equals(request.get("confirmedNoSideEffect")), actor(servletRequest))));
    }

    @PostMapping("/{channelId}/messages/{externalMessageId}/recovery/cancel")
    public Response<Map<String, Object>> cancelInboundRecovery(@PathVariable("channelId") String channelId,
                                                                 @PathVariable("externalMessageId") String externalMessageId,
                                                                 @RequestParam("projectId") String projectId,
                                                                 HttpServletRequest servletRequest) {
        return success(channels.cancelRecovery(new ChannelModels.Recovery(projectId, channelId, externalMessageId,
                false, actor(servletRequest))));
    }

    @GetMapping("/{channelId}/identities")
    public Response<List<Map<String, Object>>> identities(@PathVariable("channelId") String channelId,
                                                           @RequestParam("projectId") String projectId) {
        return success(identities.list(projectId, channelId));
    }

    @PutMapping("/{channelId}/identities")
    public Response<Map<String, Object>> bindIdentity(@PathVariable("channelId") String channelId,
                                                       @RequestParam("projectId") String projectId,
                                                       @RequestBody Map<String, Object> request,
                                                       HttpServletRequest servletRequest) {
        return success(identities.bind(commandMapper.identity(
                projectId, channelId, request, actor(servletRequest))));
    }

    @GetMapping("/status")
    public Response<Map<String, Object>> status(@RequestParam("projectId") String projectId) {
        return success(queries.status(projectId));
    }

    @PostMapping("/{channelId}/send")
    public Response<Map<String, Object>> send(@PathVariable("channelId") String channelId,
                                               @RequestParam("projectId") String projectId,
                                               @RequestBody Map<String, Object> request,
                                               HttpServletRequest servletRequest) {
        return success(chat.send(new ChannelModels.Send(projectId, channelId,
                required(request.get("target"), "通知目标不能为空"),
                required(request.get("content"), "通知内容不能为空"),
                Map.of("source", "ADMIN_TEST"), actor(servletRequest))));
    }

    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        return success(channels.create(commandMapper.configuration(
                "", "", request, actor(servletRequest))));
    }

    @PutMapping("/{channelId}")
    public Response<Map<String, Object>> update(@PathVariable("channelId") String channelId,
                                                 @RequestParam("projectId") String projectId,
                                                 @RequestBody Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        return success(channels.update(commandMapper.configuration(
                projectId, channelId, request, actor(servletRequest))));
    }

    private String actor(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.userId() == null || principal.userId().isBlank() ? principal.username() : principal.userId();
        }
        throw new SecurityException("未获取到已认证管理员");
    }

    private String required(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(message);
        return text;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info("success").data(data).build();
    }
}
