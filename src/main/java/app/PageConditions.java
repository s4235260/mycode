package app;

import java.util.List;

import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Level 2A — Conditions Summary.
 *
 * Filters Victorian crash data by atmospheric / road surface / light condition
 * and sorts the resulting summary by user-chosen column and direction.
 *
 * SQL features demonstrated:
 *   - JOIN (crashes -> reference tables)
 *   - WHERE (filter from user input)
 *   - GROUP BY + aggregation (COUNT, SUM(CASE WHEN))
 *   - ORDER BY (sort from user input)
 *
 * Data anomaly handling: rows with NULL condition IDs are excluded via
 * "WHERE c.atmospheric_id IS NOT NULL" etc.
 *
 * @author Tamoghna Kabi (s4217352), 2026.
 */
public class PageConditions implements Handler {

    public static final String URL = "/conditions";

    @Override
    public void handle(Context context) throws Exception {
        JDBCConnection jdbc = new JDBCConnection();

        // ---- 1. Parse query parameters --------------------------------------
        Integer atmosphericId = parseFilter(context.queryParam("atmospheric"));
        Integer roadSurfaceId = parseFilter(context.queryParam("road_surface"));
        Integer lightId       = parseFilter(context.queryParam("light"));

        String sortBy  = context.queryParam("sort_by");
        if (sortBy == null || sortBy.isBlank()) sortBy = "total";
        String sortDir = context.queryParam("sort_dir");
        if (sortDir == null || sortDir.isBlank()) sortDir = "desc";

        // ---- 2. Run queries -------------------------------------------------
        List<JDBCConnection.Option> atmosphericOpts = jdbc.getAtmosphericOptions();
        List<JDBCConnection.Option> roadSurfaceOpts = jdbc.getRoadSurfaceOptions();
        List<JDBCConnection.Option> lightOpts       = jdbc.getLightOptions();

        List<JDBCConnection.ConditionRow> rows = jdbc.getConditionsSummary(
            atmosphericId, roadSurfaceId, lightId, sortBy, sortDir);

        // ---- 3. Build HTML --------------------------------------------------
        String html = "<html>";

        html = html + "<head>"
             + "<title>Conditions Summary — Road Incidents</title>"
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
                    <a href='/conditions' class='active'>Conditions</a>
                    <a href='/deep-dive'>Deep Dive</a>
                    <a href='#' class='nav-disabled'>About</a>
                </div>
            </div>
        """;

        // Page intro
        html = html + """
            <div class='page-intro'>
                <h1>Conditions Summary</h1>
                <p class='muted'>Road Surface, Atmospheric &amp; Light Conditions</p>
            </div>
        """;

        // Filter panel
        html = html
            + "<div class='filter-panel'>"
            + "  <div class='section-header'>"
            + "    <h2>Filter Options</h2>"
            + "    <span class='sql-note'>// action panel</span>"
            + "  </div>"
            + "  <form class='filter-form' method='get' action='/conditions'>";

        html = html + buildSelect("Atmospheric", "atmospheric", atmosphericOpts, atmosphericId);
        html = html + buildSelect("Road Surface", "road_surface", roadSurfaceOpts, roadSurfaceId);
        html = html + buildSelect("Light Condition", "light", lightOpts, lightId);

        // Sort by
        html = html
            + "<div class='filter-group'>"
            + "  <label for='f-sort-by'>Sort by</label>"
            + "  <select name='sort_by' id='f-sort-by'>"
            + sortByOption("total",   "Total crashes",  sortBy)
            + sortByOption("fatal",   "Fatal",          sortBy)
            + sortByOption("serious", "Serious",        sortBy)
            + sortByOption("other",   "Other",          sortBy)
            + sortByOption("value",   "Condition (A-Z)", sortBy)
            + "  </select>"
            + "</div>";

        // Sort direction
        html = html
            + "<div class='filter-group'>"
            + "  <label for='f-sort-dir'>Order</label>"
            + "  <select name='sort_dir' id='f-sort-dir'>"
            + sortByOption("desc", "Highest first", sortDir)
            + sortByOption("asc",  "Lowest first",  sortDir)
            + "  </select>"
            + "</div>";

        html = html + "<button type='submit' class='btn btn-primary'>Search</button>";
        html = html + "</form></div>";

        // Results table
        html = html + """
            <div class='section-header'>
                <h2>Results — Crash Summary by Selected Conditions</h2>
                <span class='sql-note'>// JOIN · GROUP BY · ORDER BY · WHERE</span>
            </div>
        """;

        if (rows.isEmpty()) {
            html = html + "<p class='empty-message'>No crashes match the selected filters. Try widening the filter set.</p>";
        } else {
            html = html
                + "<div class='table-wrap'>"
                + "<table class='data-table'>"
                + "  <thead><tr>"
                + "    <th>Condition Type</th><th>Value</th>"
                + "    <th class='num'>Total</th><th class='num'>Fatal</th>"
                + "    <th class='num'>Serious</th><th class='num'>Other</th>"
                + "  </tr></thead>"
                + "  <tbody>";

            for (JDBCConnection.ConditionRow row : rows) {
                html = html
                    + "<tr>"
                    + "<td class='condition-type'>" + row.conditionType + "</td>"
                    + "<td>" + row.value + "</td>"
                    + "<td class='num'>" + String.format("%,d", row.total) + "</td>"
                    + "<td class='num'>" + String.format("%,d", row.fatal) + "</td>"
                    + "<td class='num'>" + String.format("%,d", row.serious) + "</td>"
                    + "<td class='num'>" + String.format("%,d", row.other) + "</td>"
                    + "</tr>";
            }

            html = html + "</tbody></table></div>";
        }

        // Bar chart (top 6 by total)
        if (!rows.isEmpty()) {
            int maxTotal = 0;
            int barLimit = Math.min(rows.size(), 6);
            for (int i = 0; i < barLimit; i++) {
                if (rows.get(i).total > maxTotal) maxTotal = rows.get(i).total;
            }
            if (maxTotal == 0) maxTotal = 1;

            html = html + """
                <div class='section-header'>
                    <h2>Visual Summary</h2>
                    <span class='sql-note'>// bar chart</span>
                </div>
                <div class='bar-chart'>
            """;

            for (int i = 0; i < barLimit; i++) {
                JDBCConnection.ConditionRow r = rows.get(i);
                double pct = (r.total / (double) maxTotal) * 100.0;
                html = html
                    + "<div class='bar-column'>"
                    + "  <div class='bar' style='height: " + String.format("%.1f", pct) + "%;' "
                    + "       title='" + r.value + ": " + String.format("%,d", r.total) + "'>"
                    + "    <span class='bar-value'>" + String.format("%,d", r.total) + "</span>"
                    + "  </div>"
                    + "  <p class='bar-label'>" + r.value + "</p>"
                    + "</div>";
            }

            html = html + "</div><p class='chart-caption'>Bar Chart: Crashes by Condition Type</p>";
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Integer parseFilter(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildSelect(String label, String name,
                                       List<JDBCConnection.Option> options,
                                       Integer selectedId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='filter-group'>");
        sb.append("  <label for='f-").append(name).append("'>").append(label).append("</label>");
        sb.append("  <select name='").append(name).append("' id='f-").append(name).append("'>");
        sb.append("    <option value='all'");
        if (selectedId == null) sb.append(" selected");
        sb.append(">All</option>");

        for (JDBCConnection.Option opt : options) {
            sb.append("    <option value='").append(opt.id).append("'");
            if (selectedId != null && selectedId == opt.id) sb.append(" selected");
            sb.append(">").append(opt.name).append("</option>");
        }

        sb.append("  </select>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String sortByOption(String value, String label, String selected) {
        return "<option value='" + value + "'"
             + (value.equals(selected) ? " selected" : "")
             + ">" + label + "</option>";
    }
}
