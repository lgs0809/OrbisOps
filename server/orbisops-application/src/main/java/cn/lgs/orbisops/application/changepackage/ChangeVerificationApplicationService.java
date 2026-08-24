package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

public final class ChangeVerificationApplicationService {
    private final ChangeVerificationQueuePort queue;
    private final ChangeVerificationDispatchPort dispatch;
    public ChangeVerificationApplicationService(ChangeVerificationQueuePort queue, ChangeVerificationDispatchPort dispatch) {
        this.queue = queue;
        this.dispatch = dispatch;
    }
    public void replay(Map<String, String> bindings) {
        // Re-read committed Landing facts: a process crash between commit and signal cannot lose work.
        for (var binding : bindings.entrySet()) {
            queue.discover(dispatch.binding(binding.getKey(), binding.getValue()), 50);
        }
        for (int i = 0; i < 4; i++) {
            var claimed = queue.claim();
            if (claimed.isEmpty()) return;
            var task = claimed.get();
            if (!bindings.getOrDefault(task.projectId(), "").equals(task.workflowId())) {
                queue.settle(task, "BLOCKED", "AUTOMATIC_VERIFICATION_BINDING_REMOVED", 60, false);
                continue;
            }
            try {
                if (!queue.owns(task)) continue;
                String status = dispatch.advance(task);
                if (!java.util.Set.of("PENDING", "COMPLETED", "FAILED", "CANCELED").contains(status))
                    throw new IllegalStateException("VERIFICATION_DISPATCH_STATUS_INVALID");
                queue.settle(task, status, "", 30, false);
            } catch (SecurityException | IllegalArgumentException denied) {
                queue.settle(task, "BLOCKED", denied.getMessage(), 60, false);
            } catch (RuntimeException unavailable) {
                int delay = (int) Math.min(3600L, 10L << Math.min(task.failures(), 9));
                // Only the exception class is persisted here; provider messages may include credentials.
                queue.settle(task, "PENDING", unavailable.getClass().getSimpleName(), delay, true);
            }
        }
    }
}
