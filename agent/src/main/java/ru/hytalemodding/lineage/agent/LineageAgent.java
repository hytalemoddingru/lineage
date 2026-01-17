/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point that installs bytecode patches for the server.
 */
public class LineageAgent {
    /**
     * Registers the transformer before application startup.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[LineageAgent] Initializing surgical security patch...");
        inst.addTransformer(new CertificateUtilTransformer());
        System.out.println("[LineageAgent] Transformer registered successfully.");
    }
}
