package cn.lgs.orbisops.application.channel.provider;

import java.util.List;

public record ChannelInboundPacket(String contentType,
                                   byte[] body,
                                   List<TransportHeader> headers) {
    public ChannelInboundPacket {
        contentType = contentType == null ? "" : contentType.trim();
        body = body == null ? new byte[0] : body.clone();
        headers = headers == null || headers.isEmpty() ? List.of() : List.copyOf(headers);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public String firstHeader(String name) {
        if (name == null || name.isBlank()) return "";
        return headers.stream()
                .filter(header -> header.name().equalsIgnoreCase(name.trim()))
                .map(TransportHeader::value)
                .findFirst()
                .orElse("");
    }

    public record TransportHeader(String name, String value) {
        public TransportHeader {
            name = required(name, "CHANNEL_HEADER_NAME_REQUIRED");
            value = value == null ? "" : value.trim();
        }

        private static String required(String value, String reasonCode) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return normalized;
        }
    }
}
