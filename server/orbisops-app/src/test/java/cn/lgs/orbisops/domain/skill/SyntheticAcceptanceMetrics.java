package cn.lgs.orbisops.domain.skill;

import java.util.*;

/** Synthetic raw scrape fixture, never a live business or model result. */
public final class SyntheticAcceptanceMetrics {
    public static Map<String,Object> receipt(String project, double errorFraction) {
        var series = new ArrayList<Map<String,Object>>();
        for (String metric : List.of("ops04_http_requests_total", "ops04_http_errors_total",
                "ops04_http_request_duration_seconds_bucket")) {
            for (String le : metric.endsWith("_bucket") ? List.of("0.01", "0.05", "0.1", "+Inf") : List.of("")) {
                var labels = new LinkedHashMap<String,Object>(Map.of("__name__",metric,"project_id",project,
                        "environment","synthetic-test","service_id","service-a","version","fixture-1","route","/orders"));
                if (!le.isEmpty()) labels.put("le",le);
                var values = new ArrayList<List<Object>>();
                for (int i=0;i<=60;i++) values.add(List.of(1000+i*5,
                        i*2.0*(metric.endsWith("errors_total") ? errorFraction : le.equals("0.01") ? 0 : 1)));
                series.add(Map.of("metric",labels,"values",values));
            }
        }
        series.add(Map.of("metric",Map.of("__name__","up"),"values",List.of(List.of(1300,1))));
        return new LinkedHashMap<>(Map.of("kind","metrics_window","status","AVAILABLE","queryId","synthetic-query",
                "collectionDefinition","ops04-completed-http-raw-scrapes-v1","scope",Map.of("projectId",project,
                        "environment","synthetic-test","serviceId","service-a","startEpoch",1000,"endEpoch",1300),"series",series));
    }
}
