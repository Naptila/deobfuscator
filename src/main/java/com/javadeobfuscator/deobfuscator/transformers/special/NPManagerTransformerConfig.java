package com.javadeobfuscator.deobfuscator.transformers.special;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

public class NPManagerTransformerConfig extends TransformerConfig {
    private boolean stringEncryption = true;
    private boolean resourceIdEncryption = false;
    private boolean numberObfuscation = true;
    private boolean instructionSubstitution = false;
    private boolean antiStringDecryptAll = false;
    private CallProtectionMode callProtectionMode = CallProtectionMode.NONE;
    private ControlFlowMode controlFlowMode = ControlFlowMode.FLATTENING_V2;

    public NPManagerTransformerConfig() {
        super(NPManagerTransformer.class);
    }

    public boolean isStringEncryption() {
        return stringEncryption;
    }

    public void setStringEncryption(boolean stringEncryption) {
        this.stringEncryption = stringEncryption;
    }

    public boolean isResourceIdEncryption() {
        return resourceIdEncryption;
    }

    public void setResourceIdEncryption(boolean resourceIdEncryption) {
        this.resourceIdEncryption = resourceIdEncryption;
    }

    public boolean isNumberObfuscation() {
        return numberObfuscation;
    }

    public void setNumberObfuscation(boolean numberObfuscation) {
        this.numberObfuscation = numberObfuscation;
    }

    public boolean isInstructionSubstitution() {
        return instructionSubstitution;
    }

    public void setInstructionSubstitution(boolean instructionSubstitution) {
        this.instructionSubstitution = instructionSubstitution;
    }

    public boolean isAntiStringDecryptAll() {
        return antiStringDecryptAll;
    }

    public void setAntiStringDecryptAll(boolean antiStringDecryptAll) {
        this.antiStringDecryptAll = antiStringDecryptAll;
    }

    public CallProtectionMode getCallProtectionMode() {
        return callProtectionMode;
    }

    public void setCallProtectionMode(CallProtectionMode callProtectionMode) {
        this.callProtectionMode = callProtectionMode;
    }

    public ControlFlowMode getControlFlowMode() {
        return controlFlowMode;
    }

    public void setControlFlowMode(ControlFlowMode controlFlowMode) {
        this.controlFlowMode = controlFlowMode;
    }

    public enum CallProtectionMode {
        NONE,
        METHOD_HIDING,
        REFLECTION_PROTECTION
    }

    public enum ControlFlowMode {
        NONE,
        FLOW_GARBAGE,
        FLATTENING_V2
    }
}
