package app;

import java.util.ArrayList;
import java.util.List;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the JDBC Connection to the SQLite Road Incidents database
 * and exposes the queries needed for Levels 1A, 2A and 3A of Sub-Task A.
 *
 * Schema highlights:
 *   crashes(id, accident_date, atmospheric_id, road_surface_id, light_id,
 *           region_id, severity_id)
 *   atmospheric_conditions(id, name), road_surfaces(id, name),
 *   light_conditions(id, name), regions(id, name)
 *   severity_levels: 1=Fatal, 2=Serious Injury, 3=Other Injury, 4=Non-Injury
 *   landing_facts(id, label, value, sublabel)  -- pre-computed for L1 single-table SELECT
 *
 * @author Tamoghna Kabi (s4217352), 2026.
 */
public class JDBCConnection {

    // SQLite database file (lives in the project's /database folder)
    public static final String DATABASE = "jdbc:sqlite:database/RoadIncidents.db";

    public JDBCConnection() {
        System.out.println("Created JDBC Connection Object");
    }

    // =========================================================================
    // LEVEL 1A — Landing facts (single-table SELECT from landing_facts)
    // =========================================================================

    /** A pre-computed fact displayed on the landing page. */
    public static class Fact {
        public final String label;
        public final int value;
        public final String sublabel;
        public Fact(String label, int value, String sublabel) {
            this.label = label;
            this.value = value;
            this.sublabel = sublabel;
        }
    }

