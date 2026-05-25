package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.trigger.application.channel.OpsWeChatWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public provider callback endpoint. Authentication is the WeChat secure-callback signature + AES envelope. */
@RestController
@RequestMapping("/api/v1/channels/{channelId}/wechat/callback")
public final class OpsWeChatWebhookController {

    private final OpsWeChatWebhookService webhook;

    public OpsWeChatWebhookController(OpsWeChatWebhookService webhook) {
        if (webhook == null) throw new IllegalArgumentException("WECHAT_WEBHOOK_SERVICE_REQUIRED");
        this.webhook = webhook;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@PathVariable("channelId") String channelId,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestParam("msg_signature") String messageSignature,
                         @RequestParam("echostr") String echoStr) {
        return webhook.verify(channelId, timestamp, nonce, messageSignature, echoStr);
    }

    @PostMapping(consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@PathVariable("channelId") String channelId,
                          @RequestParam("timestamp") String timestamp,
                          @RequestParam("nonce") String nonce,
                          @RequestParam("msg_signature") String messageSignature,
                          @RequestBody byte[] body) {
        webhook.receive(channelId, timestamp, nonce, messageSignature, body);
        return "success";
    }
}
