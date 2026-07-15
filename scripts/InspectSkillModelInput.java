import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads one frozen source on stdin; reports only integrity and sizes. No model or database writes. */
class InspectSkillModelInput {
    public static void main(String[] args) throws Exception {
        String raw = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Class<?> type = Class.forName("cn.lgs.orbisops.trigger.ops.skill.OpsSkillModelInputEncoding");
        var encode = type.getDeclaredMethod("encode", String.class); encode.setAccessible(true);
        var decode = type.getDeclaredMethod("decode", String.class); decode.setAccessible(true);
        Object result = encode.invoke(null, raw);
        var text = result.getClass().getDeclaredMethod("text"); text.setAccessible(true);
        var packed = result.getClass().getDeclaredMethod("packed"); packed.setAccessible(true);
        String wire = (String) text.invoke(result);
        boolean encoded = (boolean) packed.invoke(result);
        String restored = encoded ? CanonicalJson.stringify(decode.invoke(null, wire)) : raw;
        if (!CanonicalJson.stringify(CanonicalJson.parse(raw)).equals(restored)) {
            throw new IllegalStateException("Source did not restore exactly");
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "PASS_ENCODING_INTEGRITY_ONLY"); report.put("packed", encoded);
        report.put("sourceChars", raw.length()); report.put("modelChars", wire.length());
        report.put("sourceBytes", raw.getBytes(StandardCharsets.UTF_8).length);
        report.put("modelBytes", wire.getBytes(StandardCharsets.UTF_8).length);
        report.put("sourceHash", CanonicalObjectHasher.sha256Text(raw));
        report.put("modelHash", CanonicalObjectHasher.sha256Text(wire));
        if(wire.length()>2_000_000) {
            Class<?> directoryType=Class.forName("cn.lgs.orbisops.trigger.ops.skill.OpsSkillLayeredInput");
            var constructor=directoryType.getDeclaredConstructor(String.class);constructor.setAccessible(true);
            Object directory=constructor.newInstance(raw);
            var viewField=directoryType.getDeclaredField("sourceView");viewField.setAccessible(true);
            String view=CanonicalJson.stringify(viewField.get(directory));
            if(args.length==1 && args[0].equals("--directory-request")) {
                var instruction=directoryType.getDeclaredField("INSTRUCTION");instruction.setAccessible(true);
                var rounds=directoryType.getDeclaredField("READ_ROUNDS");rounds.setAccessible(true);
                System.out.println(CanonicalJson.stringify(Map.of("instruction",instruction.get(null),
                        "input",CanonicalJson.stringify(Map.of("format","ops-evidence-directory-v1",
                                "sourceHash",CanonicalObjectHasher.sha256Text(raw),"sourceView",viewField.get(directory),
                                "readEvidence",Map.of(),"readFeedback","","remainingReadRounds",rounds.get(null))))));
                return;
            }
            var fragmentField=directoryType.getDeclaredField("fragments");fragmentField.setAccessible(true);
            report.put("directoryChars",view.length());report.put("directoryHash",CanonicalObjectHasher.sha256Text(view));
            report.put("referenceCount",((Map<?,?>)fragmentField.get(directory)).size());
            report.put("directoryWithinRequestBudget",view.length()<390_000);
            report.put("modelReadTested",false);
        }
        System.out.println(CanonicalJson.stringify(report));
    }
}
