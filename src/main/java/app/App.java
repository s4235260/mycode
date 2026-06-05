package app;

import io.javalin.Javalin;
import io.javalin.core.util.RouteOverviewPlugin;


/**
 * Main Application Class.
 *
 * Running this class as a regular Java application starts the Javalin HTTP
 * server and serves the three Sub-Task A pages:
 *
 *   GET  /            — Level 1A — Landing Page (PageIndex)
 *   GET  /conditions  — Level 2A — Conditions Summary (PageConditions)
 *   GET  /deep-dive   — Level 3A — Deep Dive (PageDeepDive)
 *
 * @author Tamoghna Kabi (s4217352), 2026.
 * @author Timothy Wiley, 2023. email: timothy.wiley@rmit.edu.au
 * @author Santha Sumanasekara, 2021. email: santha.sumanasekara@rmit.edu.au
 */
public class App {

    public static final int         JAVALIN_PORT    = 7001;
    public static final String      CSS_DIR         = "css/";
    public static final String      IMAGES_DIR      = "images/";

    public static void main(String[] args) {
        // Create our HTTP server and listen on port 7001
        Javalin app = Javalin.create(config -> {
            config.registerPlugin(new RouteOverviewPlugin("/help/routes"));
            config.addStaticFiles(CSS_DIR);
            config.addStaticFiles(IMAGES_DIR);
        }).start(JAVALIN_PORT);

        configureRoutes(app);
    }

    public static void configureRoutes(Javalin app) {
        // Sub-Task A pages
        app.get(PageIndex.URL,      new PageIndex());      // Level 1A — Landing
        app.get(PageConditions.URL, new PageConditions()); // Level 2A — Conditions Summary
        app.get(PageDeepDive.URL,   new PageDeepDive());   // Level 3A — Deep Dive
    }
}
