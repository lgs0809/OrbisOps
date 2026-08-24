package cn.lgs.orbisops.application.skill;
/** Retryable background state, including automated REVIEW; never a user approval gate. */
public final class SkillGroupingDeferredException extends RuntimeException {
    public SkillGroupingDeferredException(Throwable cause) {super("SKILL_GROUPING_DEFERRED",cause);}
    /** Only exception class names and controlled error codes: never remote response text or credentials. */
    public String diagnostic() {
        var result=new java.util.StringJoiner(" -> ","SKILL_GROUPING_DEFERRED: ","");
        Throwable current=getCause();
        for(int depth=0;current!=null && depth<8;depth++,current=current.getCause()) {
            String message=current.getMessage();
            result.add(current.getClass().getSimpleName()
                    +(message!=null && message.matches("SKILL_[A-Z_]{1,100}")?"["+message+"]":""));
        }
        return result.toString();
    }
}
