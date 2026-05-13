package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/** Web adapter that exposes MultipartFile through the framework-neutral RAG resource contract. */
public final class MultipartRagFileResource implements RagFileResource {

    private final MultipartFile delegate;

    public MultipartRagFileResource(MultipartFile delegate) {
        if (delegate == null) throw new IllegalArgumentException("RAG_MULTIPART_FILE_REQUIRED");
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.getName();
    }

    @Override
    public String originalFilename() {
        return delegate.getOriginalFilename();
    }

    @Override
    public String contentType() {
        return delegate.getContentType();
    }

    @Override
    public long size() {
        return delegate.getSize();
    }

    @Override
    public boolean empty() {
        return delegate.isEmpty();
    }

    @Override
    public InputStream openStream() throws IOException {
        return delegate.getInputStream();
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return delegate.getBytes();
    }
}
