package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.LandChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OpsChangePackageToolExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ObjectProvider<PrepareChangePackageUseCase> preparation;
    private final ObjectProvider<ReviewChangePackageUseCase> review;
    private final ObjectProvider<LandChangePackageUseCase> landing;
    private final ObjectProvider<ChangePackageQueryService> queries;

    @Autowired
    public OpsChangePackageToolExecutionDispatchHandler(
            ObjectProvider<PrepareChangePackageUseCase> preparation,
            ObjectProvider<ReviewChangePackageUseCase> review,
            ObjectProvider<LandChangePackageUseCase> landing,
            ObjectProvider<ChangePackageQueryService> queries) {
        this.preparation = preparation;
        this.review = review;
        this.landing = landing;
        this.queries = queries;
    }

    /** Compatibility constructor for focused tests that do not exercise Landing. */
    public OpsChangePackageToolExecutionDispatchHandler(
            ObjectProvider<PrepareChangePackageUseCase> preparation,
            ObjectProvider<ReviewChangePackageUseCase> review,
            ObjectProvider<ChangePackageQueryService> queries) {
        this(preparation, review, null, queries);
    }

    @Override
    public String handlerId() {
        return "change-package";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "change_package".equals(target.toolsetId())
                || "CHANGE_PACKAGE".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        PrepareChangePackageUseCase prepare = available(preparation);
        ReviewChangePackageUseCase reviewer = available(review);
        LandChangePackageUseCase lander = available(landing);
        ChangePackageQueryService query = available(queries);
        Map<String, Object> arguments = request.arguments();
        String packageId = text(first(request.requestContext().get("packageId"), arguments.get("packageId")));
        String actor = request.actor();
        return switch (target.toolName()) {
            case "change_package_create", "PrepareChangePackage" -> {
                if (prepare == null) throw new IllegalStateException("ChangePackage preparation use case 未初始化");
                yield prepare.prepare(new ChangePackageCommands.Prepare(arguments, actor));
            }
            case "change_package_revise" -> {
                if (prepare == null) throw new IllegalStateException("ChangePackage preparation use case 未初始化");
                yield prepare.revise(new ChangePackageCommands.Revise(
                        required(packageId, "修订必须提供 packageId"), arguments, actor));
            }
            case "change_package_submit_review" -> {
                if (reviewer == null) throw new IllegalStateException("ChangePackage review use case 未初始化");
                yield reviewer.submitReview(new ChangePackageCommands.SubmitReview(
                        required(packageId, "提交审核必须提供 packageId"), arguments, actor));
            }
            case "change_package_approve" -> {
                if (reviewer == null) throw new IllegalStateException("ChangePackage review use case 未初始化");
                yield reviewer.approve(new ChangePackageCommands.Approve(
                        required(packageId, "批准必须提供 packageId"),
                        positiveInt(arguments.get("version"), "批准必须提供有效 version"),
                        required(text(arguments.get("packageHash")), "批准必须提供 packageHash"),
                        actor,
                        arguments));
            }
            case "change_package_reject" -> {
                if (reviewer == null) throw new IllegalStateException("ChangePackage review use case 未初始化");
                yield reviewer.reject(new ChangePackageCommands.Reject(
                        required(packageId, "驳回必须提供 packageId"), arguments, actor));
            }
            case "change_package_detail" -> {
                if (query == null) throw new IllegalStateException("ChangePackage query service 未初始化");
                yield query.detail(required(packageId, "查看详情必须提供 packageId"));
            }
            case "change_package_list" -> {
                if (query == null) throw new IllegalStateException("ChangePackage query service 未初始化");
                yield Map.of("items", query.list(new ChangePackageListQuery(
                        arguments, intValue(arguments.get("limit"), 50))));
            }
            case "change_package_start_landing" -> {
                if (lander == null) throw new IllegalStateException("ChangePackage landing use case 未初始化");
                positiveInt(arguments.get("version"), "Landing 必须提供有效 version");
                required(text(arguments.get("packageHash")), "Landing 必须提供 packageHash");
                yield lander.land(new ChangePackageCommands.Land(
                        required(packageId, "Landing 必须提供 packageId"), arguments, actor));
            }
            case "change_package_landing_plan" -> {
                if (query == null) throw new IllegalStateException("ChangePackage query service 未初始化");
                yield query.landingPlan(required(packageId, "查看 Landing Plan 必须提供 packageId"));
            }
            default -> throw new IllegalArgumentException("未知 ChangePackage 工具：" + target.toolName());
        };
    }

    private <T> T available(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
        return value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int positiveInt(Object value, String message) {
        int parsed = intValue(value, -1);
        if (parsed <= 0) throw new IllegalArgumentException(message);
        return parsed;
    }
}
