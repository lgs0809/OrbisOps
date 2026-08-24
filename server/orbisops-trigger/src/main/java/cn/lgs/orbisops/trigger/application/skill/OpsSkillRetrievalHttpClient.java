package cn.lgs.orbisops.trigger.application.skill;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Explicit Qwen gateway contract. No shared chat/embedding model and no automatic retry. */
@Component
public final class OpsSkillRetrievalHttpClient {
    public static final String EMBEDDING="Qwen/Qwen3-VL-Embedding-2B", RERANKER="Qwen/Qwen3-VL-Reranker-2B";
    public static final String PREPROCESS="skill-route-text-v1:nfc:instruction:mrl1024:l2";
    private final String endpoint,key,embeddingRevision,rerankerRevision;
    private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public OpsSkillRetrievalHttpClient(@Value("${orbisops.skill-runtime.retrieval.endpoint:}") String endpoint,
            @Value("${orbisops.skill-runtime.retrieval.api-key:}") String key,
            @Value("${orbisops.skill-runtime.retrieval.embedding-revision:}") String embeddingRevision,
            @Value("${orbisops.skill-runtime.retrieval.reranker-revision:}") String rerankerRevision) {
        this.endpoint=endpoint.replaceAll("/+$","");this.key=key;this.embeddingRevision=embeddingRevision;this.rerankerRevision=rerankerRevision;
        if(!this.endpoint.isBlank()) {
            var uri=URI.create(this.endpoint);
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("SKILL_RETRIEVAL_ENDPOINT_INVALID");
            if(!embeddingRevision.matches("[a-f0-9]{40}") || !rerankerRevision.matches("[a-f0-9]{40}"))
                throw new IllegalArgumentException("SKILL_RETRIEVAL_PINNED_REVISIONS_REQUIRED");
        }
    }
    public boolean configured() { return !endpoint.isBlank(); }
    public String modelIdentity() { return EMBEDDING+"@"+embeddingRevision+":"+PREPROCESS; }
    public float[] embed(String text,boolean query) { return embed(text,query,5); }
    public float[] embedBackground(String text,boolean query) { return embed(text,query,60); }
    private float[] embed(String text,boolean query,int seconds) {
        var response=post("/embed",Map.of("model",EMBEDDING,"revision",embeddingRevision,"preprocessing",PREPROCESS,
                "text",java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFC),"kind",query?"query":"document","dimensions",1024),seconds);
        identity(response,EMBEDDING,embeddingRevision);
        var vector=response.getJSONArray("embedding");
        if(vector==null || vector.size()!=1024) throw new IllegalStateException("SKILL_EMBEDDING_DIMENSION_MISMATCH");
        float[] result=new float[1024];double norm=0;
        for(int i=0;i<result.length;i++) { if(!(vector.get(i) instanceof Number)) throw new IllegalStateException("SKILL_EMBEDDING_VALUE_INVALID");result[i]=vector.getFloatValue(i);if(!Float.isFinite(result[i])) throw new IllegalStateException("SKILL_EMBEDDING_NON_FINITE");norm+=(double)result[i]*result[i]; }
        if(Math.abs(norm-1D)>0.01D) throw new IllegalStateException("SKILL_EMBEDDING_NORMALIZATION_MISMATCH");
        return result;
    }
    public Map<String,Double> rerank(String query,List<Map<String,String>> candidates) {
        if(candidates.size()>20) throw new IllegalArgumentException("SKILL_RERANK_BUDGET_EXCEEDED");
        var response=post("/rerank",Map.of("model",RERANKER,"revision",rerankerRevision,"preprocessing",PREPROCESS,"query",query,"documents",candidates));
        identity(response,RERANKER,rerankerRevision);
        var ranking=response.getJSONArray("ranking");Set<String> allowed=new HashSet<>();candidates.forEach(c->allowed.add(c.get("id")));
        if(ranking==null || ranking.size()!=candidates.size() || allowed.size()!=candidates.size()) throw new IllegalStateException("SKILL_RERANK_COUNT_MISMATCH");
        Map<String,Double> scores=new LinkedHashMap<>();
        for(var item:ranking) {
            if(!(item instanceof JSONObject row)) throw new IllegalStateException("SKILL_RERANK_ROW_INVALID");
            String id=row.getString("id");Double score=row.getDouble("score");
            if(!allowed.contains(id) || scores.containsKey(id) || score==null || !Double.isFinite(score))
                throw new IllegalStateException("SKILL_RERANK_RESPONSE_INVALID");
            scores.put(id,score);
        }
        return Map.copyOf(scores);
    }
    private void identity(JSONObject response,String model,String revision) {
        if(response==null || !model.equals(response.getString("model")) || !revision.equals(response.getString("revision")) || !PREPROCESS.equals(response.getString("preprocessing")))
            throw new IllegalStateException("SKILL_RETRIEVAL_MODEL_IDENTITY_MISMATCH");
    }
    private JSONObject post(String path,Map<String,Object> body) { return post(path,body,5); }
    private JSONObject post(String path,Map<String,Object> body,int seconds) {
        if(!configured()) throw new IllegalStateException("SKILL_RETRIEVAL_NOT_CONFIGURED");
        byte[] bytes=JSON.toJSONBytes(body);if(bytes.length>128_000) throw new IllegalStateException("SKILL_RETRIEVAL_INPUT_TOO_LARGE");
        var builder=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(seconds)).header("Content-Type","application/json");
        if(!key.isBlank()) builder.header("Authorization","Bearer "+key);
        var future=client.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),ignored->new LimitedBody());
        try {
            var response=future.get(seconds,TimeUnit.SECONDS);
            if(response.statusCode()!=200) throw new IllegalStateException("SKILL_RETRIEVAL_HTTP_"+response.statusCode());
            return JSON.parseObject(new String(response.body(),StandardCharsets.UTF_8));
        } catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException("SKILL_RETRIEVAL_INTERRUPTED",error); }
        catch(ExecutionException | TimeoutException error) { throw new IllegalStateException("SKILL_RETRIEVAL_UNAVAILABLE",error); }
        finally { if(!future.isDone()) future.cancel(true); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;private int bytes;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription=value;delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> value) {
            for(var item:value) bytes+=item.remaining();
            if(bytes>1_000_000) { subscription.cancel();delegate.onError(new IllegalStateException("SKILL_RETRIEVAL_RESPONSE_TOO_LARGE")); }
            else delegate.onNext(value);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
}