    /**
     * Returns up to 4 landing facts. Single-table SELECT satisfies the
     * Level 1 requirement of retrieving raw data from a single table.
     */
    public List<Fact> getLandingFacts() {
        List<Fact> facts = new ArrayList<>();
        String sql = "SELECT label, value, sublabel FROM landing_facts "
                   + "ORDER BY id LIMIT 4";

        try (Connection conn = DriverManager.getConnection(DATABASE);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                facts.add(new Fact(
                    rs.getString("label"),
                    rs.getInt("value"),
                    rs.getString("sublabel")
                ));
            }
        } catch (SQLException e) {
            System.err.println("getLandingFacts: " + e.getMessage());
        }
        return facts;
    }

    // =========================================================================
    // LEVEL 2A — Conditions Summary (JOIN + GROUP BY + ORDER BY + WHERE)
    // =========================================================================

    /** A row in the L2A conditions summary table. */
    public static class ConditionRow {
        public final String conditionType;
        public final String value;
        public final int total;
        public final int fatal;
        public final int serious;
        public final int other;
        public ConditionRow(String conditionType, String value, int total,
                            int fatal, int serious, int other) {
            this.conditionType = conditionType;
            this.value = value;
            this.total = total;
            this.fatal = fatal;
            this.serious = serious;
            this.other = other;
        }
    }

    /** Simple (id, name) tuple used to populate the filter dropdowns. */
    public static class Option {
        public final int id;
        public final String name;
        public Option(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public List<Option> getAtmosphericOptions() {
        return getOptions("SELECT id, name FROM atmospheric_conditions ORDER BY name");
    }

    public List<Option> getRoadSurfaceOptions() {
        return getOptions("SELECT id, name FROM road_surfaces ORDER BY name");
    }

    public List<Option> getLightOptions() {
        return getOptions("SELECT id, name FROM light_conditions ORDER BY name");
    }

    private List<Option> getOptions(String sql) {
        List<Option> options = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DATABASE);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                options.add(new Option(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("getOptions: " + e.getMessage());
        }
        return options;
    }

    // Allow-list for user-controlled sort (SQLite can't parameterise column names)
    private static final java.util.Set<String> SORT_COLUMNS =
        java.util.Set.of("total", "fatal", "serious", "other", "value");
    private static final java.util.Set<String> SORT_DIRECTIONS =
        java.util.Set.of("ASC", "DESC");

    /**
     * Returns rows broken down by atmospheric / road surface / light condition.
     * Demonstrates JOIN, WHERE, GROUP BY, aggregation (COUNT + SUM CASE) and
     * user-controlled ORDER BY.
     *
     * @param atmosphericId  filter (null = all)
     * @param roadSurfaceId  filter (null = all)
     * @param lightId        filter (null = all)
     * @param sortBy         one of total/fatal/serious/other/value
     * @param sortDir        ASC or DESC
     */
    public List<ConditionRow> getConditionsSummary(
            Integer atmosphericId, Integer roadSurfaceId, Integer lightId,
            String sortBy, String sortDir) {

        String safeSort = SORT_COLUMNS.contains(sortBy) ? sortBy : "total";
        String safeDir  = SORT_DIRECTIONS.contains(sortDir == null ? "" : sortDir.toUpperCase())
                          ? sortDir.toUpperCase() : "DESC";

        List<ConditionRow> rows = new ArrayList<>();
        rows.addAll(runConditionQuery("Atmospheric", "atmospheric_conditions", "ac",
                                       "c.atmospheric_id",
                                       atmosphericId, roadSurfaceId, lightId,
                                       safeSort, safeDir));
        rows.addAll(runConditionQuery("Road Surface", "road_surfaces", "rs",
                                       "c.road_surface_id",
                                       atmosphericId, roadSurfaceId, lightId,
                                       safeSort, safeDir));
        rows.addAll(runConditionQuery("Light", "light_conditions", "lc",
                                       "c.light_id",
                                       atmosphericId, roadSurfaceId, lightId,
                                       safeSort, safeDir));
        return rows;
    }

    private List<ConditionRow> runConditionQuery(
            String conditionType, String refTable, String refAlias,
            String joinKey,
            Integer atmosphericId, Integer roadSurfaceId, Integer lightId,
            String sortBy, String sortDir) {

        List<ConditionRow> rows = new ArrayList<>();

        // Build the WHERE clause incrementally.
        StringBuilder where = new StringBuilder(joinKey + " IS NOT NULL");
        List<Integer> params = new ArrayList<>();
        if (atmosphericId != null) {
            where.append(" AND c.atmospheric_id = ?");
            params.add(atmosphericId);
        }
        if (roadSurfaceId != null) {
            where.append(" AND c.road_surface_id = ?");
            params.add(roadSurfaceId);
        }
        if (lightId != null) {
            where.append(" AND c.light_id = ?");
            params.add(lightId);
        }

        String sql =
            "SELECT ? AS condition_type, "
          + "       " + refAlias + ".name AS value, "
          + "       COUNT(*) AS total, "
          + "       SUM(CASE WHEN c.severity_id = 1 THEN 1 ELSE 0 END) AS fatal, "
          + "       SUM(CASE WHEN c.severity_id = 2 THEN 1 ELSE 0 END) AS serious, "
          + "       SUM(CASE WHEN c.severity_id IN (3, 4) THEN 1 ELSE 0 END) AS other "
          + "FROM crashes c "
          + "JOIN " + refTable + " " + refAlias + " ON " + joinKey + " = " + refAlias + ".id "
          + "WHERE " + where + " "
          + "GROUP BY " + refAlias + ".name "
          + "ORDER BY " + sortBy + " " + sortDir;

        try (Connection conn = DriverManager.getConnection(DATABASE);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, conditionType);
            for (Integer p : params) {
                ps.setInt(idx++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ConditionRow(
                        rs.getString("condition_type"),
                        rs.getString("value"),
                        rs.getInt("total"),
                        rs.getInt("fatal"),
                        rs.getInt("serious"),
                        rs.getInt("other")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("runConditionQuery (" + conditionType + "): " + e.getMessage());
        }

        return rows;
    }

    // =========================================================================
    // LEVEL 3A — Deep Dive (subquery: above-average severity combinations)
    // =========================================================================

    /** A row in the L3A ranked combinations table. */
    public static class CombinationRow {
        public final String combination;
        public final int crashes;
        public final double fatalPct;
        public final double severityMultiplier;
        public CombinationRow(String combination, int crashes,
                              double fatalPct, double severityMultiplier) {
            this.combination = combination;
            this.crashes = crashes;
            this.fatalPct = fatalPct;
            this.severityMultiplier = severityMultiplier;
        }
    }

    /** Result bundle for the Deep Dive page. */
    public static class DeepDiveResult {
        public final double averageFatalityPct;
        public final List<CombinationRow> rows;
        public DeepDiveResult(double averageFatalityPct, List<CombinationRow> rows) {
            this.averageFatalityPct = averageFatalityPct;
            this.rows = rows;
        }
    }

    /**
     * Returns combinations of the selected factors whose fatality rate exceeds
     * the overall average — ranked by severity multiplier.
     *
     * SQL pattern: a WITH clause computes per-combination fatality rates (inner
     * query), and a second WITH computes the AVERAGE of those rates (output of
     * the first used as input to the second). The outer SELECT filters and
     * ranks combinations above that average. This satisfies the Level 3
     * "use one query's output as input to another" requirement.
     */
    public DeepDiveResult getDeepDive(boolean useAtmospheric,
                                       boolean useRoadSurface,
                                       boolean useLight) {

        if (!useAtmospheric && !useRoadSurface && !useLight) {
            return new DeepDiveResult(0.0, new ArrayList<>());
        }

        StringBuilder labelExpr = new StringBuilder();
        StringBuilder joinClause = new StringBuilder();
        StringBuilder groupByCols = new StringBuilder();

        if (useAtmospheric) {
            labelExpr.append("ac.name");
            joinClause.append(" JOIN atmospheric_conditions ac ON c.atmospheric_id = ac.id ");
            groupByCols.append("ac.name");
        }
        if (useRoadSurface) {
            if (labelExpr.length() > 0) {
                labelExpr.append(" || ' + ' || ");
                groupByCols.append(", ");
            }
            labelExpr.append("rs.name");
            joinClause.append(" JOIN road_surfaces rs ON c.road_surface_id = rs.id ");
            groupByCols.append("rs.name");
        }
        if (useLight) {
            if (labelExpr.length() > 0) {
                labelExpr.append(" || ' + ' || ");
                groupByCols.append(", ");
            }
            labelExpr.append("lc.name");
            joinClause.append(" JOIN light_conditions lc ON c.light_id = lc.id ");
            groupByCols.append("lc.name");
        }

        String mainSql =
              "WITH combinations AS ( "
            + "    SELECT " + labelExpr + " AS combination, "
            + "           COUNT(*) AS crashes, "
            + "           AVG(CASE WHEN c.severity_id = 1 THEN 1.0 ELSE 0.0 END) AS fatality_rate "
            + "    FROM crashes c "
            + joinClause
            + "    WHERE c.atmospheric_id IS NOT NULL "
            + "      AND c.road_surface_id IS NOT NULL "
            + "      AND c.light_id IS NOT NULL "
            + "    GROUP BY " + groupByCols + " "
            + "    HAVING COUNT(*) >= 30 "
            + "), "
            + "overall AS ( "
            + "    SELECT AVG(fatality_rate) AS avg_rate FROM combinations "
            + ") "
            + "SELECT comb.combination, "
            + "       comb.crashes, "
            + "       ROUND(comb.fatality_rate * 100, 2) AS fatal_pct, "
            + "       ROUND(comb.fatality_rate / overall.avg_rate, 2) AS severity_multiplier "
            + "FROM combinations comb, overall "
            + "WHERE comb.fatality_rate > overall.avg_rate "
            + "ORDER BY severity_multiplier DESC "
            + "LIMIT 20";

        List<CombinationRow> rows = new ArrayList<>();
        double avgPct = 0.0;

        try (Connection conn = DriverManager.getConnection(DATABASE);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(mainSql)) {
            while (rs.next()) {
                rows.add(new CombinationRow(
                    rs.getString("combination"),
                    rs.getInt("crashes"),
                    rs.getDouble("fatal_pct"),
                    rs.getDouble("severity_multiplier")
                ));
            }
        } catch (SQLException e) {
            System.err.println("getDeepDive (main): " + e.getMessage());
        }

        // Also fetch the overall average for the UI's "Average fatality rate" banner.
        String avgSql =
              "WITH combinations AS ( "
            + "    SELECT " + labelExpr + " AS combination, "
            + "           AVG(CASE WHEN c.severity_id = 1 THEN 1.0 ELSE 0.0 END) AS fatality_rate "
            + "    FROM crashes c "
            + joinClause
            + "    WHERE c.atmospheric_id IS NOT NULL "
            + "      AND c.road_surface_id IS NOT NULL "
            + "      AND c.light_id IS NOT NULL "
            + "    GROUP BY " + groupByCols + " "
            + "    HAVING COUNT(*) >= 30 "
            + ") "
            + "SELECT ROUND(AVG(fatality_rate) * 100, 2) AS avg_pct FROM combinations";

        try (Connection conn = DriverManager.getConnection(DATABASE);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(avgSql)) {
            if (rs.next()) {
                avgPct = rs.getDouble("avg_pct");
            }
        } catch (SQLException e) {
            System.err.println("getDeepDive (avg): " + e.getMessage());
        }

        return new DeepDiveResult(avgPct, rows);
    }
}
