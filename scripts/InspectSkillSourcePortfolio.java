package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;

/** Reads one actual frozen proposal. Uses a read-only transaction and never freezes or publishes a proposal. */
class InspectSkillSourcePortfolio {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String raw = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        var input = (Map<String,Object>) CanonicalJson.parse(raw);
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13362/orbisops_acceptance?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                "root", System.getenv("OPS_PORTFOLIO_READ_PASSWORD"))) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("START TRANSACTION READ ONLY");
            try {
                var portfolio = new JdbcSkillEvolutionSourcePortfolio(jdbc);
                var enriched = portfolio.enrich("ops-acceptance-a", input);
                portfolio.requireCurrent("ops-acceptance-a", enriched);
                String canonical = CanonicalJson.stringify(enriched);
                if (!canonical.equals(CanonicalJson.stringify(portfolio.enrich("ops-acceptance-a", input))))
                    throw new IllegalStateException("SOURCE_PORTFOLIO_REPEAT_NOT_IDENTICAL");
                var samples = (List<Map<String,Object>>) enriched.get("consolidatedExperiences");
                var report = new LinkedHashMap<String,Object>();
                report.put("status", "PASS_READ_ONLY_PROVENANCE");
                report.put("scope", "Existing accepted tasks and published method provenance; no new proposal, model call or publication");
                report.put("policy", enriched.get("sourcePortfolioPolicyVersion"));
                report.put("inputHash", CanonicalObjectHasher.sha256Text(raw));
                report.put("portfolioHash", CanonicalObjectHasher.sha256Text(canonical));
                report.put("primarySourceIds", enriched.get("primarySourceIds"));
                report.put("relatedSkillSourceGroups", enriched.get("relatedSkillSourceGroups"));
                report.put("excludedRelatedSourceIds", enriched.get("excludedRelatedSourceIds"));
                report.put("sources", samples.stream().map(s -> Map.of("sourceId", s.get("sourceId"),
                        "sourceHash", s.get("sourceHash"), "runId", s.get("runId"),
                        "wholeTaskBytes", String.valueOf(s.get("acceptedTaskEpisode")).getBytes(StandardCharsets.UTF_8).length)).toList());
                report.put("checks", Map.of("allAcceptedTaskHashesCurrent", true,
                        "publishedMethodMembershipVerified", true, "jointIncidentIndependenceVerified", true,
                        "wholeSourceCountWithin20", samples.size()<=20, "repeatReadIdentical", true,
                        "readOnlyTransactionRolledBack", true));
                connection.rollback();
                System.out.println(CanonicalJson.stringify(report));
            } finally { connection.rollback(); }
        }
    }
}
