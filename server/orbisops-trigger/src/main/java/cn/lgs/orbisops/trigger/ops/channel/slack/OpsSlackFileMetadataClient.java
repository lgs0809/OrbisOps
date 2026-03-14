package cn.lgs.orbisops.trigger.ops.channel.slack;

import com.slack.api.Slack;
import com.slack.api.methods.response.files.FilesInfoResponse;
import com.slack.api.model.File;
import org.springframework.stereotype.Component;

/** Resolves partial Slack Connect file objects through files.info when the event omits private URLs. */
@Component
final class OpsSlackFileMetadataClient {

    OpsSlackProtocolCodec.SlackFileDescriptor resolve(String botToken,
                                                       OpsSlackProtocolCodec.SlackFileDescriptor descriptor) {
        if (descriptor == null) throw new IllegalArgumentException("SLACK_FILE_DESCRIPTOR_REQUIRED");
        try {
            FilesInfoResponse response = Slack.getInstance().methods(botToken)
                    .filesInfo(request -> request.file(descriptor.fileId()));
            if (response == null || !response.isOk() || response.getFile() == null) {
                throw new IllegalStateException("SLACK_FILE_INFO_FAILED:" + safe(response == null ? null : response.getError()));
            }
            File file = response.getFile();
            String name = safe(file.getName());
            String mediaType = safe(file.getMimetype());
            String url = safe(file.getUrlPrivateDownload());
            if (url.isBlank()) url = safe(file.getUrlPrivate());
            long size = file.getSize() == null ? descriptor.sizeBytes() : Math.max(0L, file.getSize());
            return new OpsSlackProtocolCodec.SlackFileDescriptor(
                    descriptor.fileId(),
                    name.isBlank() ? descriptor.fileName() : name,
                    mediaType.isBlank() ? descriptor.mediaType() : mediaType,
                    size,
                    url,
                    false);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("SLACK_FILE_INFO_FAILED:" + safe(failure.getMessage()), failure);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
