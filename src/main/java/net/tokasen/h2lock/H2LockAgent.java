package net.tokasen.h2lock;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Java agent that rewrites embedded H2 JDBC URLs at the {@code JdbcConnection}
 * constructor level, injecting a configurable {@code FILE_LOCK} mode. Works for
 * any plugin that constructs H2 directly or via reflection, regardless of the
 * class loader that loads H2 (e.g. AxVaults, AxShulkers, LuckPerms).
 *
 * <p>System properties (all optional):
 * <ul>
 *   <li>{@code h2lock.enabled} (default true) - global switch.</li>
 *   <li>{@code h2lock.type} (default fs) - one of {@code fs}, {@code socket}, {@code no}.</li>
 *   <li>{@code h2lock.autoserver} (default true) - also inject {@code AUTO_SERVER=TRUE};
 *       forces a compatible lock (promotes {@code fs} to {@code socket}).</li>
 *   <li>{@code h2lock.override} (default false) - replace an existing FILE_LOCK.</li>
 *   <li>{@code h2lock.verbose} (default false) - log every rewritten URL.</li>
 * </ul>
 */
public final class H2LockAgent {

    private static final String TARGET = "org.h2.jdbc.JdbcConnection";

    private H2LockAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        // At premain the target is not loaded yet, so on-load transformation is enough.
        install(inst, false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        // On dynamic attach the target may already be loaded; retransform it.
        install(inst, true);
    }

    private static void install(Instrumentation inst, boolean retransform) {
        if ("false".equalsIgnoreCase(System.getProperty("h2lock.enabled"))) {
            System.out.println("[H2LockAgent] disabled via -Dh2lock.enabled=false");
            return;
        }
        // Tolerate class-file versions newer than the bundled Byte Buddy knows about.
        if (System.getProperty("net.bytebuddy.experimental") == null) {
            System.setProperty("net.bytebuddy.experimental", "true");
        }

        String type = System.getProperty("h2lock.type", "fs");
        boolean autoServer = !"false".equalsIgnoreCase(System.getProperty("h2lock.autoserver"));
        boolean override = "true".equalsIgnoreCase(System.getProperty("h2lock.override"));
        boolean verbose = "true".equalsIgnoreCase(System.getProperty("h2lock.verbose"));

        // AUTO_SERVER binds a TCP socket; default it to loopback for reachability and
        // safety. Otherwise H2 may bind a link-local address that clients cannot reach.
        if (autoServer && System.getProperty("h2.bindAddress") == null) {
            System.setProperty("h2.bindAddress", "localhost");
        }

        System.out.println("[H2LockAgent] installing (type=" + type + ", autoServer=" + autoServer
                + ", override=" + override + ", verbose=" + verbose + ")");

        AgentBuilder builder = new AgentBuilder.Default()
                // Plugin class loaders (Paper, LuckPerms) hide JDK class files as
                // resources, so resolving referenced types like java.util.Map through
                // them fails. Fall back to the agent's own loader for those lookups.
                .with(AgentBuilder.LocationStrategy.ForClassLoader.STRONG
                        .withFallbackTo(ClassFileLocator.ForClassLoader.of(
                                H2LockAgent.class.getClassLoader())))
                .with(new TransformLogger());
        if (retransform) {
            builder = builder.with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
        builder.type(named(TARGET))
                .transform((b, type0, classLoader, module, pd) -> b.visit(
                        Advice.to(UrlAdvice.class)
                                .on(isConstructor().and(takesArgument(0, String.class)))))
                .installOn(inst);
    }
}
