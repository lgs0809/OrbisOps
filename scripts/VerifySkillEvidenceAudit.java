import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resolves recorded read paths against the immutable source. No network or model calls. */
class VerifySkillEvidenceAudit {
    public static void main(String[] args) throws Exception {
        var input=CanonicalJson.parseObject(new String(System.in.readAllBytes(),StandardCharsets.UTF_8));
        String source=(String)input.get("source");
        var audit=(Map<?,?>)input.get("audit");
        String sourceHash=CanonicalObjectHasher.sha256Text(source);
        if(!sourceHash.equals(audit.get("sourceHash"))) throw new IllegalStateException("SOURCE_HASH_MISMATCH");
        Object root=CanonicalJson.parse(source);
        var verified=new ArrayList<Map<String,Object>>();
        for(Object entry:(List<?>)audit.get("readEvidence")) {
            var ref=(Map<?,?>)entry;
            String path=(String)ref.get("path");Object value=root;
            String[] parts=path.isEmpty()?new String[0]:path.substring(1).split("/",-1);
            for(int i=0;i<parts.length;i++) {
                String part=parts[i].replace("~1","/").replace("~0","~");
                if(value instanceof String text && part.equals("@json")) value=CanonicalJson.parse(text);
                else if(value instanceof String text && part.equals("@chars")) {
                    String[] range=parts[++i].split(":");value=text.substring(Integer.parseInt(range[0]),Integer.parseInt(range[1]));
                } else if(value instanceof List<?> list && part.equals("@items")) {
                    String[] range=parts[++i].split(":");value=list.subList(Integer.parseInt(range[0]),Integer.parseInt(range[1]));
                } else if(value instanceof List<?> list) value=list.get(Integer.parseInt(part));
                else if(value instanceof Map<?,?> map && map.containsKey(part)) value=map.get(part);
                else throw new IllegalStateException("READ_PATH_INVALID");
            }
            String raw=CanonicalJson.stringify(value),hash=CanonicalObjectHasher.sha256Text(raw);
            String id=CanonicalObjectHasher.sha256Text(path+":"+hash);
            if(!hash.equals(ref.get("sha256")) || !id.equals(ref.get("id"))
                    || raw.length()!=((Number)ref.get("chars")).intValue()) throw new IllegalStateException("READ_HASH_MISMATCH");
            verified.add(Map.of("path",path,"id",id,"hash",hash,"chars",raw.length()));
        }
        if(verified.isEmpty()) throw new IllegalStateException("NO_ACTUAL_READS");
        System.out.println(CanonicalJson.stringify(Map.of("status","PASS_READ_PROVENANCE_ONLY",
                "sourceHash",sourceHash,"verifiedReads",verified,"completeSourceReviewed",false)));
    }
}
