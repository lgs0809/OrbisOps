import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.alibaba.fastjson.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Runs the actual directory state machine with replies supplied by the diagnostic transport. No persistence. */
class DiagnoseSkillEvidenceProtocol {
    public static void main(String[] args) throws Exception {
        var stdin=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        var first=CanonicalJson.parseObject(stdin.readLine());
        Class<?> type=Class.forName("cn.lgs.orbisops.trigger.ops.skill.OpsSkillLayeredInput");
        var constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);
        Object directory=constructor.newInstance((String)first.get("source"));
        var instruction=type.getDeclaredField("INSTRUCTION");instruction.setAccessible(true);
        var generate=type.getDeclaredMethod("generate",Function.class);generate.setAccessible(true);
        Function<String,JSONObject> transport=input->{
            try {
                System.out.println(CanonicalJson.stringify(Map.of("type","MODEL_REQUEST","input",input,"instruction",instruction.get(null))));
                System.out.flush();
                String body=(String)CanonicalJson.parseObject(stdin.readLine()).get("reply");
                body=body.strip();
                if(body.startsWith("```json\n") && body.endsWith("```")) body=body.substring(8,body.length()-3).strip();
                return new JSONObject(CanonicalJson.parseObject(body));
            } catch(Exception e) {throw new IllegalStateException("DIAGNOSTIC_REPLY_INVALID",e);}
        };
        try {
            Object result=generate.invoke(directory,transport);
            System.out.println(CanonicalJson.stringify(Map.of("type","FINAL","output",result)));
        } catch(Exception e) {
            Throwable cause=e.getCause()==null?e:e.getCause();
            String code=cause.getMessage();
            System.out.println(CanonicalJson.stringify(Map.of("type","ERROR","code",
                    code!=null && code.matches("[A-Z_]+")?code:"DIAGNOSTIC_FAILED")));
        }
    }
}
