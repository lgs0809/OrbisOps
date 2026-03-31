package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;

import java.util.Map;

/** Local log read/search tool protocol adapter. */
public final class OpsLocalLogAdapter {

    private final LocalHostApplicationService service;
    private final OpsLocalAdapterSettings settings;

    public OpsLocalLogAdapter(
            LocalHostApplicationService service,
            OpsLocalAdapterSettings settings) {
        if (service == null) throw new IllegalArgumentException("LOCAL_HOST_SERVICE_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        this.service = service;
        this.settings = settings;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        String file = args.required("file", "日志工具必须提供 file");
        if ("tail_log".equals(toolName)) {
            return Map.of(
                    "status", "SUCCEEDED",
                    "file", file,
                    "lines", service.tail(
                            file,
                            settings.allowedLogRoots(),
                            args.raw("limit"),
                            settings.maxRows()));
        }
        String pattern = args.text("query", args.text("pattern", "ERROR"));
        return Map.of(
                "status", "SUCCEEDED",
                "file", file,
                "query", pattern,
                "lines", service.search(
                        file,
                        settings.allowedLogRoots(),
                        pattern,
                        args.raw("limit"),
                        settings.maxRows()));
    }
}
