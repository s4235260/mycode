package app;

import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Level 3A — Deep Dive (Discover New Facts).
 *
 * Surfaces combinations of road/atmospheric/light conditions whose fatality
 * rate is above the average — exposing non-obvious high-risk patterns for
 * the road safety policy analyst.
 *
 * SQL pattern: an inner query computes the per-combination fatality rate AND
 * its average; the outer query keeps only combinations above that average,
 * ranked by a "severity multiplier" (combination_rate / overall_avg).
 * This satisfies the Level 3 requirement of using one query's output as
 * input to another.
 *
 * @author Tamoghna Kabi (s4217352), 2026.
 */
public class PageDeepDive implements Handler {

    public static final String URL = "/deep-dive";

    @Override
    public void handle(Context context) throws Exception {
        JDBCConnection jdbc = new JDBCConnection();

        // ---- 1. Parse multi-select checkboxes -------------------------------
        boolean submitted = "1".equals(context.queryParam("submitted"));
        boolean useAtm   = submitted ? "on".equals(context.queryParam("atmospheric"))
                                     : true;
        boolean useSurf  = submitted ? "on".equals(context.queryParam("road_surface"))
                                     : true;
        boolean useLight = submitted ? "on".equals(context.queryParam("light"))
                                     : true;

        boolean anySelected = useAtm || useSurf || useLight;

        // ---- 2. Run query ---------------------------------------------------
        JDBCConnection.DeepDiveResult result = jdbc.getDeepDive(useAtm, useSurf, useLight);

        // Build the "Key Insight" headline sentence from the top row.
        String insight = null;
        if (!result.rows.isEmpty()) {
            JDBCConnection.CombinationRow top = result.rows.get(0);
            insight = top.combination
                    + " produces fatal crash rates "
                    + top.severityMultiplier + "x the average ("
                    + top.fatalPct + "% vs " + result.averageFatalityPct
                    + "% overall) — a critical target for infrastructure investment.";
        }

        // ---- 3. Build HTML --------------------------------------------------
        String html = "<html>";

        html = html + "<head>"
             + "<title>Deep Dive — Road Incidents</title>"
             + "<meta charset='UTF-8'>"
             + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
             + "<link rel='stylesheet' type='text/css' href='common.css' />"
             + "</head>";

        html = html + "<body>";

        // Header / nav
        html = html + """
            <div class='topnav'>
                <a href='/' class='logo'>LOGO</a>
                <div class='nav-links'>
                    <a href='/'>Home</a>
                    <a href='/conditions'>Conditions</a>
                    <a href='/deep-dive' class='active'>Deep Dive</a>
                    <a href='#' class='nav-disabled'>About</a>
                </div>
            </div>
        """;

        // Page intro
        html = html + """
            <div class='page-intro'>
                <h1>Deep Dive — Dangerous Condition Combinations</h1>
                <p class='muted'>Discover combinations of road, weather &amp; light conditions with disproportionate crash severity</p>
            </div>
        """;

        // Sequence map (matches wireframe)
        html = html + """
            <ol class='sequence-map'>
                <li><span class='step-num'>01</span> Select Factors</li>
                <li class='arrow'>&rarr;</li>
                <li><span class='step-num'>02</span> Compute Avg</li>
                <li class='arrow'>&rarr;</li>
                <li><span class='step-num'>03</span> Rank Combos</li>
            </ol>
        """;

        // Filter (multi-select checkboxes)
        html = html
            + "<div class='filter-panel'>"
            + "  <div class='section-header'>"
            + "    <h2>Select Factors to Combine — Multi-Select</h2>"
            + "    <span class='sql-note'>// outer query input</span>"
            + "  </div>"
            + "  <form class='checkbox-form' method='get' action='/deep-dive'>"
            + "    <input type='hidden' name='submitted' value='1'>"
            + checkbox("atmospheric",  "Atmospheric",   useAtm)
            + checkbox("road_surface", "Road Surface",  useSurf)
            + checkbox("light",        "Light Condition", useLight)
            + "    <button type='submit' class='btn btn-primary'>Analyse</button>"
            + "  </form>"
            + "</div>";

        if (!anySelected) {
            html = html + "<p class='empty-message'>Select at least one factor to combine.</p>";
        } else {
            // Average bar
            html = html
                + "<div class='avg-bar'>"
                + "  <p><strong>Average fatality rate across all combinations:</strong> "
                + "  <span class='avg-value'>" + result.averageFatalityPct + "%</span></p>"
                + "  <p class='muted'>Showing combinations with above-average severity, ranked by severity multiplier</p>"
                + "</div>";

            // Results table
            html = html + """
                <div class='section-header'>
                    <h2>High-Risk Condition Combinations (Ranked by Severity Multiplier)</h2>
                    <span class='sql-note'>// subquery: above-average pattern</span>
                </div>
            """;

            if (result.rows.isEmpty()) {
                html = html + "<p class='empty-message'>No combinations above the average. Try adding more factors.</p>";
            } else {
                html = html
                    + "<div class='table-wrap'>"
                    + "<table class='data-table'>"
                    + "  <thead><tr>"
                    + "    <th class='num'>Rank</th><th>Combination</th>"
                    + "    <th class='num'>Crashes</th><th class='num'>Fatal %</th>"
                    + "    <th class='num'>Severity Multiplier</th>"
                    + "  </tr></thead>"
                    + "  <tbody>";

                int rank = 1;
                for (JDBCConnection.CombinationRow row : result.rows) {
                    String trClass = (rank <= 2) ? "highlight" : "";
                    html = html
                        + "<tr class='" + trClass + "'>"
                        + "<td class='num'>" + rank + "</td>"
                        + "<td>" + row.combination + "</td>"
                        + "<td class='num'>" + String.format("%,d", row.crashes) + "</td>"
                        + "<td class='num'>" + row.fatalPct + "%</td>"
                        + "<td class='num multiplier'>" + row.severityMultiplier + "x</td>"
                        + "</tr>";
                    rank++;
                }

                html = html
                    + "</tbody></table></div>"
                    + "<p class='muted small'>Top 2 rows highlighted — severity multiplier above the rest</p>";
            }

            // Key Insight
            if (insight != null) {
                html = html
                    + "<div class='insight-panel'>"
                    + "  <h2>Key Insight</h2>"
                    + "  <p>" + insight + "</p>"
                    + "</div>";
            }
        }

        // Footer
        html = html + """
            <div class='footer'>
                <p>
                    <a href='/'>Home</a> ·
                    <a href='/conditions'>Conditions</a> ·
                    <a href='/deep-dive'>Deep Dive</a> ·
                    <a href='#'>About</a>
                </p>
                <p>RMIT University · Programming Studio 1 · 2026 · Tamoghna Kabi (s4217352)</p>
            </div>
        """;

        html = html + "</body></html>";

        context.html(html);
    }

    private static String checkbox(String name, String label, boolean checked) {
        return "<label class='checkbox'>"
             + "  <input type='checkbox' name='" + name + "'"
             + (checked ? " checked" : "") + ">"
             + "  <span>" + label + "</span>"
             + "</label>";
    }
}
