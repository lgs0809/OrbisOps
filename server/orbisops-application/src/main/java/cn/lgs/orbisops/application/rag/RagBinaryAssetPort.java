package cn.lgs.orbisops.application.rag;

import java.io.IOException;
import java.nio.file.Path;

/** Binary asset storage boundary for parsed RAG images and multimodal embedding input. */
public interface RagBinaryAssetPort {

    Path store(String knowledge, String source, String fileName, byte[] content) throws IOException;

    boolean isRegularFile(Path path);

    byte[] read(Path path) throws IOException;
}
