/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.agent;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Bytecode transformer that patches certificate binding and handshake logic.
 */
public class CertificateUtilTransformer implements ClassFileTransformer {
    private static final String UTIL_CLASS = "com/hypixel/hytale/server/core/auth/CertificateUtil";
    private static final String MANAGER_CLASS = "com/hypixel/hytale/server/core/auth/ServerAuthManager";
    private static final String HANDSHAKE_CLASS = "com/hypixel/hytale/server/core/io/handlers/login/HandshakeHandler";

    /**
     * Applies targeted patches to specific server classes.
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className.equals(UTIL_CLASS)) {
            return patchUtil(classfileBuffer);
        }

        if (className.equals(MANAGER_CLASS)) {
            return patchManager(classfileBuffer);
        }

        if (className.equals(HANDSHAKE_CLASS)) {
            return patchHandshake(classfileBuffer);
        }

        return null;
    }

    /**
     * Overrides certificate binding validation to always succeed.
     */
    private byte[] patchUtil(byte[] buffer) {
        System.out.println("[LineageAgent] Patching CertificateUtil...");
        ClassReader cr = new ClassReader(buffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("validateCertificateBinding")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            visitInsn(Opcodes.ICONST_1);
                            visitInsn(Opcodes.IRETURN);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Returns the proxy fingerprint from system properties when available.
     */
    private byte[] patchManager(byte[] buffer) {
        System.out.println("[LineageAgent] Patching ServerAuthManager...");
        ClassReader cr = new ClassReader(buffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("getServerCertificateFingerprint")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            Label original = new Label();
                            visitLdcInsn("lineage.proxy.fingerprint");
                            visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            visitInsn(Opcodes.DUP);
                            visitJumpInsn(Opcodes.IFNULL, original);
                            visitInsn(Opcodes.ARETURN);
                            visitLabel(original);
                            visitInsn(Opcodes.POP);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Extracts proxy fingerprint from referral data and sets it before auth grant.
     */
    private byte[] patchHandshake(byte[] buffer) {
        System.out.println("[LineageAgent] Patching HandshakeHandler...");
        ClassReader cr = new ClassReader(buffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("exchangeServerAuthGrant")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitFieldInsn(
                                Opcodes.GETFIELD,
                                "com/hypixel/hytale/server/core/io/handlers/login/HandshakeHandler",
                                "referralData",
                                "[B"
                            );
                            visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "ru/hytalemodding/lineage/agent/LineageAgentHelper",
                                "extractProxyFingerprint",
                                "([B)Ljava/lang/String;",
                                false
                            );
                            Label skip = new Label();
                            Label done = new Label();
                            visitInsn(Opcodes.DUP);
                            visitJumpInsn(Opcodes.IFNULL, skip);
                            visitLdcInsn("lineage.proxy.fingerprint");
                            visitInsn(Opcodes.SWAP);
                            visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "java/lang/System",
                                "setProperty",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false
                            );
                            visitInsn(Opcodes.POP);
                            visitJumpInsn(Opcodes.GOTO, done);
                            visitLabel(skip);
                            visitInsn(Opcodes.POP);
                            visitLabel(done);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }
}
