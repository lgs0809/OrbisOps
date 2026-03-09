package cn.lgs.orbisops.application.worksession.run;

/** Schedules a previously authorized recoverable Work Session request. */
public interface WorkSessionResumeSchedulerPort<Q> {

    void schedule(Q request);
}
