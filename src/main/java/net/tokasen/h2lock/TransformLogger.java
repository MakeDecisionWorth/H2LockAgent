package net.tokasen.h2lock;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * Confirms on the console that the target class was actually instrumented,
 * and surfaces any transform error. Useful to verify the hook fired despite
 * class-loader isolation or relocation changes in the target plugin.
 */
final class TransformLogger extends AgentBuilder.Listener.Adapter {

    @Override
    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader,
                                 JavaModule module, boolean loaded, DynamicType dynamicType) {
        System.out.println("[H2LockAgent] instrumented " + typeDescription.getName()
                + " (loader=" + classLoader + ")");
    }

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule module,
                        boolean loaded, Throwable throwable) {
        System.out.println("[H2LockAgent] failed to transform " + typeName + ": " + throwable);
    }
}
