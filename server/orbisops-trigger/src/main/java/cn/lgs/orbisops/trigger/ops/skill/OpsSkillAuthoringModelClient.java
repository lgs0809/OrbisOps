package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.application.skill.SkillModelTransportDeferredException;
import cn.lgs.orbisops.trigger.ops.runtime.OpsModelProviderFailureClassifier;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsModelHttpClientFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsBackgroundModelCallDeadline;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Exact Terra catalog binding. Bounded read-only evidence calls, no agent tools or fallback model. */
@Component
public class OpsSkillAuthoringModelClient {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OpsSkillAuthoringModelClient.class);
    public static final String MODEL = "gpt-5.6-terra";
    public static final String PROMPT_VERSION = "skill-author-related-packages-v7-source-pages-deadline";
    private final AiClientModelCatalogPort models;
    private final AiClientApiCatalogPort apis;
    private final ModelAvailabilityPort availability;
    private final OpsSecretResolver secrets;

    public OpsSkillAuthoringModelClient(AiClientModelCatalogPort models, AiClientApiCatalogPort apis,
            ModelAvailabilityPort availability, OpsSecretResolver secrets) {
        this.models=models; this.apis=apis; this.availability=availability; this.secrets=secrets;
    }
    private Binding configured() {
        if (!availability.snapshot().modelCallsEnabled()) return null;
        var matches=models.listEnabled().stream()
                .filter(m->MODEL.equals(m.modelName()) && Integer.valueOf(1).equals(m.status())).toList();
        if (matches.size()!=1) return null;
        var model=matches.get(0); var api=apis.findByApiId(model.apiId());
        if(api==null || !Integer.valueOf(1).equals(api.status()) || api.baseUrl()==null || api.baseUrl().isBlank()) return null;
        String key=secrets.resolve(api.apiKey());
        if(!availability.isApiKeyUsable(key) || key.startsWith("${")) return null;
        return new Binding(model,api,key);
    }
    public boolean available() { return configured()!=null; }

    public JSONObject generate(String instruction,String input) {
        try {
            return OpsBackgroundModelCallDeadline.call(60, () -> generate(instruction,input,60));
        } catch (OpsBackgroundModelCallDeadline.DeadlineExceeded timeout) {
            throw new SkillModelTransportDeferredException(timeout);
        }
    }
    private JSONObject generate(String instruction,String input,int seconds) {
        var binding=configured();
        if(binding==null) throw new IllegalStateException("SKILL_EVOLUTION_MODEL_UNAVAILABLE");
        if(input==null || input.length()>16_000_000) throw new IllegalArgumentException("SKILL_AUTHORING_SOURCE_TOO_LARGE");
        var encoded=OpsSkillModelInputEncoding.encode(input);
        boolean layered=encoded.text().length()>2_000_000;
        if(layered) {
            try { cn.lgs.orbisops.domain.shared.json.CanonicalJson.parse(input); }
            catch(RuntimeException invalidJson) { throw new IllegalArgumentException("SKILL_AUTHORING_INPUT_TOO_LARGE"); }
        }
        String effectiveInstruction=instruction+(layered?"\n"+OpsSkillLayeredInput.INSTRUCTION:
                encoded.packed()?"\n"+OpsSkillModelInputEncoding.INSTRUCTION:"");
        JSONObject output;
        if(layered) {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
            output=new OpsSkillLayeredInput(input).generate(wire -> {
                long remaining=java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(deadline-System.nanoTime());
                if(remaining<1) throw new IllegalStateException("SKILL_EVIDENCE_INPUT_DEFERRED",
                        new IllegalArgumentException("SKILL_EVIDENCE_TIME_BUDGET"));
                return call(binding,effectiveInstruction,wire,(int)remaining);
            });
        } else output=call(binding,effectiveInstruction,encoded.text(),seconds);
        var api=binding.api();
        String path=api.completionsPath()==null || api.completionsPath().isBlank()?"/v1/chat/completions":api.completionsPath();
        // Provenance is supplied by the resolved client, never accepted from model text.
        output.put("authoringModel",MODEL);
        output.put("modelId",binding.model().modelId());
        output.put("authoringApiId",api.apiId());
        output.put("authoringPromptVersion",PROMPT_VERSION);
        output.put("modelInputEncoding",layered?OpsSkillLayeredInput.FORMAT:encoded.packed()?OpsSkillModelInputEncoding.FORMAT:"ORIGINAL");
        output.put("sourceInputChars",input.length());
        if(!layered) {
            output.remove("evidenceInputAudit");
            output.put("modelInputChars",encoded.text().length());
            output.put("modelInputHash",CanonicalObjectHasher.sha256Text(encoded.text()));
        } else {
            var audit=output.getJSONObject("evidenceInputAudit");
            output.put("modelInputChars",audit.getInteger("totalRequestChars"));
            output.put("modelInputHash",CanonicalObjectHasher.sha256(audit.get("requestHashes")));
        }
        output.put("sourceInputHash",CanonicalObjectHasher.sha256Text(input));
        output.put("authoringBindingHash",CanonicalObjectHasher.sha256(Map.of(
                "modelId",binding.model().modelId(),"modelName",MODEL,"apiId",api.apiId(),
                "baseUrl",api.baseUrl(),"path",path,"promptVersion",PROMPT_VERSION,
                "responseFormat","json_object",
                "promptHash",CanonicalObjectHasher.sha256Text(effectiveInstruction))));
        return output;
    }

    private JSONObject call(Binding binding,String instruction,String input,int seconds) {
        var api=binding.api();
        String path=api.completionsPath()==null || api.completionsPath().isBlank()?"/v1/chat/completions":api.completionsPath();
        var client=OpenAiApi.builder().baseUrl(api.baseUrl()).apiKey(binding.key()).completionsPath(path)
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(3,seconds)).build();
        var chat=OpenAiChatModel.builder().openAiApi(client)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder().model(MODEL)
                        .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                        .build()).build();
        org.springframework.ai.chat.model.ChatResponse response;
        try {
            response=chat.call(new Prompt(List.of(new SystemMessage(instruction),new UserMessage(input))));
        } catch (RuntimeException failure) {
            if (OpsModelProviderFailureClassifier.retryable(failure))
                throw new SkillModelTransportDeferredException(failure);
            throw failure;
        }
        if(response==null || response.getResult()==null || response.getResult().getOutput()==null)
            throw new IllegalStateException("SKILL_AUTHORING_MODEL_EMPTY");
        if(response.getMetadata()==null || !MODEL.equals(response.getMetadata().getModel()))
            throw new IllegalStateException("SKILL_AUTHORING_RESPONSE_MODEL_MISMATCH");
        String body=response.getResult().getOutput().getText();
        String finish=response.getResult().getMetadata().getFinishReason();
        if("length".equals(finish)) throw invalidReply("TRUNCATED");
        try { return parseReply(body); }
        catch(IllegalStateException invalid) {
            // Diagnostic metadata only: never put provider text or source content in logs.
            LOG.warn("Skill authoring reply invalid inputHash={} replyHash={} replyChars={} framing={}",
                    CanonicalObjectHasher.sha256Text(input),CanonicalObjectHasher.sha256Text(body==null?"":body),
                    body==null?0:body.length(),body==null?"EMPTY":body.strip().startsWith("```")?"FENCED":
                            body.strip().startsWith("{")?"OBJECT":"OTHER");
            throw invalid;
        }
    }

    static JSONObject parseReply(String body) {
        if(body==null || body.isBlank()) throw invalidReply("EMPTY");
        String value=body.strip();
        // Some compatible providers wrap the single object in a Markdown code block.
        // Unwrap only that whole envelope; never extract a favourable object from prose.
        if(value.startsWith("```")) {
            int newline=value.indexOf('\n');
            if(newline<0 || !value.endsWith("```")) throw invalidReply("JSON_INVALID");
            String language=value.substring(3,newline).strip();
            if(!language.isEmpty() && !language.equalsIgnoreCase("json")) throw invalidReply("JSON_INVALID");
            value=value.substring(newline+1,value.length()-3).strip();
        }
        try {
            if(!value.startsWith("{")) throw new IllegalArgumentException();
            return new JSONObject(CanonicalJson.parseObject(value));
        } catch(IllegalArgumentException invalid) { throw invalidReply("JSON_INVALID"); }
    }
    private static IllegalStateException invalidReply(String reason) {
        return new IllegalStateException("SKILL_AUTHORING_MODEL_INVALID",
                new IllegalArgumentException("SKILL_AUTHORING_REPLY_"+reason));
    }
    private record Binding(AiClientModelDefinition model,AiClientApiDefinition api,String key) { }
}
