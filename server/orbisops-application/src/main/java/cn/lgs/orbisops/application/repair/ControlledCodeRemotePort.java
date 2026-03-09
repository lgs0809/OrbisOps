package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

import java.util.List;

/** Narrow application port for Code Workspace MCP-backed repository/worktree operations. */
public interface ControlledCodeRemotePort {
    boolean supports(SourceRepository repository);
    RemoteRead read(SourceRepository repository, RepairWorkspace workspace, String revision, String path);
    List<RemoteSearchHit> search(SourceRepository repository, RepairWorkspace workspace, String revision, String query, int limit, boolean regex, boolean caseSensitive, String glob);
    List<RemoteFileEntry> glob(SourceRepository repository, RepairWorkspace workspace, String revision, String pattern, int limit);
    RemoteMutation edit(SourceRepository repository, RepairWorkspace workspace, String path, String expectedSha256, String oldString, String newString, boolean replaceAll);
    RemoteMutation write(SourceRepository repository, RepairWorkspace workspace, String path, String expectedSha256, String content);
    RemoteBash bash(SourceRepository repository, RepairWorkspace workspace, String command, ControlledCodeEffect effect, String cwd, int timeoutMs, boolean background, String action, String executionId, int limitBytes);
    RemoteLsp lsp(SourceRepository repository, RepairWorkspace workspace, String revision, String action, String path, int line, int character, String query);

    record RemoteRead(String content, String sha256) {}
    record RemoteSearchHit(String path, int line, String text) {}
    record RemoteFileEntry(String path, long sizeBytes, String modifiedAt) {}
    record RemoteMutation(String path, String beforeSha256, String afterSha256, int replacements, boolean created) {}
    record RemoteBash(String commandHash, int exitCode, String status, String output, String outputHash,
                      boolean truncated, long durationMs, String cwd, String executionId, Long pid, String logRef) {}
    record RemoteLsp(String status, boolean readOnly, String action, Object results, String reasonCode) {}
}
