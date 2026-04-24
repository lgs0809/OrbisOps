package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.TaskExecutionResponseDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleRequestDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.config.TaskScheduleApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * 周期任务配置管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/task-schedule")
public class AiAgentTaskScheduleAdminController {

    private final TaskScheduleApplicationService taskScheduleApplicationService;

    public AiAgentTaskScheduleAdminController(TaskScheduleApplicationService taskScheduleApplicationService) {
        this.taskScheduleApplicationService = taskScheduleApplicationService;
    }

    @GetMapping("/list")
    public Response<List<TaskScheduleResponseDTO>> listTaskSchedules(@RequestParam("projectId") String projectId) {
        return handle("查询巡检任务失败", () -> taskScheduleApplicationService.listSchedules(projectId), null);
    }

    @PostMapping("/create")
    public Response<Boolean> createTaskSchedule(@RequestBody TaskScheduleRequestDTO request) {
        return handle("创建周期任务失败", () -> taskScheduleApplicationService.create(request), false);
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateTaskSchedule(@RequestBody TaskScheduleRequestDTO request) {
        return handle("更新周期任务失败", () -> taskScheduleApplicationService.update(request), false);
    }

    @PutMapping("/status/{id}")
    public Response<Boolean> updateTaskStatus(@PathVariable("id") Long id,
                                              @RequestParam("status") Integer status,
                                              @RequestParam("projectId") String projectId) {
        return handle("更新巡检任务状态失败", () -> taskScheduleApplicationService.updateStatus(id, status, projectId), false);
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteTaskSchedule(@PathVariable("id") Long id,
                                                @RequestParam("projectId") String projectId) {
        return handle("删除巡检任务失败", () -> taskScheduleApplicationService.delete(id, projectId), false);
    }

    @PostMapping("/run-now/{id}")
    public Response<Long> runNow(@PathVariable("id") Long id,
                                 @RequestParam("projectId") String projectId) {
        return handle("立即执行失败", () -> taskScheduleApplicationService.runNow(id, projectId), null);
    }

    @GetMapping("/execution/list")
    public Response<List<TaskExecutionResponseDTO>> listExecutions(@RequestParam("projectId") String projectId,
                                                                   @RequestParam("scheduleId") Long scheduleId,
                                                                   @RequestParam(value = "limit", required = false) Integer limit) {
        return handle("查询执行记录失败", () -> taskScheduleApplicationService.listExecutions(projectId, scheduleId, limit), null);
    }

    private <T> Response<T> handle(String errorMessage, Supplier<T> action, T errorData) {
        try {
            return Response.<T>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<T>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(errorMessage + "：" + e.getMessage())
                    .data(errorData)
                    .build();
        }
    }
}
