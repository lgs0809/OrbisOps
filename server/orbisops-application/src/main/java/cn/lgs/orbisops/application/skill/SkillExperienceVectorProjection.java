package cn.lgs.orbisops.application.skill;

import java.util.List;

/** Equal-weight normalized centroid of independent accepted sources, used only for candidate recall. */
public final class SkillExperienceVectorProjection {
    private SkillExperienceVectorProjection() {}
    public static float[] centroid(List<float[]> vectors) {
        if(vectors.isEmpty() || vectors.get(0).length==0) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_EMPTY");
        double[] sum=new double[vectors.get(0).length];
        for(float[] vector:vectors) {
            if(vector.length!=sum.length) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_DIMENSION");
            double norm=0;
            for(float value:vector) {if(!Float.isFinite(value)) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_NON_FINITE");norm+=(double)value*value;}
            if(norm<=0) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_NORM");
            for(int i=0;i<sum.length;i++) sum[i]+=vector[i]/Math.sqrt(norm);
        }
        double norm=0;for(double value:sum)norm+=value*value;
        // A cancelling cluster has no meaningful centroid; retain a real member as its recall representative.
        if(norm<1e-20) {float[] first=vectors.get(0);return centroid(List.of(first));}
        float[] result=new float[sum.length];for(int i=0;i<sum.length;i++)result[i]=(float)(sum[i]/Math.sqrt(norm));
        return result;
    }
}
