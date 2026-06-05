package app;

import java.util.List;

import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Level 1A — Landing Page.
 *
 * Displays four headline facts about Victorian road crashes, pulled from the
 * landing_facts table via a single-table SELECT (satisfies Level 1 SQL
 * requirement of "retrieving raw data from a table").
 *
 * @author Tamoghna Kabi (s4217352), 2026.
 */
public class PageIndex implements Handler {

    public static final String URL = "/";

    @Override
    public void handle(Context context) throws Exception {
        JDBCConnection jdbc = new JDBCConnection();
        List<JDBCConnection.Fact> facts = jdbc.getLandingFacts();

        String html = "<html>";

        html = html + "<head>"
             + "<title>Home — Investigating Road Incidents</title>"
             + "<meta charset='UTF-8'>"
             + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
             + "<link rel='stylesheet' type='text/css' href='common.css' />"
             + "</head>";

        html = html + "<body>";

        // Header + primary nav
        html = html + """
            <div class='topnav'>
                <a href='/' class='logo'>LOGO</a>
                <div class='nav-links'>
                    <a href='/' class='active'>Home</a>
                    <a href='/conditions'>Conditions</a>
                    <a href='/deep-dive'>Deep Dive</a>
                    <a href='#' class='nav-disabled'>About</a>
                </div>
            </div>
        """;

        // Hero banner
        html = html + """
            <div class='hero'>
                <p class='hero-eyebrow'>— HERO BANNER —</p>
                <h1>Investigating Road Incidents in Victoria</h1>
                <p class='hero-subtitle'>Explore how road, atmospheric &amp; light conditions affect crashes</p>
            </div>
        """;

        // Facts grid header
        html = html + """
            <div class='section-header'>
                <h2>Key Facts <span class='muted'>(retrieved from database)</span></h2>
                <span class='sql-note'>// SELECT × single table</span>
            </div>
        """;

        // Four fact cards — built dynamically from DB rows
        html = html + "<div class='facts-grid'>";
        for (JDBCConnection.Fact fact : facts) {
            html = html
                + "<article class='fact-card'>"
                + "<p class='fact-label'>" + fact.label.toUpperCase() + "</p>"
                + "<p class='fact-value'>" + String.format("%,d", fact.value) + "</p>"
                + "<p class='fact-sublabel'>" + fact.sublabel + "</p>"
                + "</article>";
        }
        html = html + "</div>";

        // About panel
        html = html + """
            <div class='about-panel'>
                <div class='about-text'>
                    <h3>About This Website</h3>
                    <p>A data-driven website allowing exploration of Victorian
                       road incident data filtered by environmental conditions.</p>
                </div>
                <a href='/conditions' class='btn btn-primary'>Explore Conditions &rarr;</a>
            </div>
        """;

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

        // DO NOT MODIFY THIS
        context.html(html);
    }
}
