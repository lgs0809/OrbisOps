package cn.lgs.orbisops.trigger.application.skill;
import cn.lgs.orbisops.application.skill.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Comparator;

@lombok.extern.slf4j.Slf4j
@Component
public final class OpsSkillFileProjectionWorker {
    private final SkillFileSourcePort source;
    private final SkillFileProjectionPort projection;
    private int offset;
    public OpsSkillFileProjectionWorker(SkillFileSourcePort source,SkillFileProjectionPort projection) {this.source=source;this.projection=projection;}
    @Scheduled(fixedDelayString="${orbisops.skill-runtime.file-projection-delay-ms:60000}")
    public synchronized void scan() {
        var files=source.findAll().stream().sorted(Comparator.comparing(SkillFileDefinition::name)).toList();
        if(files.size()>20_000) throw new IllegalStateException("SKILL_FILE_CATALOG_LIMIT_EXCEEDED");
        if(offset>=files.size())offset=0;
        for(int end=Math.min(files.size(),offset+100);offset<end;offset++) {
            try {projection.synchronize(files.get(offset));}
            catch(RuntimeException failure) {log.warn("File Skill projection pending id={} reason={}",files.get(offset).name(),failure.getClass().getSimpleName());}
        }
    }
}
